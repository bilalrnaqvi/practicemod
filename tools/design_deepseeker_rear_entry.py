"""Rear-starboard entry model proposal, preserving the integrated source and furniture."""
import copy
import json
import uuid
from pathlib import Path
from deepseeker_mesh_clip import subtract_box, mesh

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/blockbench/deepseeker-rear-entry';OUT.mkdir(parents=True,exist_ok=True)
m=json.loads((ROOT/'src/main/blockbench/archive/deepseeker_before_rear_entry.bbmodel').read_text())
original={e['name']:copy.deepcopy(e) for e in m['elements']}
groups={g['name']:g for g in m['groups']};parents={};nodes={}
def walk(ns,chain):
    for n in ns:
        if isinstance(n,str):parents[n]=chain
        else:
            g=next(g for g in m['groups'] if g['uuid']==n['uuid']);nodes[g['name']]=n
            walk(n['children'],chain+[g])
walk(m['outliner'],[])
def group(name,parent):
    g=copy.deepcopy(groups['Starboard_interior_ladder']);g.update(name=name,uuid=str(uuid.uuid5(uuid.NAMESPACE_URL,'rear-entry/'+name)),children=[])
    m['groups'].append(g);groups[name]=g
    n={'uuid':g['uuid'],'children':[],'isOpen':True};nodes[name]=n;nodes[parent]['children'].append(n)
    return n
def cube(name,lo,hi,template,parent):
    e=copy.deepcopy(original[template]);e.update(name=name,uuid=str(uuid.uuid5(uuid.NAMESPACE_URL,'rear-entry/'+name)),
            **{'from':lo,'to':hi,'origin':[(a+b)/2 for a,b in zip(lo,hi)]})
    e.pop('rotation',None);m['elements'].append(e);parent['children'].append(e['uuid']);return e
def prune(ids):
    def clean(ns):
        ns[:]=[n for n in ns if not isinstance(n,str) or n not in ids]
        for n in ns:
            if isinstance(n,dict):clean(n['children'])
    clean(m['outliner']);m['elements'][:]=[e for e in m['elements'] if e['uuid'] not in ids]

# Replace the old aperture with continuous matching roof/pressure layers.
removed={e['uuid'] for e in m['elements'] if e['name'].startswith(('roof_panel_','hatch_base_','seal_pressure_liner_0_','pressure_panel_0_'))}
removed.update(nodes['Starboard_interior_ladder']['children']);removed.add(original['ladder_landing_grate']['uuid']);prune(removed)
cube('entry_roof_reclosed',[-21,79.8,-40.05],[21,83.7,-4.05],'roof_panel_right',nodes['hull'])
cube('entry_pressure_reclosed',[-14.74038,76.5,-58.05],[14.74038,82.5,16.35],'pressure_panel_0_right',nodes['hull'])
cube('entry_liner_reclosed',[-14.1705194,75.6,-58.5],[14.1705194,77.4,20.25],'seal_pressure_liner_0_right',nodes['hull'])

# Reuse and reflect the low octagonal hatch around its fore/aft axis.
moving_id=groups['Hatch_lid_rotate_X_to_open']['uuid']
for e in m['elements']:
    if any(g['uuid']==moving_id for g in parents.get(e['uuid'],[])) or e['name'].startswith('hatch_hinge_'):
        lo,hi=e['from'][:],e['to'][:]
        e['from']=[lo[0]+19,lo[1],-hi[2]-28.85];e['to']=[hi[0]+19,hi[1],-lo[2]-28.85]
        e['origin']=[e['origin'][0]+19,e['origin'][1],-e['origin'][2]-28.85]
        if 'rotation' in e:e['rotation'][1]*=-1
groups['Hatch_lid_rotate_X_to_open']['origin']=[19,89.4,-16.7]
groups['hatch']['origin']=[19,87.9,-2]
for e in m['elements']:
    if e['name'].startswith('hatch_hinge_'):
        for key in ('from','to','origin'):
            e[key][1]-=3.6;e[key][2]-=2.7
