#!/usr/bin/env python3
"""Static Blockbench structure and player-envelope checks; not an animation test."""
import json,math
from pathlib import Path
R=Path(__file__).resolve().parents[1]
m=json.loads((R/'src/main/blockbench/deepseeker_expedition.bbmodel').read_text())
gs={g['uuid']:g for g in m['groups']};parents={}
def walk(ns,chain):
 for n in ns:
  if isinstance(n,str):
   assert n not in parents,'Duplicate outliner element'
   parents[n]=chain
  else:walk(n['children'],chain+[gs[n['uuid']]])
walk(m['outliner'],[])
assert set(parents)=={e['uuid'] for e in m['elements']}
assert len(parents)==len(m['elements'])
for e in m['elements']:
 if e['type']=='cube':assert all(b>a for a,b in zip(e['from'],e['to'])),e['name']
 for f in e['faces'].values():assert f.get('texture') is None or f['texture'] in (0,1)
def rot(p,deg):
 p=list(p)
 for i,d in enumerate(deg):
  a,b=((1,2),(2,0),(0,1))[i];c,s=math.cos(math.radians(d)),math.sin(math.radians(d));p[a],p[b]=c*p[a]-s*p[b],s*p[a]+c*p[b]
 return p
def transform(p,e):
 o=e.get('origin',[0,0,0]);r=rot([p[i]-o[i] for i in range(3)],e.get('rotation',[0,0,0]));return [r[i]+o[i] for i in range(3)]
def dot(a,b):return sum(x*y for x,y in zip(a,b))
axes=[[1,0,0],[0,1,0],[0,0,1]]
obbs=[]
for e in m['elements']:
 if any(g['name']=='Hatch_lid_rotate_X_to_open' for g in parents[e['uuid']]):continue
 if e['type']=='mesh':
  # Conservative local bounding box includes shaped tanks and hatch wedges.
  e=dict(e, **{'from':[min(v[i] for v in e['vertices'].values())+e.get('origin',[0,0,0])[i] for i in range(3)],'to':[max(v[i] for v in e['vertices'].values())+e.get('origin',[0,0,0])[i] for i in range(3)]})
 center=[(a+b)/2 for a,b in zip(e['from'],e['to'])];basis=axes
 for t in [e]+list(reversed(parents[e['uuid']])):
  center=transform(center,t);basis=[rot(v,t.get('rotation',[0,0,0])) for v in basis]
 obbs.append((e['name'],center,[(b-a)/2 for a,b in zip(e['from'],e['to'])],basis))
def overlap(c,h,obb):
 name,oc,oh,oa=obb;delta=[oc[i]-c[i] for i in range(3)]
 candidates=axes+oa+[[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]] for a in axes for b in oa]
 for ax in candidates:
  l=math.sqrt(dot(ax,ax))
  if l<1e-8:continue
  ax=[v/l for v in ax]
  radius=sum(h[i]*abs(ax[i]) for i in range(3))+sum(oh[i]*abs(dot(oa[i],ax)) for i in range(3))
  if abs(dot(delta,ax))>=radius-.02:return False
 return True
checks=[]
# Standard standing player envelope: 0.6 x 1.8 blocks; 16 model units per block.
# Stop at the storage approach: deeper case fronts start at z=4.97.
for z in range(-53,1):checks.append(('aisle',0,21.6,z))
for x in range(15):checks.append(('landing',x,21.6,-1))
for y in range(22,90):checks.append(('climb',14,y,-1))
fail=[]
for label,x,y,z in checks:
 for obb in obbs:
  if overlap([x,y+14.4,z],[4.8,14.4,4.8],obb):fail.append((label,(x,y,z),obb[0]))
if fail:
 for f in fail[:25]:print('COLLISION',f)
 raise SystemExit(f'{len(fail)} intersections')
# Preserve cockpit and controls byte-for-byte in the staged revision.
s=json.loads((R/'src/main/blockbench/archive/deepseeker_pre_expedition.bbmodel').read_text());old={e['uuid']:e for e in s['elements']};count=0
for e in m['elements']:
 if any(g['name'] in ('pilot_seat','front_console','left_console','right_console') for g in parents[e['uuid']]):
  assert e==old[e['uuid']],e['name'];count+=1
