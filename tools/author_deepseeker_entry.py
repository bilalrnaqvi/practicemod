#!/usr/bin/env python3
"""Cut a hatch shaft and cockpit doorway; preserve the existing atlas and fittings."""
import copy, json, uuid
from pathlib import Path
path = Path(__file__).resolve().parents[1] / 'src/main/blockbench/deepseeker.bbmodel'
m = json.loads(path.read_text())
already_authored = any(e['name'] == 'entry_door' for e in m['elements'])
replacements = {}
added = []
def part(e, name, lo, hi):
    n = copy.deepcopy(e)
    n.update(uuid=str(uuid.uuid5(uuid.NAMESPACE_URL, 'deepseeker-entry/' + name)), name=name,
             **{'from':lo, 'to':hi, 'origin':[(a+b)/2 for a,b in zip(lo,hi)]})
    added.append(n)
    return n['uuid']
for e in m['elements']:
    name = e['name']
    if already_authored and name != 'pressure_panel_0': continue
    if name.startswith('machinery_bulkhead_') and e['from'][1] < 55 and e['to'][1] > 21.3:
        lo,hi=e['from'],e['to']; ids=[]
        for suffix,a,b in [('left',lo,[ -9,hi[1],hi[2]]),('right',[9,lo[1],lo[2]],hi)]:
            ids.append(part(e,name+'_'+suffix,a,b))
        if lo[1]<21.3: ids.append(part(e,name+'_sill',[-9,lo[1],lo[2]],[9,21.3,hi[2]]))
        if hi[1]>55: ids.append(part(e,name+'_lintel',[-9,55,lo[2]],[9,hi[1],hi[2]]))
        replacements[e['uuid']]=ids
    # Open every horizontal skin crossed by the hatch shaft, including the base.
    if name in ('roof_panel','hatch_base','seal_pressure_liner_0','pressure_panel_0') or (name=='inner_pressure_liner' and e.get('rotation',[0,0,0])==[0,0,0]):
        lo,hi=e['from'],e['to']; ids=[]
        for suffix,a,b in [('left',lo,[-9,hi[1],hi[2]]),('right',[9,lo[1],lo[2]],hi),
                           ('front',[-9,lo[1],lo[2]],[9,hi[1],-38]),('rear',[-9,lo[1],-16],[9,hi[1],hi[2]])]:
            if all(b[i]>a[i] for i in range(3)): ids.append(part(e,name+'_'+suffix,a,b))
        replacements[e['uuid']]=ids
    if name.startswith(('machinery_drive_','machinery_vent')):
        # Door leaf carries the central fittings; vents sit outside its clear opening.
        if name.startswith('machinery_drive_'): e['name']='entry_door_'+name
        else:
            shift=-4 if e['from'][0]<0 else 4
            for key in ('from','to','origin'): e[key][0]+=shift
if not already_authored:
    base=next(e for e in m['elements'] if e['name']=='machinery_bulkhead_10')
    door=part(base,'entry_door',[-9,21.3,-58.6],[9,55,-53])
    floor=part(base,'entry_floor',[-15,20.1,-55],[15,21.3,-12])
def rewrite(nodes):
    result=[]
    for n in nodes:
        if isinstance(n,str): result.extend(replacements.get(n,[n]))
        else:
            n['children']=rewrite(n['children'])
            if not already_authored and next(g for g in m['groups'] if g['uuid']==n['uuid'])['name']=='aft_machinery': n['children'] += [door,floor]
            result.append(n)
    return result
m['outliner']=rewrite(m['outliner'])
m['elements']=[e for e in m['elements'] if e['uuid'] not in replacements]+added
path.write_text(json.dumps(m,indent=2)+'\n')
