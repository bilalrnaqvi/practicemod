#!/usr/bin/env python3
"""Run the actual Minecraft player-mesh clearance test after ./gradlew build."""
from pathlib import Path
import json
import math
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
classpath = ':'.join([str(root/'build/classes/java/main'),
    str(root/'build/moddev/artifacts/neoforge-21.1.235-merged.jar'),
    *(root/'build/moddev/clientLegacyClasspath.txt').read_text().splitlines()])
output = root/'build/deepseeker-preview'
output.mkdir(exist_ok=True)
for command in [
    ['javac', '-proc:none', '-cp', classpath, '-d', str(output), str(root/'tools/ValidateSubmarinePlayerMesh.java')],
    ['java', '-Djava.awt.headless=true', '-cp', str(output)+':'+classpath, 'com.bilal.practicemod.ValidateSubmarinePlayerMesh']]:
    result = subprocess.run(command, cwd=root)
    if result.returncode: sys.exit(result.returncode)

model = json.loads((root/'src/main/blockbench/deepseeker.bbmodel').read_text())
groups = {g['uuid']: g for g in model['groups']}
chains = {}
def walk(nodes, chain):
    for n in nodes:
        if isinstance(n, str): chains[n] = chain
        else: walk(n['children'], chain+[groups[n['uuid']]])
walk(model['outliner'], [])
def rotate(p, transform):
    origin = transform.get('origin', [0,0,0])
    p = [p[i]-origin[i] for i in range(3)]
    for axis, degrees in enumerate(transform.get('rotation', [0,0,0])):
        a,b = ((1,2),(2,0),(0,1))[axis]
        c,s = math.cos(math.radians(degrees)),math.sin(math.radians(degrees))
        p[a],p[b] = c*p[a]-s*p[b],s*p[a]+c*p[b]
    return [p[i]+origin[i] for i in range(3)]
control_boxes = []
for e in model['elements']:
    if not any(g['name'] in ('front_console','left_console','right_console','aft_machinery') for g in chains[e['uuid']]): continue
    corners = []
    for mask in range(8):
        p = [e['to'][i] if mask & (1<<i) else e['from'][i] for i in range(3)]
        for transform in [e]+list(reversed(chains[e['uuid']])): p = rotate(p, transform)
        corners.append([v/16 for v in p])
    control_boxes.append((e['name'], [min(p[i] for p in corners) for i in range(3)],
                          [max(p[i] for p in corners) for i in range(3)]))
for kind in ('wide','slim'):
    points = json.loads((root/f'build/deepseeker-player-{kind}.json').read_text())
    low = [min(p[i] for p in points) for i in range(3)]
    high = [max(p[i] for p in points) for i in range(3)]
    # Side consoles may sit beside the legs. Require a real spatial gap between
    # conservative world-aligned bounds, rather than forcing everything ahead of the toes.
    closest = min((math.sqrt(sum(max(0, lo[i]-high[i], low[i]-hi[i])**2 for i in range(3))),name)
                  for name,lo,hi in control_boxes)
    assert closest[0] >= .12, f'{kind} player too close to {closest[1]}: {closest[0]:.3f}'
    print(f'PASS: {kind} player bounds have {closest[0]:.3f} blocks of clearance from controls and machinery ({closest[1]} nearest)')
