#!/usr/bin/env python3
"""Shorten the aft hull once, then deterministically dress its sealed machinery bulkhead.

Keep all cockpit dimensions and attachments. Source remains cube-only/Blockbench editable.
"""
import copy
import json
import math
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT/'src/main/blockbench/deepseeker.bbmodel'
BINDINGS = ROOT/'src/main/blockbench/deepseeker_screen_bindings.json'
model = json.loads(PATH.read_text())
bindings = json.loads(BINDINGS.read_text())
CUT, RATIO = -58.5, .5

def compact(z): return z if z <= CUT else CUT + (z-CUT)*RATIO

groups = {g['uuid']: g for g in model['groups']}
chains = {}
def visit(nodes, chain):
    for n in nodes:
        if isinstance(n, str): chains[n] = chain
        else: visit(n['children'], chain+[groups[n['uuid']]['name']])
visit(model['outliner'], [])
original = {e['name']: e for e in model['elements']}
materials = {name: copy.deepcopy(original[source]['faces']) for name, source in
             [('dark','central_pedestal'), ('steel','console_rim'), ('yellow','pressure_panel_0')]}
remove = set()
for e in model['elements']:
    parents = chains[e['uuid']]
    if 'rear_cabin' in parents or 'aft_machinery' in parents:
        remove.add(e['uuid'])
    elif 'ceiling_unit' in parents and e['name'] != 'inner_pressure_liner':
        remove.add(e['uuid'])
    elif 'floor' in parents and e['name'] != 'inner_pressure_liner' and e['from'][2] >= CUT:
        remove.add(e['uuid'])
model['elements'] = [e for e in model['elements'] if e['uuid'] not in remove]
remove_groups = {g['uuid'] for g in model['groups'] if g['name'] in ('rear_cabin','aft_machinery')}
def prune(nodes):
    result=[]
    for n in nodes:
        if isinstance(n,str):
            if n not in remove: result.append(n)
        elif n['uuid'] not in remove_groups:
            n['children']=prune(n['children']);result.append(n)
    return result
model['outliner']=prune(model['outliner'])
model['groups']=[g for g in model['groups'] if g['uuid'] not in remove_groups]

if 'aft_compaction' not in bindings:
    for e in model['elements']:
        # Shoulder lamp assemblies and the entire forward cockpit remain untouched.
        if e['name'].startswith('shoulder_lamp_'): continue
        rotation=e.get('rotation',[0,0,0])
        if rotation[0] or rotation[1]:
            # Translate tilted tail panels / top hatch pieces rigidly. An anisotropic
            # scale would shear their cuboids; all rotated cockpit pieces are ahead of CUT.
            pivot=e['origin'][2]
            # The hatch remains circular, all its pieces translate about one common pivot.
            if 'hatch' in chains[e['uuid']]: pivot=4.8
            delta=compact(pivot)-pivot
            for key in ('from','to','origin'): e[key][2]=round(e[key][2]+delta,7)
        elif 'hatch' in chains[e['uuid']]:
            delta=compact(4.8)-4.8
            for key in ('from','to','origin'): e[key][2]=round(e[key][2]+delta,7)
        else:
            for key in ('from','to','origin'): e[key][2]=round(compact(e[key][2]),7)
        if 'floor' in chains[e['uuid']] and e['name']=='deck_plate':
            e['to'][2]=min(e['to'][2],-54)
    # Only nonrotating aft group pivots move; forward console transforms stay identical.
    for g in model['groups']:
        if not any(g.get('rotation',[0,0,0])): g['origin'][2]=compact(g['origin'][2])
    bindings['aft_compaction']={'start_model_units':CUT,'scale':RATIO,
        'bulkhead_front_model_units':-58.5,'description':'Fixed cockpit; sealed compact machinery behind pilot'}
else:
    assert bindings['aft_compaction']['start_model_units']==CUT
    assert bindings['aft_compaction']['scale']==RATIO

# Close behind the seat rather than providing a doorway or aisle into the aft hull.
def uid(name): return str(uuid.uuid5(uuid.NAMESPACE_URL,'practicemod/deepseeker/aft-machinery/'+name))
group=copy.deepcopy(next(g for g in model['groups'] if g['name']=='interior_root'))
group.update(name='aft_machinery',uuid=uid('group'),origin=[0,0,0],children=[])
model['groups'].append(group)
node={'uuid':group['uuid'],'isOpen':True,'children':[]}
model['outliner'][0]['children'].append(node)
new=[]
def box(name,lo,hi,material='dark'):
    e={'name':'machinery_'+name,'type':'cube','uuid':uid(name),'box_uv':False,
       'from':list(lo),'to':list(hi),'origin':[(a+b)/2 for a,b in zip(lo,hi)],
       'autouv':0,'export':True,'color':4,'faces':copy.deepcopy(materials[material])}
    for face in e['faces'].values(): face['texture']=0
    model['elements'].append(e);node['children'].append(e['uuid']);new.append(e)
# Overlapping strips exactly cover the octagonal cross-section, with no door outline.
for row in range(22):
    y0=12.3+row*3; y1=min(77.7,y0+3.15)
    width=min(32.7,32.7*(1+math.tan(math.pi/8))-max(abs(y0-45),abs(y1-45)))
    box('bulkhead_'+str(row),(-width,y0,-58.5),(width,y1,-53))
# Two broad louver banks and a compact center drive cover; a shallow industrial face.
for side,x in enumerate((-22,6)):
    box('vent_bed_'+str(side),(x,32,-59.4),(x+16,64,-58.4),'steel')
    for row in range(5):
        box(f'vent_{side}_{row}',(x+1,35+row*5,-60.3),(x+15,37+row*5,-59.3))
box('drive_cover',(-5,27,-61),(5,69,-58.4),'steel')
box('drive_band',(-5.2,47,-61.3),(5.2,51,-61),'yellow')
# Just two broad pipe runs; no benches, bottles, luggage, screens, handles, or floor aisle.
for side,x in enumerate((-27,25)):
    box('conduit_'+str(side),(x,28,-60),(x+2,65,-58.4),'steel')
assert len(new)==38
PATH.write_text(json.dumps(model,indent=2)+'\n')
BINDINGS.write_text(json.dumps(bindings,indent=2)+'\n')
print(f'Compact aft hull: {len(new)} sealed machinery cuboids; removed {len(remove)} old fittings/deck pieces.')
