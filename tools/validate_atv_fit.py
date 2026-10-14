#!/usr/bin/env python3
"""Render the real ATV/player meshes, and check their static seated clearances."""
import csv
import itertools
import json
import math
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)
cp = ':'.join([str(ROOT/'build/classes/java/main'),
    str(ROOT/'build/moddev/artifacts/neoforge-21.1.235-merged.jar'),
    *(ROOT/'build/moddev/clientLegacyClasspath.txt').read_text().splitlines()])
out = ROOT/'build/crag-scale-preview'
out.mkdir(exist_ok=True)
java = Path('/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin')
subprocess.run([str(java/'javac'), '-proc:none', '-cp', cp, '-d', str(out),
    'tools/PreviewJetski.java', 'tools/PreviewAtvScale.java'], check=True)
subprocess.run([str(java/'java'), '-Djava.awt.headless=true', '-cp', str(out)+':'+cp,
    'com.bilal.practicemod.PreviewAtvScale'], check=True)

def dot(a,b): return sum(x*y for x,y in zip(a,b))
def cross(a,b): return [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]]
def rotate(p, e):
    o=e.get('origin',[0,0,0]); p=[p[i]-o[i] for i in range(3)]
    for axis,deg in enumerate(e.get('rotation',[0,0,0])):
        a,b=((1,2),(2,0),(0,1))[axis]; c,s=math.cos(math.radians(deg)),math.sin(math.radians(deg))
        p[a],p[b]=c*p[a]-s*p[b],s*p[a]+c*p[b]
    return [p[i]+o[i] for i in range(3)]

def overlap(a,b,axes_a,axes_b):
    depth=1e9
    for axis in axes_a+axes_b+[cross(x,y) for x in axes_a for y in axes_b]:
        norm=math.sqrt(dot(axis,axis))
        if norm<1e-6: continue
        axis=[v/norm for v in axis]
        aa=[dot(v,axis) for v in a]; bb=[dot(v,axis) for v in b]
        d=min(max(aa),max(bb))-max(min(aa),min(bb))
        if d<=0: return 0
        depth=min(depth,d)
    return depth

model=json.loads((ROOT/'src/main/blockbench/crag.bbmodel').read_text())
groups={g['uuid']:g for g in model['groups']}; parents={}
def visit(nodes,chain):
    for n in nodes:
        if isinstance(n,str): parents[n]=chain
        else: visit(n['children'],chain+[groups[n['uuid']]])
visit(model['outliner'],[])
boxes=[]
for e in model['elements']:
    chain=[e]+list(reversed(parents[e['uuid']]))
    points=[]
    for corner in itertools.product((0,1),repeat=3):
        p=[e['to'][i] if corner[i] else e['from'][i] for i in range(3)]
        for transform in chain: p=rotate(p,transform)
        points.append([v/16 for v in p])
    axes=[[points[index][i]-points[0][i] for i in range(3)] for index in (1,2,4)]
    boxes.append((e['name'],points,axes))

parts={}
for row in csv.reader((ROOT/'build/crag-rider-points.csv').open()):
    parts.setdefault(row[0],[]).append(list(map(float,row[1:])))
failures=[]
for part,vertices in parts.items():
    points=[v[:3] for v in vertices]
    axes=[v[3:] for v in vertices[::4]]
    for name,box,box_axes in boxes:
        depth=overlap(points,box,axes,box_axes)
        # Minecraft has solid cuboid hands: intentional contact encloses the side grips.
        if ((name in ('steering_left','steering_right') and '-driver/' in part) or
            (name == 'passenger_grab_bar' and '-passenger/' in part)) and ('arm' in part or 'sleeve' in part) and depth<=.065:
            continue
        # Tiny skin/padding contact is intentional; solid fittings must not cut through limbs.
        if depth>.018:
            failures.append(f'{part} intersects {name}: {depth:.4f} blocks')
# Check all combinations of standard/slim occupants against each other.
for a,av in parts.items():
    if '-driver/' not in a: continue
    for b,bv in parts.items():
        if '-passenger/' not in b: continue
        if overlap([v[:3] for v in av],[v[:3] for v in bv],
                   [v[3:] for v in av[::4]],[v[3:] for v in bv[::4]])>.005:
            failures.append(f'Occupants overlap: {a} / {b}')
if failures:
    raise SystemExit('\n'.join(failures))
print(f'PASS: both seats, wide/slim skins and layers against {len(boxes)} cuboids (0.018-block padding tolerance; steering/grab-rail contact allowed).')
