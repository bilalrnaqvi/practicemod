#!/usr/bin/env python3
"""Conservative seated torso/leg clearance through the supported pitch range.

The vanilla riding pose tilts the 12px legs forward by about 81 degrees.
Use separate padded torso/head and leg envelopes rather than an upright entity AABB.
This is a geometry check, not a substitute for client player-model rendering.
"""
import itertools
import json
import math
from pathlib import Path

root = Path(__file__).resolve().parents[1]
model = json.loads((root/'src/main/blockbench/deepseeker.bbmodel').read_text())
scale = json.loads((root/'src/main/blockbench/deepseeker_screen_bindings.json').read_text())['hull_scale_from_original']
eye = next(g['origin'] for g in model['groups'] if g['name'] == 'pilot_camera')
floor = max(e['to'][1] for e in model['elements'] if e['name'] == 'deck_plate')
minimum = math.inf
for pitch in range(-24, 25):
    angle = math.radians(pitch)
    for ys, zs in [((-.85, .25), (-.25, .25)), ((-1.2, -.65), (-.9, .15))]:
        for x, y, z in itertools.product((-.4, .4), ys, zs):
            # Inverse hull pitch: player remains upright while the submarine trims.
            p = [eye[0] + 16*x,
                 eye[1] + 16*(math.cos(angle)*y - math.sin(angle)*z),
                 eye[2] + 16*(math.sin(angle)*y + math.cos(angle)*z)]
            minimum = min(minimum, (p[1]-floor)/16)
            q = [v/scale for v in p]
            # Conservative inset pressure volume also used by the water-exclusion shader.
            radius = (8.45 + 3.2*max(0, min(1, (q[2]+38.5)/17.7))) if q[2] < -20.8 else 10.2
            assert -38.5 < q[2] < 32.4
            assert max(abs(q[0]), abs(q[1]-15)) < radius, (pitch, p)
            assert abs(q[0]) + abs(q[1]-15) < radius*math.sqrt(2), (pitch, p)
assert minimum >= .2, f'Seated avatar too close to deck: {minimum}'
print(f'PASS: padded seated avatar contained through +/-24 degrees; minimum deck clearance {minimum:.3f} blocks')
print(f'Eye-to-deck space: {(eye[1]-floor)/16:.3f} blocks')
