"""Scoped static clearance review for the staged model, not full-body animation QA."""
import itertools
import json
import math
import sys
from collections import Counter
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=Path(sys.argv[2]) if len(sys.argv)>2 else ROOT/'output/blockbench/deepseeker-entry-redesign'
SOURCE=Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'src/main/blockbench/deepseeker_entry_redesign.bbmodel'
m=json.loads(SOURCE.read_text())
groups={g['uuid']:g for g in m['groups']}; parents={}
def walk(nodes, chain):
    for n in nodes:
        if isinstance(n,str): parents[n]=chain
        else: walk(n['children'],chain+[groups[n['uuid']]])
walk(m['outliner'],[])
def rotate(p, origin, angles):
    p=[p[i]-origin[i] for i in range(3)]
    for axis, deg in enumerate(angles):
        a,b=((1,2),(2,0),(0,1))[axis];c=math.cos(math.radians(deg));s=math.sin(math.radians(deg))
        p[a],p[b]=c*p[a]-s*p[b],s*p[a]+c*p[b]
    return [p[i]+origin[i] for i in range(3)]
def box(e, angle=0):
    if e['type']=='mesh':
        vertices=[[v[i]+e.get('origin',[0,0,0])[i] for i in range(3)] for v in e['vertices'].values()]
        lo=[min(v[i] for v in vertices) for i in range(3)]
        hi=[max(v[i] for v in vertices) for i in range(3)]
    else: lo,hi=e['from'],e['to']
    points=[]
    for p in itertools.product(*zip(lo,hi)):
        p=rotate(p,e.get('origin',[0,0,0]),e.get('rotation',[0,0,0]))
        for g in reversed(parents.get(e.get('uuid'),[])):
            p=rotate(p,g['origin'],[angle,0,0] if g['name']=='Hatch_lid_rotate_X_to_open' else g.get('rotation',[0,0,0]))
        points.append(p)
    return points
def dot(a,b):return sum(x*y for x,y in zip(a,b))
def sub(a,b):return [x-y for x,y in zip(a,b)]
def cross(a,b):return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
def overlap(a,b):
    aa=[sub(a[i],a[0]) for i in (1,2,4)];bb=[sub(b[i],b[0]) for i in (1,2,4)];depth=float('inf')
    for axis in aa+bb+[cross(x,y) for x in aa for y in bb]:
        length=math.sqrt(dot(axis,axis))
        if length<1e-8:continue
        x=[dot(p,axis)/length for p in a];y=[dot(p,axis)/length for p in b]
        d=min(max(x),max(y))-max(min(x),min(y))
        if d<=.015:return 0
        depth=min(depth,d)
    return depth
def mesh_overlap(a,e):
    """Convex mesh narrow phase after the conservative bounding-box test."""
    points={}
    for key,v in e['vertices'].items():
        p=[v[i]+e.get('origin',[0,0,0])[i] for i in range(3)]
        p=rotate(p,e.get('origin',[0,0,0]),e.get('rotation',[0,0,0]))
        for g in reversed(parents[e['uuid']]):p=rotate(p,g['origin'],g.get('rotation',[0,0,0]))
        points[key]=p
    edges=[];normals=[]
    for face in e['faces'].values():
        ps=[points[k] for k in face['vertices']]
        normals.append(cross(sub(ps[1],ps[0]),sub(ps[2],ps[0])))
        edges.extend(sub(ps[(i+1)%len(ps)],ps[i]) for i in range(len(ps)))
    aa=[sub(a[i],a[0]) for i in (1,2,4)];depth=float('inf')
    for axis in aa+normals+[cross(x,y) for x in aa for y in edges]:
        length=math.sqrt(dot(axis,axis))
        if length<1e-8:continue
        x=[dot(p,axis)/length for p in a];y=[dot(p,axis)/length for p in points.values()]
        d=min(max(x),max(y))-max(min(x),min(y))
        if d<=.015:return 0
        depth=min(depth,d)
    return depth