handle=next(e for e in m['elements'] if e['name']=='hatch_handle')
handle.update(**{'from':[14.5,95.8,8.05],'to':[23.5,98.2,11.05],'origin':[19,97,9.55]})
for name,x in [('hatch_handle_post_-1',15.48),('hatch_handle_post_1',22.52)]:
    post=next(e for e in m['elements'] if e['name']==name)
    post.update(**{'from':[x-.56,90.4,8.99],'to':[x+.56,96.32,10.11],'origin':[x,93.36,9.55]})

# Port-side antenna maintains its existing design and avoids the new lid/shaft.
for e in m['elements']:
    if any(g['name']=='mast' for g in parents.get(e['uuid'],[])):
        lo,hi=e['from'][0],e['to'][0];e['from'][0],e['to'][0]=-hi,-lo;e['origin'][0]*=-1
    if e['name']=='short_aerial':
        for key in ('from','to','origin'):e[key][0]-=6.5

# Cut all intersecting structural layers, including diagonal shoulder and rear band.
walk(m['outliner'],[])
cut_names=[];replacements={}
for e in list(m['elements']):
    chain=parents[e['uuid']]
    if e['type']!='cube' or not any(g['name'] in ('hull','frame','ceiling_unit') for g in chain):continue
    assert not any(any(abs(v)>1e-7 for v in g.get('rotation',[0,0,0])) for g in chain)
    pieces=subtract_box(e,[9.8,64,-13.2],[28.2,101,9.2])
    if len(pieces)==1 and pieces[0] is e:continue
    cut_names.append(e['name']);replacements[e['uuid']]=[p['uuid'] for p in pieces]
    m['elements'].remove(e);m['elements'].extend(pieces)
def replace(ns):
    out=[]
    for n in ns:
        if isinstance(n,str):out.extend(replacements.get(n,[n]))
        else:replace(n['children']);out.append(n)
    ns[:]=out
replace(m['outliner'])

collar=group('Rear_entry_collar','hatch')
def painted_panel(e):
    # Sample a broad painted patch at one texel per model pixel, avoiding stretched thin edge UVs.
    spans=[b-a for a,b in zip(e['from'],e['to'])]
    for name,axes in {'north':(0,1),'south':(0,1),'east':(2,1),'west':(2,1),'up':(0,2),'down':(0,2)}.items():
        e['faces'][name]['uv']=[1280,223,1280+min(28,spans[axes[0]]),223+min(48,spans[axes[1]])]
    return e
cube('rear_entry_collar_port',[7,80.5,-15],[10,87.45,11],'hatch_base_left',collar)
painted_panel(cube('rear_entry_collar_starboard',[28,64.8,-15],[31,85.6,11],'roof_panel_right',collar))
for tag,z in [('front',-15),('rear',9)]:
    painted_panel(cube('rear_entry_collar_'+tag+'_inner',[10,80.5,z],[15,85.6,z+2],'roof_panel_right',collar))
    # Tapered shoulder saddle: a closed wedge with quad faces, not stair-step cubes.
    points=[[15,80.5,z],[28,67.74375,z],[28,85.6,z],[15,85.6,z],
            [15,80.5,z+2],[28,67.74375,z+2],[28,85.6,z+2],[15,85.6,z+2]]
    faces=[(0,3,2,1),(4,5,6,7),(0,1,5,4),(3,7,6,2),(0,4,7,3),(1,2,6,5)]
    uv=original['roof_panel_right']['faces']['up']['uv'];u,v,U,V=uv
    polys=[([points[i]+list(t) for i,t in zip(face,[(u,v),(U,v),(U,V),(u,V)])],0) for face in faces]
    e=mesh('rear_entry_shoulder_'+tag,polys);m['elements'].append(e);collar['children'].append(e['uuid'])
