#!/usr/bin/env python3
"""Idempotent cube-only pressure-shell repair; remains editable/exportable in Blockbench."""
import copy, json, math, uuid
from pathlib import Path
root = Path(__file__).resolve().parents[1]
path = root / 'src/main/blockbench/deepseeker.bbmodel'
m = json.loads(path.read_text())
# Repair in the original design units, then return to the authored full-size model.
scale = json.loads((root/'src/main/blockbench/deepseeker_screen_bindings.json').read_text())['hull_scale_from_original']
for e in m['elements']:
    for key in ('from', 'to', 'origin'):
        e[key] = [v/scale for v in e[key]]
by_name = {e['name']: e for e in m['elements']}
groups = {g['name']: g for g in m['groups']}

def outliner(nodes, uid):
    for n in nodes:
        if isinstance(n, dict):
            if n['uuid'] == uid: return n
            found = outliner(n['children'], uid)
            if found: return found

def add(name, low, high, rotation=None, origin=None):
    e = copy.deepcopy(by_name['pressure_panel_0'])
    e.update(name=name, uuid=str(uuid.uuid5(uuid.NAMESPACE_URL, 'practicemod/deepseeker/'+name)),
             **{'from':low, 'to':high, 'origin':origin or [(a+b)/2 for a,b in zip(low,high)]})
    if rotation: e['rotation'] = rotation
    # Reuse the opaque hull's material, including the inside-facing surfaces.
    for face in e['faces'].values(): face['uv'] = [252, 4, 268, 28]
    existing = by_name.get(name)
    if existing: m['elements'][m['elements'].index(existing)] = e
    else:
        m['elements'].append(e)
        outliner(m['outliner'], groups['hull']['uuid'])['children'].append(e['uuid'])

# Continuous overlapping inner shell, behind the existing exterior plating.
# Port/starboard side walls are split around the two existing observation windows.
r, thickness = 10.5, .6
half = (r + thickness/2) * math.tan(math.pi/8) + .25
for side in [0,1,3,4,5,7]:
    angle = side*math.pi/4
    cx, cy = -r*math.sin(angle), 15+r*math.cos(angle)
    add(f'seal_pressure_liner_{side}', [cx-half,cy-thickness/2,-19.5],
        [cx+half,cy+thickness/2,33], [0,0,side*45], [cx,cy,0])
for sign in [-1,1]:
    lowx, highx = sorted([sign*10.2,sign*12.55])
    for name,y0,y1,z0,z1 in [('upper',18.4,20.4,-19.5,33),('lower',9.6,13.6,-19.5,33),
                             ('forward',13.4,18.6,-19.5,-3.8),('aft',13.4,18.6,8.6,33)]:
        add(f'seal_side_{sign}_{name}',[lowx,y0,z0],[highx,y1,z1])
    lowx, highx = sorted([sign*12.4,sign*13.18])
    for name,y0,y1,z0,z1 in [('upper',18.35,19.2,-4.2,9.1),('lower',12.9,13.65,-4.2,9.1),
                             ('forward',13.4,18.6,-4.3,-3.7),('aft',13.4,18.6,8.5,9.2)]:
        add(f'seal_port_gasket_{sign}_{name}',[lowx,y0,z0],[highx,y1,z1])
    # The old "inset" was a solid plate behind the glass; retain its source record but disable it.
    by_name[f'side_inset_{sign}']['export'] = False
    by_name[f'side_inset_{sign}']['visibility'] = False
    pane=by_name[f'side_port_{sign}']
    pane['from'][1],pane['to'][1] = 13.3,18.7
    pane['from'][2],pane['to'][2] = -4.15,9.0
# A complete rear pressure bulkhead, behind the original square engine mounting plate.
# Overlapping horizontal strips follow the octagonal inner profile without a square protrusion.
for row in range(22):
    y0=4.1+row
    y1=min(25.9,y0+1.05)
    # Octagon inequality is |x|+|y-15| <= r*(1+tan(pi/8)).
    halfwidth=min(10.9,10.9*(1+math.tan(math.pi/8))-max(abs(y0-15),abs(y1-15)))
    add(f'seal_stern_bulkhead_{row}',[-halfwidth,y0,32.4],[halfwidth,y1,33.2])
# Thicker, overlapping glazing closes the tapered facet seams while preserving all UVs/frames.
for e in m['elements']:
    if e['name'].startswith('canopy_facet_'):
        center=e['origin']; side,segment=map(int,e['name'].split('_')[-2:])
        original_half=[3.89361,4.22498,4.55635,4.88772][segment]
        e['from'][0],e['to'][0] = center[0]-original_half-.24,center[0]+original_half+.24
        e['from'][1],e['to'][1] = center[1]-.15,center[1]+.15
for e in m['elements']:
    for key in ('from', 'to', 'origin'):
        e[key] = [round(v*scale, 7) for v in e[key]]
path.write_text(json.dumps(m,indent=2)+'\n')
print('Sealed side walls, port gaskets, inner pressure shell, rear bulkhead and canopy seams.')
