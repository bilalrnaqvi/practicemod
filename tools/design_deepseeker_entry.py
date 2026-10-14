"""Stage a continuous hatch/ladder route; never overwrite the integrated baseline."""
import copy
import json
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/blockbench/deepseeker-entry-redesign'
OUT.mkdir(parents=True, exist_ok=True)
m = json.loads((ROOT / 'src/main/blockbench/deepseeker.bbmodel').read_text())
original = {e['name']: e for e in m['elements']}
groups = {g['name']: g for g in m['groups']}

def node(uid, nodes=None):
    for n in m['outliner'] if nodes is None else nodes:
        if isinstance(n, dict):
            if n['uuid'] == uid:
                return n
            found = node(uid, n['children'])
            if found:
                return found

def group(name, parent):
    g = copy.deepcopy(groups['Starboard_interior_ladder'])
    g.update(name=name, uuid=str(uuid.uuid5(uuid.NAMESPACE_URL, 'deepseeker-entry/'+name)), children=[])
    m['groups'].append(g)
    n = {'uuid': g['uuid'], 'children': [], 'isOpen': True}
    node(groups[parent]['uuid'])['children'].append(n)
    return n

def cube(name, lo, hi, template, parent):
    e = copy.deepcopy(original[template])
    e.update(name=name, uuid=str(uuid.uuid5(uuid.NAMESPACE_URL, 'deepseeker-entry/'+name)),
             **{'from': lo, 'to': hi, 'origin': [(a+b)/2 for a,b in zip(lo,hi)]})
    e.pop('rotation', None)
    m['elements'].append(e)
    parent['children'].append(e['uuid'])
    return e

ladder = node(groups['Starboard_interior_ladder']['uuid'])
removed = set(ladder['children'])
removed.add(original['ladder_landing_grate']['uuid'])
def remove_references(nodes):
    nodes[:] = [n for n in nodes if not isinstance(n,str) or n not in removed]
    for n in nodes:
        if isinstance(n,dict):remove_references(n['children'])
remove_references(m['outliner'])
m['elements'] = [e for e in m['elements'] if e['uuid'] not in removed]
ladder['children'] = []
groups['Starboard_interior_ladder']['origin'] = [7, 21.55, -27]
for tag, z in [('front', -35.8), ('rear', -19.2)]:
    cube('entry_ladder_rail_'+tag, [6.4,21.55,z], [8,86.3,z+1.6], 'interior_ladder_rail_-10', ladder)
    cube('entry_ladder_foot_'+tag, [4.8,21.3,z-.6], [9.6,22.6,z+2.2], 'mast_socket', ladder)
    cube('entry_ladder_roof_mount_'+tag, [7.4,76,z-.3], [11,77.4,z+1.9], 'mast_socket', ladder)
for i, y in enumerate(range(27, 82, 6)):
    cube(f'entry_ladder_rung_{i:02}', [5.5,y,-34.2], [8,y+1.5,-19.2], 'interior_ladder_rung_6', ladder)
    cube(f'entry_ladder_tread_{i:02}', [5.4,y+1.5,-34.1], [8,y+1.7,-19.3], 'ladder_tread_6', ladder)
cube('entry_ladder_landing', [-6,21.3,-36], [5.3,21.55,-18], 'ladder_landing_grate', ladder)

# Move the one ceiling conduit out of the newly connected ladder shaft.
pipe = next(e for e in m['elements'] if e['name']=='ceiling_conduit_7')
for key in ('from','to','origin'):
    pipe[key][0] += 4

# Purposeful low starting ledge beside the lid, tied into the existing roof shoulder.
ledge = group('Entry_start_ledge', 'hatch')
cube('entry_start_ledge', [16,82.4,-38], [29,84,-16], 'hatch_base_right', ledge)
cube('entry_start_tread', [17,84,-37], [28,84.25,-17], 'ladder_landing_grate', ledge)
for z in (-36, -20):
    cube('entry_ledge_support_'+str(z), [18,78.5,z], [27,82.4,z+2], 'mast_socket', ledge)

# Side grip is reachable from the ledge without placing the head over the lid.
handle = next(e for e in m['elements'] if e['name']=='hatch_handle')
handle.update(**{'from':[11.4,95.2,-32], 'to':[13.8,97.2,-23.5], 'origin':[12.6,96.2,-27.75]})
for name,z in [('hatch_handle_post_-1',-31.3),('hatch_handle_post_1',-24.2)]:
    post = next(e for e in m['elements'] if e['name']==name)
    post.update(**{'from':[12.04,90.4,z-.56], 'to':[13.16,95.6,z+.56], 'origin':[12.6,93,z]})

contacts = {
    'units': 'Blockbench model pixels; 16 pixels per block',
    'status': 'Model proposal. Contact targets are staging references, not an authored animation.',
    'start_support_bounds': [[17,84.25,-37],[28,84.25,-17]],
    'start_head_clearance_envelope': [[18.5,91,-32],[26.5,101,-24]],
    'hatch_pivot': [0,93,-14.85], 'hatch_open_degrees': 90,
    'closed_lid_handle': [12.6,96.2,-27.75],
    'upper_grips': [[6.4,85,-35],[6.4,85,-18.4]],
    'rung_top_centers': [[6.7,y+1.7,-26.7] for y in range(27,82,6)],
    'descent_body_corridor': [[-5.4,22.7,-31.5],[4.2,116,-21.9]],
    'landing': [-.5,21.55,-27],
    'route': ['Crouch on ledge; head outside lid sweep.',
              'Lift handle, release; remain supported until lid stops.',
              'Plant hands on rim, vault feet over sill into opening.',
              'Face ladder before loading upper rung; compress on contact.',
              'Alternate planted hands and feet down continuous ladder.',
              'Step off onto landing, turn toward cockpit, join retained chair approach.'],
    'camera': 'Outside/cutaway views are model review only. First-person gameplay remains required.'
}
m.setdefault('practicemod', {})['entry_redesign'] = contacts
path = ROOT / 'src/main/blockbench/deepseeker_entry_redesign.bbmodel'
path.write_text(json.dumps(m,indent=2)+'\n')
(OUT/'entry-contacts.json').write_text(json.dumps(contacts,indent=2)+'\n')
print(path)
