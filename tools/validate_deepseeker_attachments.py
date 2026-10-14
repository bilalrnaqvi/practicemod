"""Check the tail's full roof support and unobstructed upper lamp lenses."""
import json,math
from pathlib import Path
m=json.loads((Path(__file__).resolve().parents[1]/'src/main/blockbench/deepseeker.bbmodel').read_text());groups={g['uuid']:g for g in m['groups']};parents={}
def walk(ns,chain=[]):
 for n in ns:
  if isinstance(n,str):parents[n]=chain
  else:walk(n['children'],chain+[groups[n['uuid']]])
walk(m['outliner'])
def rot(p,r,inverse=False):
 p=p[:]
 for axis in (reversed(range(3)) if inverse else range(3)):
  a,b=((1,2),(2,0),(0,1))[axis];angle=math.radians(r[axis])*(-1 if inverse else 1);c,s=math.cos(angle),math.sin(angle);p[a],p[b]=c*p[a]-s*p[b],s*p[a]+c*p[b]
 return p

def hit(o,d,e):
 for g in parents[e['uuid']]+[e]:
  org=g.get('origin',[0,0,0]);r=g.get('rotation',[0,0,0]);o=[v+org[i] for i,v in enumerate(rot([o[i]-org[i] for i in range(3)],r,True))];d=rot(d,r,True)
 if e['type']=='mesh':
  o=[o[i]-e.get('origin',[0,0,0])[i] for i in range(3)]
  def sub(a,b):return [a[i]-b[i] for i in range(3)]
  def dot(a,b):return sum(a[i]*b[i] for i in range(3))
  def cross(a,b):return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
  for f in e['faces'].values():
   vs=[e['vertices'][v] for v in f['vertices']]
   for a,b,c in [(vs[0],vs[1],vs[2]),(vs[0],vs[2],vs[3])]:
    ab,ac=sub(b,a),sub(c,a);p=cross(d,ac);det=dot(ab,p)
    if abs(det)<1e-9:continue
    t=sub(o,a);u=dot(t,p)/det
    if not 0<=u<=1:continue
    q=cross(t,ab);v=dot(d,q)/det;dist=dot(ac,q)/det
    if v>=0 and u+v<=1 and .001<dist<20:return True
  return False
 near,far=0,20
 for i in range(3):
  if abs(d[i])<1e-8:
   if not e['from'][i]<=o[i]<=e['to'][i]:return False
  else:
   a,b=sorted([(e['from'][i]-o[i])/d[i],(e['to'][i]-o[i])/d[i]]);near=max(near,a);far=min(far,b)
   if far<near:return False
 return far>max(near,.001)
for e in m['elements']:
 if not e['name'].startswith('shoulder_lamp_lens'):continue
 blocked={};r=e['rotation'];d=rot([0,0,-1],r);org=e['origin']
 world_normal=d[:]
 for group in reversed(parents[e['uuid']]):world_normal=rot(world_normal,group.get('rotation',[0,0,0]))
 assert all(abs(world_normal[i]-[0,0,-1][i])<1e-7 for i in range(3)), 'Shoulder lamps must face exactly forward'
 for ix in range(1,10):
  for iy in range(1,10):
   p=[e['from'][0]+(e['to'][0]-e['from'][0])*ix/10,e['from'][1]+(e['to'][1]-e['from'][1])*iy/10,e['from'][2]-.001]
   o=[v+org[i] for i,v in enumerate(rot([p[i]-org[i] for i in range(3)],r))]
   hits=[q['name'] for q in m['elements'] if q.get('export',True) and q is not e and hit(o,d,q)]
   for name in hits:blocked[name]=blocked.get(name,0)+1
 assert not blocked, (e['name'], 'Lens blocked by model geometry', blocked)
 print(e['name']+': all 81 lens samples clear')

# Glass over the bezel made its lower edge look thinner even with a clear lens.
for e in m['elements']:
 if not e['name'].startswith('shoulder_lamp_mount_housing_'):continue
 outer=[v for k,v in e['vertices'].items() if k.startswith('outer')]
 lip=[v for k,v in e['vertices'].items() if k.startswith('lip')]
 bx=(max(abs(v[0]) for v in outer)+max(abs(v[0]) for v in lip))/2
 by=(max(abs(v[1]) for v in outer)+max(abs(v[1]) for v in lip))/2
 for x,y in [(-bx,0),(bx,0),(0,-by),(0,by)]:
  p=rot([x,y,outer[0][2]-.001],e['rotation']);o=[p[i]+e['origin'][i] for i in range(3)]
  blockers=[q['name'] for q in m['elements'] if q is not e and q.get('export',True) and hit(o,[0,0,-1],q)]
  assert not blockers,(e['name'],'Bezel hidden by geometry',blockers)
print('All four bezel edges on both lamps are unobstructed')

parts={e['name']:e for e in m['elements']}
root,roof=parts['tail_root'],parts['pressure_panel_0']
for axis in (0,2):
 assert roof['from'][axis] <= root['from'][axis] < root['to'][axis] <= roof['to'][axis], 'Tail shoe overhangs roof'
assert roof['from'][1] < root['from'][1] < roof['to'][1] < root['to'][1], 'Tail shoe must penetrate roof'
fin=parts['tail_fin']
for vertex in fin['vertices'].values():
 if vertex[1]==min(v[1] for v in fin['vertices'].values()):
  assert all(root['from'][i]<=vertex[i]<=root['to'][i] for i in range(3)), 'Fin bottom not inside mounting shoe'
print('Tail mounting shoe fully supported by roof; fin bottom embedded in shoe')
