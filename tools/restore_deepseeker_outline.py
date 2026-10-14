"""Restore the approved archived exterior while retaining the expedition cabin for route revision."""
import copy, json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
old = json.loads((ROOT/'src/main/blockbench/archive/deepseeker_pre_expedition.bbmodel').read_text())
current = json.loads((ROOT/'src/main/blockbench/archive/deepseeker_raised_hatch_before_outline_restore.bbmodel').read_text())
assert old['textures'][0]['source'] == current['textures'][0]['source']

def names(model): return {g['uuid']:g['name'] for g in model['groups']}
def collect(nodes):
    groups, elements = set(), set()
    for n in nodes:
        if isinstance(n,str): elements.add(n)
        else:
            groups.add(n['uuid']); g,e=collect(n['children']);groups|=g;elements|=e
    return groups,elements

def pick(model, selected):
    ns=names(model)
    return [copy.deepcopy(n) for n in model['outliner'][0]['children']
            if isinstance(n,dict) and ns[n['uuid']] in selected]

exterior = pick(old, {'hull','cockpit_glass','frame','left_thruster','right_thruster','rear_thruster','hatch','lights','tail'})
interior = pick(current, {'interior_root','EXPEDITION_INTERIOR'})
og,oe=collect(exterior);cg,ce=collect(interior)
m=copy.deepcopy(current)
root=copy.deepcopy(old['outliner'][0]);root['children']=exterior+interior
m['outliner']=[root]
m['groups']=[copy.deepcopy(g) for g in old['groups'] if g['uuid'] in og or g['uuid']==root['uuid']]
used={g['uuid'] for g in m['groups']}
m['groups'] += [copy.deepcopy(g) for g in current['groups'] if g['uuid'] in cg and g['uuid'] not in used]
# Preserve the cockpit camera locator, which is stored as an empty top-level group.
for n in current['outliner'][1:]:
    if isinstance(n,dict) and names(current)[n['uuid']]=='pilot_camera':
        m['outliner'].append(copy.deepcopy(n))
        if n['uuid'] not in {g['uuid'] for g in m['groups']}:
            m['groups'].append(copy.deepcopy(next(g for g in current['groups'] if g['uuid']==n['uuid'])))
m['elements']=[copy.deepcopy(e) for e in old['elements'] if e['uuid'] in oe]+[copy.deepcopy(e) for e in current['elements'] if e['uuid'] in ce]
# Preserve the requested original side-wall ladder, trimming fittings that
# would protrude through the restored low roof. Furniture stays untouched.
ladder_node = pick(current, {'EXPEDITION_INTERIOR'})
def find_group(nodes, name):
    for n in nodes:
        if isinstance(n,dict):
            if names(current).get(n['uuid']) == name: return n
            found=find_group(n['children'],name)
            if found:return found
ladder=find_group(ladder_node,'Starboard_interior_ladder')
_,ladder_ids=collect([ladder])
removed=set()
for e in m['elements']:
    if e['uuid'] not in ladder_ids:continue
    if e['name'].startswith('interior_ladder_grip_'):
        removed.add(e['uuid']);continue
    # Original side-wall position; trim only the top to fit the restored low roof.
    if e['name'].startswith('interior_ladder_rail_'):
        e['from'][1]=21.55;e['to'][1]=66
    elif e['from'][1]>66:
        removed.add(e['uuid'])
m['elements']=[e for e in m['elements'] if e['uuid'] not in removed]
def prune(nodes):
    for n in nodes[:]:
        if isinstance(n,str):
            if n in removed:nodes.remove(n)
        else:prune(n['children'])
prune(m['outliner'])
# Keep the round archived lid; open aft from the handle; a raised hinge axis clears the aft mast.
for e in m['elements']:
    if e['name'].startswith('hatch_hinge_'):
        for key in ('from','to','origin'): e[key][1]+=3.9
moving={e['uuid'] for e in m['elements'] if e['name'].startswith(('hatch_center','hatch_octagon_rim','hatch_handle'))}
def detach(nodes):
    nodes[:]=[n for n in nodes if not isinstance(n,str) or n not in moving]
    for n in nodes:
        if isinstance(n,dict):detach(n['children'])
detach(m['outliner'])
hatch=next(g for g in m['groups'] if g['name']=='hatch')
uid='0cf19293-2428-490d-8744-9822ce950a18'
lid=copy.deepcopy(hatch);lid.update(uuid=uid,name='Hatch_lid_rotate_X_to_open',origin=[0,93,-14.85],rotation=[0,0,0])
m['groups'].append(lid)
def attach(nodes):
    for n in nodes:
        if isinstance(n,dict):
            if n['uuid']==hatch['uuid']:n['children'].append({'uuid':uid,'children':sorted(moving)})
            else:attach(n['children'])
attach(m['outliner'])
m['practicemod']={'hatch_open_degrees':90}
m['name']='Deepseeker — restored outline ladder exterior'
path=ROOT/'src/main/blockbench/deepseeker_outline_ladder.bbmodel'
path.write_text(json.dumps(m,indent=2)+'\n')
# Exact geometry/UV equality is the restoration criterion; no approximated repaint.
lookup={e['uuid']:e for e in m['elements']}
assert all(lookup[e['uuid']]==e for e in old['elements'] if e['uuid'] in oe and not e['name'].startswith('hatch_hinge_'))
assert all(lookup[e['uuid']]==e for e in current['elements'] if e['uuid'] in ce and e['uuid'] not in ladder_ids)
assert len(lookup)==len(m['elements'])
assert collect(m['outliner'])[1]==set(lookup)
assert sum(e['name'].startswith('boarding_paint_') for e in m['elements'])==22
assert not any(e['name'].startswith('exterior_ladder_') for e in m['elements'])
out=ROOT/'output/blockbench/deepseeker-outline-restored';out.mkdir(parents=True,exist_ok=True)
(out/'restoration.json').write_text(json.dumps({'source':str(path.relative_to(ROOT)),
    'archived_exterior_elements':len(oe),'preserved_interior_elements':len(ce),
    'painted_marks':22,'exterior_matches_archive_except_raised_hinges':True,'furniture_matches_current':True,'interior_ladder':'Original side-wall location; top trimmed beneath low restored roof; grip extensions removed',
    'status':'Restored runtime model; boarding animation paused pending route revision.'},indent=2)+'\n')
print(path)