for tag,x in [('starboard',28)]:
    cube('rear_entry_sill_'+tag,[x,85.6,-15],[x+3,87.45,11],'hatch_base_right',collar)
for tag,z in [('front',-15),('rear',9)]:
    cube('rear_entry_sill_'+tag,[10,85.6,z],[28,87.45,z+2],'hatch_base_front',collar)

ladder=nodes['Starboard_interior_ladder'];groups['Starboard_interior_ladder']['origin']=[25,21.55,-2]
for tag,z in [('front',-11.6),('rear',6.8)]:
    cube('rear_entry_ladder_rail_'+tag,[24,28,z],[26,86.3,z+1.6],'interior_ladder_rail_-10',ladder)
    cube('rear_entry_ladder_foot_'+tag,[22.8,27.8,z-.4],[27,29,z+2],'mast_socket',ladder)
    cube('rear_entry_ladder_top_mount_'+tag,[25.8,80,z-.1],[29,82.4,z+1.7],'mast_socket',ladder)
for i,y in enumerate(range(29,84,6)):
    cube(f'rear_entry_ladder_rung_{i:02}',[24,y,-10],[25,y+1.5,6.8],'interior_ladder_rung_6',ladder)
    cube(f'rear_entry_ladder_tread_{i:02}',[24,y+1.5,-9.9],[25,y+1.7,6.7],'ladder_tread_6',ladder)
cube('rear_entry_landing',[11.7,21.3,-13.5],[21.8,24,-1.1],'ladder_landing_grate',ladder)
ledge=group('Rear_entry_start_ledge','hatch')
cube('rear_entry_start_ledge',[11,81.8,12],[27,84,27],'hatch_base_right',ledge)
cube('rear_entry_start_tread',[12,84,13],[26,84.25,26],'ladder_landing_grate',ledge)
for x in (12,24):cube('rear_entry_start_support_'+str(x),[x,77,12],[x+2,81.8,23],'mast_socket',ledge)

contacts={
    'units':'Blockbench model pixels, 16 pixels per block',
    'status':'Integrated rear-entry geometry; animation and runtime review in output/animation/boarding-runtime.',
    'hatch_pivot':[19,89.4,-16.7],'hatch_open_degrees':-90,
    'opening':[[10,87.45,-13],[28,87.45,9]],
    'descent_body_corridor':[[12,24.05,-11],[21.6,118,-1.4]],
    'start_head_clearance_envelope':[[15,92,14],[23,102,22]],
    'start_support_bounds':[[12,84.25,13],[26,84.25,26]],
    'closed_lid_handle':[19,97,9.55],
    'upper_grips':[[24,85,-10.8],[24,85,7.6]],
    'rung_top_centers':[[24,y+1.7,-1.6] for y in range(29,84,6)],
    'landing':[16.8,24,-6.2],
    'access_areas':{
        'bed_side':[[3.6,21.6,-52],[13.2,50.4,-17]],
        'desk_front':[[-13.4,21.6,-51],[-3.8,50.4,-16]],
        'storage_front':[[-10.8,21.6,-7],[10.8,50.4,4.6]],
        'central_aisle':[[-4.8,21.6,-59],[4.8,50.4,4.6]]},
    'walking_route_centers':[[16.8,24,-6.2],[16.8,24,-8.5],[6.8,24,-8.5],[6.8,21.55,-8.5],[0,21.55,-8.5],[0,21.55,-54.5]],
    'cut_structural_parts':cut_names,
    'camera':'First-person gameplay implemented; outside/cutaway inspection remains separate.'}
m.setdefault('practicemod',{}).update(hatch_open_degrees=-90,entry_redesign=contacts)
path=ROOT/'src/main/blockbench/deepseeker_rear_entry.bbmodel';path.write_text(json.dumps(m,indent=2)+'\n')
(OUT/'entry-contacts.json').write_text(json.dumps(contacts,indent=2)+'\n')
print(path);print('Cut structural parts:',cut_names)
