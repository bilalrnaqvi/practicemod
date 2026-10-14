#!/usr/bin/env python3
"""Verify approved model promotion, texture packing and runtime moving-part bindings."""
import base64, io, json, math, re
from pathlib import Path
from PIL import Image
R=Path(__file__).resolve().parents[1]
model=R/'src/main/blockbench/deepseeker.bbmodel'
assert model.read_bytes()==(R/'src/main/blockbench/deepseeker_expedition.bbmodel').read_bytes()
m=json.loads(model.read_text()); groups={g['uuid']:g for g in m['groups']}; parents={}
def walk(nodes,chain):
 for n in nodes:
  if isinstance(n,str):parents[n]=chain
  else:walk(n['children'],chain+[groups[n['uuid']]['name']])
walk(m['outliner'],[])
es=[e for e in m['elements'] if e.get('export',True)]
code=(R/'src/main/java/com/bilal/practicemod/DeepseekerGeometry.java').read_text()
def values(name):
 s=re.search(r'\b'+name+r' = \{(.*?)\};',code).group(1)
 return json.loads('['+s+']')
assert values('NAMES')==[e['name'] for e in es]
for e,part,gauge in zip(es,values('ENTRY_PARTS'),values('GAUGE')):
 assert (part==1)==('Hatch_lid_rotate_X_to_open' in parents[e['uuid']]),e['name']
 assert gauge==('Air_supply_gauge_needle' in parents[e['uuid']]),e['name']
 assert part!=2,'Removed dividing door must not be exported'
assert sum(values('GAUGE'))==1
assert sum(values('GLASS'))==35
for s in re.findall(r'return new float\[\] \{\s*(.*?)\s*\};',code,re.S):
 a=[float(v.strip().removesuffix('F')) for v in s.split(',')]
 assert len(a)%32==0
 for i in range(0,len(a),8):
  assert all(math.isfinite(v) for v in a[i:i+8])
  assert 0<=a[i+3]<=1 and 0<=a[i+4]<=1
  assert abs(sum(v*v for v in a[i+5:i+8])-1)<1e-5
atlas=Image.open(R/'src/main/resources/assets/practicemod/textures/entity/deepseeker.png').convert('RGBA')
x=0
for t in m['textures']:
 im=Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).convert('RGBA')
 assert atlas.crop((x,0,x+im.width,im.height)).tobytes()==im.tobytes(), 'Texture colors/alpha changed during packing'
 x+=im.width
assert atlas.size==(4096,2048)
print(f'PASS: {len(es)} exported elements match approved source; hatch/gauge bindings, normals, UVs and both texture sheets verified.')
