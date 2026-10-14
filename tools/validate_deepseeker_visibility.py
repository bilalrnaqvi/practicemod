#!/usr/bin/env python3
"""Audit the exported model's horizon across the pilot's 180-degree look arc."""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
model = json.loads((ROOT / 'src/main/blockbench/deepseeker.bbmodel').read_text())
groups = {g['uuid']: g for g in model['groups']}
chains = {}

def visit(nodes, chain):
    for node in nodes:
        if isinstance(node, str):
            chains[node] = chain
        else:
            visit(node['children'], chain + [groups[node['uuid']]])

visit(model['outliner'], [])
eye = next(g['origin'] for g in model['groups'] if g['name'] == 'pilot_camera')

def inverse(point, transform, direction=False):
    pivot = transform.get('origin', [0, 0, 0])
    p = list(point) if direction else [point[i] - pivot[i] for i in range(3)]
    for axis in (2, 1, 0):
        a, b = ((1, 2), (2, 0), (0, 1))[axis]
        angle = math.radians(-transform.get('rotation', [0, 0, 0])[axis])
        c, s = math.cos(angle), math.sin(angle)
        p[a], p[b] = c*p[a]-s*p[b], s*p[a]+c*p[b]
    return p if direction else [p[i]+pivot[i] for i in range(3)]

def distance(element, ray):
    origin, direction = eye, ray
    for transform in chains[element['uuid']] + [element]:
        origin = inverse(origin, transform)
        direction = inverse(direction, transform, True)
    near, far = 0, math.inf
    for i in range(3):
        if abs(direction[i]) < 1e-10:
            if not element['from'][i] <= origin[i] <= element['to'][i]:
                return math.inf
        else:
            a, b = sorted(((element['from'][i]-origin[i])/direction[i],
                           (element['to'][i]-origin[i])/direction[i]))
            near, far = max(near, a), min(far, b)
            if far < near:
                return math.inf
    return near if far > 0 else math.inf

opaque = []
glass = []
for element in model['elements']:
    if not element.get('export', True):
        continue
    target = glass if any(g['name'] == 'cockpit_glass' for g in chains[element['uuid']]) else opaque
    target.append(element)

blocked = []
for yaw in range(-90, 91):
    angle = math.radians(yaw)
    ray = (math.sin(angle), 0, -math.cos(angle))
    glazing = min(distance(e, ray) for e in glass)
    obstruction, name = min((distance(e, ray), e['name']) for e in opaque)
    # Count frames beyond the near glass too: they still obstruct the exterior view.
    if not math.isfinite(glazing) or math.isfinite(obstruction):
        blocked.append((yaw, name))
print(f'Pilot {eye}: {181-len(blocked)}/181 horizon rays clear through glass')
print('Blocked angles:', blocked)
# Thin structural frames are acceptable, but no broad blind zones or blocked side views.
assert not any(abs(yaw) == 90 for yaw, _ in blocked), 'Side views must clear the hull'
assert len(blocked) <= 18, 'At least 90% of the forward hemisphere must remain visible'
longest = run = 0
for yaw in range(-90, 91):
    run = run + 1 if any(a == yaw for a, _ in blocked) else 0
    longest = max(longest, run)
assert longest <= 6, 'A structural obstruction spans more than six degrees'
print('PASS: 180-degree look arc with only narrow structural frames')
