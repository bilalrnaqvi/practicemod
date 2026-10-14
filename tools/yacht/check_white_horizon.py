"""Static dimensions + sampled route player-AABB check. Not Minecraft physics."""
import json,math,sys
from pathlib import Path
from collections import defaultdict
R=Path(__file__).resolve().parents[2];D=R/'src/main/yacht/white_horizon';O=R/'output/yacht/white-horizon';O.mkdir(parents=True,exist_ok=True)
s=json.loads((D/'exports/scene.json').read_text());layout=json.loads((D/'white_horizon.layout.json').read_text());sol=[e for e in s['elements'] if e['solid']];grid=defaultdict(list)
for i,e in enumerate(sol):
 a,b=e['min'],e['max']
 for x in range(math.floor(a[0]),math.ceil(b[0])):
  for z in range(math.floor(a[2]),math.ceil(b[2])):grid[x,z].append(i)
def nearby(x,z):
 return {i for xx in range(math.floor(x-.3),math.ceil(x+.3)) for zz in range(math.floor(z-.3),math.ceil(z+.3)) for i in grid[xx,zz]}
def overlap(a,b,c,d):return a<d-1e-6 and b>c+1e-6
fail=[];measure=[];sample_count=0
for rt in layout['routes']:
 pts=rt['feet'];samples=[]
 for a,b in zip(pts,pts[1:]):
  n=max(1,math.ceil(math.dist(a,b)/.15))
  samples += [[a[j]+(b[j]-a[j])*t/n for j in range(3)] for t in range(n)]
 samples.append(pts[-1]);bad=[];clearance=99
 for x,y,z in samples:
  sample_count+=1;near=[sol[i] for i in nearby(x,z)]
  supports=[e['max'][1] for e in near if (e['category']!='furniture' or e['construction']=='carpet') and y-.6-1e-5<=e['max'][1]<=y+.6+1e-5 and overlap(x-.3,x+.3,e['min'][0],e['max'][0]) and overlap(z-.3,z+.3,e['min'][2],e['max'][2])]
  if not supports:bad.append({'feet':[x,y,z],'reason':'no nearby floor'});continue
  foot=max(supports)
  for e in near:
   a,b=e['min'],e['max']
   if overlap(x-.3,x+.3,a[0],b[0]) and overlap(z-.3,z+.3,a[2],b[2]):
    if a[1]>=foot-1e-5:clearance=min(clearance,a[1]-foot)
    if overlap(foot+1e-5,foot+1.8,a[1],b[1]):bad.append({'feet':[x,foot,z],'reason':'body collision','element':e['name']})
 if bad:fail.append({'route':rt['name'],'failures':len(bad),'examples':bad[:4]})
 measure.append({'route':rt['name'],'samples':len(samples),'min_headroom':None if clearance==99 else round(clearance,4),'pass':not bad})
# Architectural construction audit: cuboids only, zero rotations, minimum 1/16 lattice.
model=json.loads((R/'src/main/blockbench/white_horizon/white_horizon.bbmodel').read_text())
assert all(e['type']=='cube' and e['rotation']==[0,0,0] for e in model['elements'])
assert all(abs(v-round(v))<1e-6 for e in model['elements'] for v in e['from']+e['to'])
assert len({e['uuid'] for e in model['elements']})==len(model['elements'])
# Salon's central void excludes floors/fittings; glazing is at outer edge.
void=[]
for e in sol:
 a,b=e['min'],e['max']
 if all(overlap(lo,hi,aa,bb) for lo,hi,aa,bb in zip([-2.5,1.8,-3.5],[2.5,7.5,11.5],a,b)):void.append(e['name'])
if void:fail.append({'salon_void_obstructions':void[:8]})
# Check all solid joins, not only sampled paths.
occupied=defaultdict(list);intersections=[]
for i,e in enumerate(sol):
 a,b=e['min'],e['max'];near=set();cells=[]
 for x in range(math.floor(a[0]),math.ceil(b[0])):
  for y in range(math.floor(a[1]),math.ceil(b[1])):
   for z in range(math.floor(a[2]),math.ceil(b[2])):
    key=x,y,z;near.update(occupied[key]);cells.append(key)
 for j in near:
  f=sol[j]
  if all(overlap(aa,bb,cc,dd) for aa,bb,cc,dd in zip(a,b,f['min'],f['max'])):intersections.append([e['name'],f['name']])
 for key in cells:occupied[key].append(i)
if intersections:fail.append({'solid_intersections':intersections[:10]})
refs=[]
def visit(nodes):
 for n in nodes:
  if isinstance(n,str):refs.append(n)
  else:visit(n['children'])
visit(model['outliner'])
assert len(refs)==len(set(refs))==len(model['elements'])
assert set(refs)=={e['uuid'] for e in model['elements']}
assert len(layout['rooms'])==6
assert all(math.isfinite(v) for e in model['elements'] for v in e['from']+e['to'])
# Per-face texel density is exactly 16 pixels per block, including clipped joins.
for e in model['elements']:
 dx,dy,dz=[e['to'][i]-e['from'][i] for i in range(3)]
 dims={'north':(dx,dy),'south':(dx,dy),'east':(dz,dy),'west':(dz,dy),'up':(dx,dz),'down':(dx,dz)}
 for side,(w,h) in dims.items():
  u,v,U,V=e['faces'][side]['uv'];assert abs(abs(U-u)-w)<1e-6 and abs(abs(V-v)-h)<1e-6
assert all(t['width']==16 and t['height']==16 for t in model['textures'])
report={'phase':layout['phase'],'source_sha256':s['source_sha256'],'elements':len(sol),'player_aabb':[.6,1.8,.6],'route_step_tolerance':.6,'samples':sample_count,'routes':measure,'failures':fail,'solid_intersections':len(intersections),'outliner_coverage':True,'texture_density_pixels_per_block':16,'axis_aligned_cuboids_only':True,'pass':not fail,'limitations':'Sampled static AABB support/clearance only; open doors. No dynamic contacts, Minecraft stair shapes, fluids, latency or full navigable-space proof.'}
(O/(layout['phase']+'-geometry-check.json')).write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({'phase':layout['phase'],'samples':sample_count,'routes':len(measure),'pass':not fail,'failures':fail},indent=2))
sys.exit(bool(fail))
