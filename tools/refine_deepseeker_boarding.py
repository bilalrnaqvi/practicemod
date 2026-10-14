#!/usr/bin/env python3
"""Author subtle painted boarding marks and chair boarding step without changing the cockpit reference."""
import copy,json,uuid,math
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'src/main/blockbench/deepseeker.bbmodel'
m=json.loads(p.read_text());prefix='boarding_'
old={e['uuid'] for e in m['elements'] if e['name'].startswith(prefix) or e['name'].startswith('hatch_handle_post')}
m['elements']=[e for e in m['elements'] if e['uuid'] not in old]
def clean(nodes):
 for n in nodes[:]:
  if isinstance(n,str):
   if n in old:nodes.remove(n)
  else:clean(n['children'])
clean(m['outliner'])
base=next(e for e in m['elements'] if e['name']=='hatch_hinge_1')
ids=[]
def cube(name,lo,hi):
 e=copy.deepcopy(base);e.update(name=prefix+name,uuid=str(uuid.uuid5(uuid.NAMESPACE_URL,prefix+name)),
     **{'from':[v*16 for v in lo],'to':[v*16 for v in hi],'origin':[(a+b)*8 for a,b in zip(lo,hi)]})
 e.pop('rotation',None);m['elements'].append(e);ids.append(e['uuid']);return e
# Flat, ochre stencil marks projected onto the existing hull skin. No rails or hardware.
def inverse(v,rot):
 v=list(v)
 for axis in (2,1,0):
  a,b=((1,2),(2,0),(0,1))[axis];ang=-math.radians(rot[axis]);c,ss=math.cos(ang),math.sin(ang)
  v[a],v[b]=c*v[a]-ss*v[b],ss*v[a]+c*v[b]
 return v
def surface(y,z):
 nearest=1000
 for e in m['elements']:
  if e['type']!='cube' or not e.get('export',True) or e['name'].startswith(prefix):continue
  pivot=e.get('origin',[0,0,0]);rot=e.get('rotation',[0,0,0])
  o=inverse([100-pivot[0],y-pivot[1],z-pivot[2]],rot);d=inverse([-1,0,0],rot);near,far=0,1000
  for i in range(3):
   lo=e['from'][i]-pivot[i];hi=e['to'][i]-pivot[i]
   if abs(d[i])<1e-9:
    if not lo<=o[i]<=hi:near=1001;break
   else:
    aa,bb=sorted(((lo-o[i])/d[i],(hi-o[i])/d[i]));near=max(near,aa);far=min(far,bb)
  if far>=near:nearest=min(nearest,near)
 assert nearest<100, (y,z)
 return 100-nearest+.025
for i in range(11):
 y=29+i*5.1;z=-41.7+max(0,y-60)*.73
 half=1.6 if y<60 else 3.4
 # Short interrupted strokes read as a worn climbing guide, not a physical ladder.
 for j,(za,zb) in enumerate(((z-half,z-.2),(z+.2,z+half))):
  coords=[(y-.27,za),(y+.27,za),(y+.27,zb),(y-.27,zb)]
  vs={str(k):[surface(yy,zz),yy,zz] for k,(yy,zz) in enumerate(coords)}
  name=prefix+'paint_'+str(i)+'_'+str(j);uid=str(uuid.uuid5(uuid.NAMESPACE_URL,name))
  e={'name':name,'uuid':uid,'type':'mesh','origin':[0,0,0],'rotation':[0,0,0],'export':True,
     'vertices':vs,'faces':{'paint':{'vertices':list(vs),'uv':{k:[285.5,3.5] for k in vs},'texture':0}}}
  m['elements'].append(e);ids.append(uid)
# A low step below the reversed seat supports the climb onto the elevated cushion.
cube('chair_step',( -.30,1.34,-4.06),(.30,1.70,-3.73))
for side in (-1,1):
 e=cube('handle_post_'+str(side),( .35+side*.22-.035,5.65,-2.435),(.35+side*.22+.035,6.02,-2.365))
 e['name']='hatch_handle_post_'+str(side)
# Make the pedestal a narrow swivel column so the lower legs clear it during rotation.
for e in m['elements']:
 if e['name']=='seat_foot':
  e['from'][0]=-2.5;e['to'][0]=2.5;e['from'][2]=-77.5;e['to'][2]=-72.5
  e['origin']=[(a+b)/2 for a,b in zip(e['from'],e['to'])]
# Clearance-fitted chair: the cushion supports the seated thighs rather than intersecting them.
for e in m['elements']:
 name=e['name']
 if name=='hatch_handle':
  e['from'][0]=1.1;e['to'][0]=10.1;e['origin'][0]=5.6
  e['from'][2]=-39.9;e['to'][2]=-36.9;e['origin'][2]=-38.4
  e['from'][1]=95.8;e['to'][1]=98.2;e['origin'][1]=97
 if name=='seat_pan':e['from'][1]=38.8;e['to'][1]=40.8
 if name=='seat_cushion':e['from'][1]=40.8;e['to'][1]=42.0
 if name=='seat_suspension':e['to'][1]=38.8;e['from'][1]=36.8
 if name=='seat_foot':e['to'][1]=36.8
 if name=='seat_side_trim':e['from'][1]=39;e['to'][1]=40.5
 if name in ('armrest','arm_support'):
  sign=1 if e['from'][0]>0 else -1
  e['from'][0],e['to'][0]=sorted((sign*9.8,sign*12.0))
 if name=='entry_door':e['to'][1]=68
 if name in ('seat_pan','seat_cushion','seat_suspension','seat_foot','seat_side_trim','armrest','arm_support','entry_door'):
  e['origin']=[(a+b)/2 for a,b in zip(e['from'],e['to'])]
# Taller clear doorway for stepping onto the raised chair. Retain its side jambs and lintel.
repl={};parts=[]
for e in m['elements']:
 if not e['name'].startswith('machinery_bulkhead_'):continue
 lo,hi=e['from'],e['to']
 if lo[0]>-9 or hi[0]<9 or lo[1]>=68 or hi[1]<=21.3:continue
 repl[e['uuid']]=[]
 for suffix,a,b in [('left',lo,[-9,hi[1],hi[2]]),('right',[9,lo[1],lo[2]],hi),('top',[-9,68,lo[2]],[9,hi[1],hi[2]])]:
  if any(b[i]<=a[i] for i in range(3)):continue
  n=copy.deepcopy(e);name=e['name']+'_clear_'+suffix
  n.update(name=name,uuid=str(uuid.uuid5(uuid.NAMESPACE_URL,name)),**{'from':a,'to':b,'origin':[(x+y)/2 for x,y in zip(a,b)]})
  repl[e['uuid']].append(n['uuid']);parts.append(n)
def replace(nodes):
 result=[]
 for n in nodes:
  if isinstance(n,str):result+=repl.get(n,[n])
  else:n['children']=replace(n['children']);result.append(n)
 return result
m['outliner']=replace(m['outliner']);m['elements']=[e for e in m['elements'] if e['uuid'] not in repl]+parts
def attach(nodes):
 for n in nodes:
  if isinstance(n,dict):
   if next(g for g in m['groups'] if g['uuid']==n['uuid'])['name']=='hull':n['children']+=ids;return True
   if attach(n['children']):return True
attach(m['outliner']);p.write_text(json.dumps(m,indent=2)+'\n')
