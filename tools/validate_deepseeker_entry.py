#!/usr/bin/env python3
"""Check the animated entry openings against the authored closed model."""
import json, math
from pathlib import Path
root=Path(__file__).resolve().parents[1]
m=json.loads((root/'src/main/blockbench/deepseeker.bbmodel').read_text())
def contains(e,p):
    if e['type']!='cube' or not e.get('export',True): return False
    origin=e.get('origin',[0,0,0]); p=[p[i]-origin[i] for i in range(3)]
    for axis in (2,1,0):
        a,b=((1,2),(2,0),(0,1))[axis]
        angle=-math.radians(e.get('rotation',[0,0,0])[axis]); c,s=math.cos(angle),math.sin(angle)
        p[a],p[b]=c*p[a]-s*p[b],s*p[a]+c*p[b]
    return all(e['from'][i]-origin[i]<p[i]<e['to'][i]-origin[i] for i in range(3))
static=[e for e in m['elements'] if not e['name'].startswith(('entry_door','hatch_center','hatch_handle','hatch_octagon_rim'))]
for x in (-4.8,0,4.8):
    for z in (-31.65,-26.85,-22.05):
        for y in range(23,94):
            hits=[e['name'] for e in static if contains(e,(x,y,z))]
            assert not hits, ('Hatch shaft blocked',x,y,z,hits)
for x in (-4.8,0,4.8):
    for y in (21.4,30,40,50,60,65):
        for z in range(-62,-51):
            hits=[e['name'] for e in static if contains(e,(x,y,z))]
            assert not hits, ('Doorway blocked',x,y,z,hits)
leaf=next(e for e in m['elements'] if e['name']=='entry_door')
lid=next(e for e in m['elements'] if e['name']=='hatch_center')
assert contains(leaf,(0,40,-55)) and contains(lid,(0,88,-26.85))
print('PASS: player-width hatch shaft and doorway clear when open; both have closed leaves.')
