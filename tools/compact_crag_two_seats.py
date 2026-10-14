#!/usr/bin/env python3
"""Narrow the two-seat CRAG's exterior while preserving both fitted cockpits."""
import json
from pathlib import Path

root=Path(__file__).resolve().parents[1]
path=root/'src/main/blockbench/crag.bbmodel'
m=json.loads(path.read_text())
assert m.get('crag_proportions')=='two-seat-v1', 'Requires the wide two-seat revision; do not apply twice.'
groups={g['uuid']:g for g in m['groups']}
elements={e['uuid']:e for e in m['elements']}
for node in m['outliner']:
    g=groups[node['uuid']]; part=g['name']
    if part in ('driver_seat','passenger_seat','steering'): continue
    scale=.92 if part=='roll_cage' else .85
    for id in node['children']:
        e=elements[id]; name=e['name']
        if name.startswith(('driver_','passenger_')): continue
        if name.startswith('rock_slider'):
            # Keep the inner face clear of the outside boot; trim only the outer edge.
            e['from'][0],e['to'][0]=(-17.28,-15.36) if e['from'][0]<0 else (15.36,17.28)
            continue
        for key in ('from','to','origin'): e[key][0]=round(e[key][0]*scale,8)
        if name=='floor': e['from'][0],e['to'][0]=-15.68,15.68
    if part.startswith('wheel_'):g['origin'][0]=round(g['origin'][0]*scale,8)
m['crag_proportions']='compact-two-seat-v1'
path.write_text(json.dumps(m,indent=2)+'\n')
print('Narrowed shell, cage, tires and track; preserved both seat/hand/foot anchors.')