moving=[e for e in m['elements'] if any(g['name']=='Hatch_lid_rotate_X_to_open' for g in parents[e['uuid']])]
ids={e['uuid'] for e in moving};fixed=[e for e in m['elements'] if e['uuid'] not in ids and e.get('export',True)]
fixed_boxes=[(e,box(e)) for e in fixed]
contacts=m['practicemod']['entry_redesign']
def envelope(bounds):return box({'type':'cube','from':bounds[0],'to':bounds[1]})
corridor=envelope(contacts['descent_body_corridor']);head=envelope(contacts['start_head_clearance_envelope'])
corridor_hits=[e['name'] for e,b in fixed_boxes if overlap(corridor,b) and (e['type']!='mesh' or mesh_overlap(corridor,e))]
sweep={};head_hits=[]
opening=contacts['hatch_open_degrees']
for sample in range(46):
    angle=opening*sample/45
    for lid in moving:
        b=box(lid,angle)
        if overlap(head,b):head_hits.append([angle,lid['name']])
        for e,a in fixed_boxes:
            d=overlap(a,b)
            if d and e['type']=='mesh':d=mesh_overlap(b,e)
            seal=e['name'].startswith(('hatch_base_','rear_entry_sill_','rear_entry_collar_','rear_entry_shoulder_')) and d<=.8
            if d and not e['name'].startswith('hatch_hinge_') and not seal:
                key=(lid['name'],e['name']);sweep[key]=max(d,sweep.get(key,0))
open_hits=[e['name'] for e in moving if overlap(corridor,box(e,opening))]
def hits(bounds):
    a=envelope(bounds)
    return [e['name'] for e,b in fixed_boxes if overlap(a,b) and (e['type']!='mesh' or mesh_overlap(a,e))]
access_hits={name:hits(bounds) for name,bounds in contacts.get('access_areas',{}).items()}
route_hits={}
route=contacts.get('walking_route_centers',[])
for i,(a,b) in enumerate(zip(route,route[1:])):
    for sample in range(21):
        p=[a[j]+(b[j]-a[j])*sample/20 for j in range(3)]
        for name in hits([[p[0]-4.8,p[1]+.05,p[2]-4.8],[p[0]+4.8,p[1]+28.8,p[2]+4.8]]):
            route_hits.setdefault(name,[]).append([i,sample])
storage_hits={}
if contacts.get('access_areas'):
    fittings=[(e,b) for e,b in fixed_boxes if e['name'].startswith('rear_entry_')]
    for e in m['elements']:
        panel=next((g for g in parents[e['uuid']] if g['name'].endswith('_front_access')),None)
        if panel is None:continue
        for angle in range(0,-91,-5):
            a=[rotate(p,panel['origin'],[angle,0,0]) for p in box(e)]
            for fitting,b in fittings:
                if overlap(a,b) and (fitting['type']!='mesh' or mesh_overlap(a,fitting)):
                    storage_hits.setdefault(e['name'],set()).add(fitting['name'])
    baseline_path=ROOT/'src/main/blockbench/archive/deepseeker_before_rear_entry.bbmodel'
    baseline=json.loads((baseline_path if baseline_path.exists() else ROOT/'src/main/blockbench/deepseeker.bbmodel').read_text())
    original={e['uuid']:e for e in baseline['elements']}
    furniture=('Starboard_bunk_and_storage','Port_chart_repair_workstation','Port_aft_air_supply','Aft_expedition_storage','pilot_seat')
    retained=[e for e in m['elements'] if any(g['name'] in furniture for g in parents[e['uuid']])]
    assert all(e==original.get(e['uuid']) for e in retained), 'Unrequested furniture change'
    assert baseline['textures']==m['textures'], 'Unexpected texture atlas change'
refs=[]
def references(nodes):
    for n in nodes:
        if isinstance(n,str):refs.append(n)
        else:references(n['children'])
references(m['outliner'])
assert set(refs)=={e['uuid'] for e in m['elements']} and all(v==1 for v in Counter(refs).values()), 'Invalid element hierarchy'
report={'scope':'Static 0.6-block body corridor, proposed start-head envelope, lid sweep every 2 degrees. Cube SAT and convex-mesh narrow phase. Not an articulated player sweep or proof of reachable poses.',
        'corridor_obstructions':corridor_hits,'open_lid_obstructions':open_hits,'start_head_sweep_collisions':head_hits,
        'unexpected_lid_sweep_intersections':[{'lid':a,'fixed':b,'depth_pixels':round(d,4)} for (a,b),d in sweep.items()],
        'rung_count':len(contacts['rung_top_centers']), 'rung_spacing_pixels':6,
        'ladder_rail_top':86.3,'hatch_sill_top':87.45,
        'furniture_access_obstructions':access_hits,'walking_route_obstructions':route_hits,
        'storage_panel_sweep_against_entry_fittings':{k:sorted(v) for k,v in storage_hits.items()}}
(OUT/'clearance.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
assert not (corridor_hits or open_hits or head_hits or sweep or any(access_hits.values()) or route_hits or storage_hits), 'Staged model clearance needs correction'
