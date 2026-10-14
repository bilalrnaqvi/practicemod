#!/usr/bin/env python3
"""One-time full-size cockpit revision of the approved CRAG, retaining its UVs.

The bbmodel remains the editable authority; this migration refuses to scale it twice.
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / 'src/main/blockbench/crag.bbmodel'
model = json.loads(path.read_text())
if model.get('crag_proportions') == 'steve-fit-v1':
    raise SystemExit('CRAG already has the Steve-fit proportions; edit the bbmodel directly.')
groups = {g['uuid']: g for g in model['groups']}
elements = {e['uuid']: e for e in model['elements']}

def transform(e, scale=(1, 1, 1), offset=(0, 0, 0), pivot=(0, 0, 0)):
    for key in ('from', 'to', 'origin'):
        e[key] = [round((v-pivot[i])*scale[i]+pivot[i]+offset[i], 8)
                  for i, v in enumerate(e[key])]

def beam(e, a, b, width):
    mid = [(a[i]+b[i])/2 for i in range(3)]
    delta = [b[i]-a[i] for i in range(3)]
    e['origin'] = mid
    if delta[0]:
        e['from'] = [min(a[0], b[0]), mid[1]-width/2, mid[2]-width/2]
        e['to'] = [max(a[0], b[0]), mid[1]+width/2, mid[2]+width/2]
        e['rotation'] = [0, 0, 0]
    else:
        length = math.hypot(delta[1], delta[2])
        e['from'] = [mid[0]-width/2, mid[1]-length/2, mid[2]-width/2]
        e['to'] = [mid[0]+width/2, mid[1]+length/2, mid[2]+width/2]
        e['rotation'] = [math.degrees(math.atan2(delta[2], delta[1])), 0, 0]

for node in model['outliner']:
    group = groups[node['uuid']]
    part = group['name']
    for uid in node['children']:
        e = elements[uid]
        name = e['name']
        if part.startswith('wheel_'):
            old = group['origin']
            new = [math.copysign(9.12, old[0]), 6.39, math.copysign(13.44, old[2])]
            transform(e, (.9, .9, .9), [new[i]-old[i] for i in range(3)], old)
        elif part == 'bucket_seat':
            # A wider cushion, upright back support and longer thigh support.
            transform(e, (1.10, 1, 1), (0, 1.76, -.72))
            if e['rotation'][0] == -14:
                e['rotation'][0] = -8
            if name == 'seat_base':
                e['from'][1] = 7.36  # pedestal reaches the floor
            if name == 'seat_base' or name.startswith('seat_bolster'):
                e['to'][2] = -3.2
            if name in ('seat_cushion', 'seat_cushion_center'):
                e['origin'] = [0, 12.8, -4.48]
                e['rotation'] = [25, 0, 0]
                transform(e, offset=(0, -.16, 0))
            if name == 'headrest':
                transform(e, offset=(0, 1.2, 2.1))
        elif part == 'steering':
            transform(e, offset=(0, 5.0, 2.28))
            if name != 'steering_hub':
                e['rotation'][0] = 20
        elif part == 'roll_cage':
            side = -1 if sum(e['from'][0:1]+e['to'][0:1]) < 0 else 1
            x = side*6.536
            if name.startswith('front_pillar'):
                beam(e, (x,17.08,9.36), (x,35.8,5.3), 1.8)
            elif name.startswith('rear_pillar'):
                beam(e, (x,15.78,-14.06), (x,35.8,-10.5), 1.8)
            elif name.startswith('roof_rail'):
                beam(e, (x,35.8,-10.5), (x,35.8,5.3), 1.8)
            elif name in ('roof_front', 'roof_rear'):
                z = 5.3 if name == 'roof_front' else -10.5
                beam(e, (-6.536,35.8,z), (6.536,35.8,z), 1.8)
            else:
                old_z = 2.2 if (e['from'][2]+e['to'][2])/2 > 0 else -7.5
                new_z = 5.3 if old_z > 0 else -10.5
                transform(e, offset=(x-side*7.6, 8.4, new_z-old_z))
        else:
            # Move axle assemblies as units so tires stay round and fenders stay fitted.
            center_z = (e['from'][2]+e['to'][2])/2
            shift_z = math.copysign(2.56, center_z) if abs(center_z) > 6 else 0
            transform(e, (.86, 1, 1), (0, 1.28, shift_z))
            if name in ('floor', 'central_skid'):
                e['from'][2] -= 2.56
                e['to'][2] += 2.56
            if name == 'central_skid':
                e['from'][1], e['to'][1] = 4.8, 6.8
            if name == 'floor':
                e['from'][1], e['to'][1] = 6.16, 7.36
            if name.startswith('footwell'):
                e['from'][1], e['to'][1] = 7.36, 7.52
                e['to'][2] = 6.8
            if name.startswith('axle'):
                transform(e, offset=(0, -1.99, 0))
            if name.startswith('spring'):
                # Keep the lower spring end close to the new axle height.
                e['from'][1] -= 1.99
    if part.startswith('wheel_'):
        group['origin'] = [math.copysign(9.12, group['origin'][0]), 6.39,
                           math.copysign(13.44, group['origin'][2])]
    elif part == 'steering':
        group['origin'] = [0, 20, 4.48]

model['crag_proportions'] = 'steve-fit-v1'
path.write_text(json.dumps(model, indent=2)+'\n')
print('Updated CRAG body, wheelbase, seat, controls and cage; preserved face UVs.')