print(f'PASS: {len(m["elements"])} elements, complete outliner, valid dimensions and texture references.')
print(f'PASS: {len(checks)} standard-player envelope samples across aisle, landing and vertical hatch descent (lid open).')
print(f'PASS: {count} pilot-seat/control elements unchanged. Animated limbs and exterior climbing require later movement validation.')
# Furniture must not penetrate other assemblies or the actual rotated hull liners.
def obb_intersects(a,b):
 _,ac,ah,aa=a;_,bc,bh,ba=b;delta=[bc[i]-ac[i] for i in range(3)]
 tests=aa+ba+[[x[1]*y[2]-x[2]*y[1],x[2]*y[0]-x[0]*y[2],x[0]*y[1]-x[1]*y[0]] for x in aa for y in ba]
 for axis in tests:
  length=math.sqrt(dot(axis,axis))
  if length<1e-8:continue
  axis=[v/length for v in axis]
  extent=sum(ah[i]*abs(dot(aa[i],axis))+bh[i]*abs(dot(ba[i],axis)) for i in range(3))
  if abs(dot(delta,axis))>=extent-.03:return False
 return True
by_name={e['name']:e for e in m['elements']}
furniture={'Starboard_bunk_and_storage','Port_chart_repair_workstation','Port_aft_air_supply','Aft_expedition_storage'}
for a in obbs:
 ea=by_name[a[0]];ga=next((g['name'] for g in parents[ea['uuid']] if g['name'] in furniture),parents[ea['uuid']][-1]['name'])
 if ga not in furniture:continue
 for b in obbs:
  eb=by_name[b[0]];gb=next((g['name'] for g in parents[eb['uuid']] if g['name'] in furniture),parents[eb['uuid']][-1]['name'])
  # Same-assembly fittings and the needle's own hub are intentionally joined.
  if ga==gb or gb=='Air_supply_gauge_needle':continue
  assert not obb_intersects(a,b),f'Furniture penetration: {a[0]} / {b[0]}'
print('PASS: no furniture penetration into other assemblies or rotated hull geometry.')
# Check saved animation, exact loop seam and the needle sweep above its dial.
a=next(a for a in m['animations'] if a['name']=='animation.deepseeker.air_supply_idle')
assert a['loop']=='loop' and a['length']==2.4
ng=next(g for g in m['groups'] if g['name']=='Air_supply_gauge_needle')
k=a['animators'][ng['uuid']]['keyframes']
assert k[0]['time']==0 and k[-1]['time']==2.4
assert k[0]['data_points']==k[-1]['data_points']
assert min(float(v['data_points'][0]['x']) for v in k)==-2
assert max(float(v['data_points'][0]['x']) for v in k)==2
needle=by_name['air_gauge_needle'];dial=by_name['pressure_gauge_face'];pivot=ng['origin']
for j in range(81):
 angle=-2+j*.05
 for x in (needle['from'][0],needle['to'][0]):
  for y in (needle['from'][1],needle['to'][1]):
   for z in (needle['from'][2],needle['to'][2]):
    p=transform(transform([x,y,z],needle),dict(ng,rotation=[angle,0,0]))
    assert p[0]>dial['to'][0]+.05,'Needle intersects face'
    assert math.hypot(p[1]-pivot[1],p[2]-pivot[2])<2.3,'Needle intersects bezel'
print('PASS: looping +/-2 degree gauge animation; 81 needle sweep samples clear the dial and bezel.')
# Front-access panels can later fold down independently despite stacked cases.
access_groups=[g for g in m['groups'] if g['name'].startswith('Storage_') and g['name'].endswith('_front_access')]
assert len(access_groups)==3
for ag in access_groups:
 moving=[e for e in m['elements'] if ag in parents[e['uuid']]]
 moving_names={e['name'] for e in moving}
 for angle in range(0,-91,-15):
  for e in moving:
   c=[(x+y)/2 for x,y in zip(e['from'],e['to'])];basis=axes
   for part in [e]+list(reversed(parents[e['uuid']])):
    if part.get('uuid')==ag['uuid']:part=dict(part,rotation=[angle,0,0])
    c=transform(c,part);basis=[rot(axis,part.get('rotation',[0,0,0])) for axis in basis]
   candidate=(e['name'],c,[(y-x)/2 for x,y in zip(e['from'],e['to'])],basis)
   for other in obbs:
    if other[0] not in moving_names:assert not obb_intersects(candidate,other),(ag['name'],angle,e['name'],other[0])
print('PASS: all three storage panels clear the cabin and stacked cases at 15-degree samples from closed to 90 degrees open.')
