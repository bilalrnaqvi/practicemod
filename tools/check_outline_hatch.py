"""Check restored hatch sweep and player-sized transfer volume using oriented-box SAT."""
import json, math, itertools, os
from pathlib import Path
R=Path(__file__).resolve().parents[1]
OPEN=float(os.environ.get('HATCH_OPEN','90'))
PIVOT=[0,93,-14.85]
m=json.loads((R/'src/main/blockbench/deepseeker_outline_ladder.bbmodel').read_text())
groups={g['uuid']:g for g in m['groups']};parents={}
def walk(ns,chain):
 for n in ns:
  if isinstance(n,str):parents[n]=chain
  else:walk(n['children'],chain+[groups[n['uuid']]])
walk(m['outliner'],[])
def rotate(p,o,angles):
 p=[p[i]-o[i] for i in range(3)]
 for axis,d in enumerate(angles):
  a,b=((1,2),(2,0),(0,1))[axis];c,s=math.cos(math.radians(d)),math.sin(math.radians(d));p[a],p[b]=c*p[a]-s*p[b],s*p[a]+c*p[b]
 return [p[i]+o[i] for i in range(3)]
def box(e,angle=0):
 ps=list(itertools.product(*zip(e['from'],e['to'])))
 for i,p in enumerate(ps):
  p=rotate(p,e.get('origin',[0,0,0]),e.get('rotation',[0,0,0]))
  for g in reversed(parents.get(e.get('uuid'),[])):p=rotate(p,g['origin'],g.get('rotation',[0,0,0]))
  if angle:p=rotate(p,PIVOT,[angle,0,0])
  ps[i]=p
 return ps
sub=lambda a,b:[x-y for x,y in zip(a,b)]
dot=lambda a,b:sum(x*y for x,y in zip(a,b))
def cross(a,b):return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
def overlap(a,b):
 aa=[sub(a[i],a[0]) for i in (1,2,4)];bb=[sub(b[i],b[0]) for i in (1,2,4)]
 depth=1e9
 for ax in aa+bb+[cross(x,y) for x in aa for y in bb]:
  length=math.sqrt(dot(ax,ax))
  if length<1e-8:continue
  x=[dot(p,ax)/length for p in a];y=[dot(p,ax)/length for p in b]
  d=min(max(x),max(y))-max(min(x),min(y))
  if d<=.015:return 0
  depth=min(depth,d)
 return depth
moving=lambda e:e['name'].startswith(('hatch_center','hatch_octagon_rim','hatch_handle'))
cubes=[e for e in m['elements'] if e['type']=='cube' and e.get('export',True)]
static=[(e,box(e)) for e in cubes if not moving(e)]
shaft=box({'from':[-4.8,21.7,-31.65],'to':[4.8,116,-22.05]})
shaft_hits=[(e['name'],round(overlap(shaft,b),3)) for e,b in static if overlap(shaft,b)]
hits={}
for angle in [OPEN*i/21 for i in range(1,22)]:
 for e in filter(moving,cubes):
  b=box(e,angle)
  for other,a in static:
   d=overlap(a,b)
   if d>0:hits[(e['name'],other['name'])]=max(d,hits.get((e['name'],other['name']),0))
open_hits=[e['name'] for e in filter(moving,cubes) if overlap(shaft,box(e,OPEN))]
report={'opening_degrees':OPEN,'pivot':PIVOT,'shaft_dimensions_pixels':[9.6,94.3,9.6],'shaft_obstructions':shaft_hits,'open_lid_obstructions':open_hits,'sweep_intersections':[{'lid':a,'fixed':b,'penetration_pixels':round(d,3)} for (a,b),d in hits.items()]}
out=R/'output/blockbench/deepseeker-outline-restored/clearance.json';out.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))

# Hinge knuckles intersect by design; the original closed seal overlaps by .75 px.
unexpected=[(a,b,d) for (a,b),d in hits.items() if not b.startswith('hatch_hinge_') and not (b.startswith('hatch_base_') and d<=.8)]
assert not shaft_hits and not open_hits and not unexpected, 'Clearance failed'
print('PASS: player envelope and sampled hatch sweep; original seal/hinge contacts allowed.')
