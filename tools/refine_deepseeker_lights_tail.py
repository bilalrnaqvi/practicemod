#!/usr/bin/env python3
"""Apply approved shoulder lamps and single-panel mesh tail; preserve unrelated model parts."""
import base64, copy, io, json, math, uuid
from pathlib import Path
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]
PATH=ROOT/'src/main/blockbench/deepseeker.bbmodel'
TEX=ROOT/'src/main/resources/assets/practicemod/textures/entity/deepseeker.png'
m=json.loads(PATH.read_text()); original={e['name']:e for e in m['elements']}
groups={g['name']:g for g in m['groups']}
remove={e['uuid'] for e in m['elements'] if e['name'].startswith(('tail_', 'shoulder_lamp', 'shoulder_frame_'))}
def prune(nodes):
 for n in nodes[:]:
  if isinstance(n,str):
   if n in remove:nodes.remove(n)
  else:prune(n['children'])
prune(m['outliner']);m['elements']=[e for e in m['elements'] if e['uuid'] not in remove]
def node_for(uid,nodes):
 for n in nodes:
  if isinstance(n,dict):
   if n['uuid']==uid:return n
   found=node_for(uid,n['children'])
   if found:return found

def uid(name):return str(uuid.uuid5(uuid.NAMESPACE_URL,'deepseeker/refined/'+name))
dark=copy.deepcopy(original['central_pedestal']['faces'])
def add(e,group):
 m['elements'].append(e);node_for(groups[group]['uuid'],m['outliner'])['children'].append(e['uuid']);return e

def cube(name,lo,hi,group,faces=None,origin=None,rotation=None):
 e=dict(name=name,type='cube',uuid=uid(name),box_uv=False,export=True,from_=lo,to=hi,origin=origin or [(a+b)/2 for a,b in zip(lo,hi)],faces=copy.deepcopy(faces or dark),autouv=0)
 e['from']=e.pop('from_')
 if rotation:e['rotation']=rotation
 return add(e,group)

# A continuous tapered prism, with outward-wound planar quads.
def prism(name,outline,halfwidth,side_uv=None):
 vertices={f'{s}{i}':[x,y,z] for s,x in [('l',-halfwidth),('r',halfwidth)] for i,(y,z) in enumerate(outline)}
 polygons={'east':['r0','r1','r2','r3'],'west':['l3','l2','l1','l0']}
 for i in range(4):
  j=(i+1)%4;polygons[f'edge{i}']=[f'l{i}',f'l{j}',f'r{j}',f'r{i}']
 fs={}
 for key,vs in polygons.items():
  if side_uv and key in ('east','west'):
   tx,ty=side_uv[key]
   uv={v:[tx+(36-vertices[v][2] if key=='east' else vertices[v][2]-8)*2,ty+(120-vertices[v][1])*2] for v in vs}
  else:
   u0,v0,u1,v1=dark['north']['uv'];uv=dict(zip(vs,[[u0,v1],[u0,v0],[u1,v0],[u1,v1]]))
  fs[key]={'vertices':vs,'uv':uv,'texture':0}
 return add(dict(name=name,type='mesh',uuid=uid(name),origin=[0,0,0],rotation=[0,0,0],vertices=vertices,faces=fs,export=True),'tail')

# Native pixel atlas: two planar side tiles keep the digits undistorted and readable on both sides.
atlas=Image.open(TEX).convert('RGBA')
for side,tx,center in [('east',1800,20),('west',1860,36)]:
 # Existing mustard paint, tiled with deterministic pixel wear; no changes to old UV regions.
 for y in range(72):
  for x in range(56):
   noise=((x*17+y*31)%29)
   atlas.putpixel((tx+x,1800+y),(204+noise//3,139+noise//4,29+noise//5,255))
 draw=ImageDraw.Draw(atlas)
 digits=['1110111','1010001','1010111','1010001','1110111']
 for y,row in enumerate(digits):
  for x,on in enumerate(row):
   if on=='1':draw.rectangle([tx+center-7+x*2,1818+y*2,tx+center-6+x*2,1819+y*2],fill=(239,234,209,255))
prism('tail_fin',[(84,8),(120,24),(120,36),(84,36)],3.3,{'east':(1800,1800),'west':(1860,1800)})
prism('tail_leading_edge',[(84,7.3),(120,23.3),(120,25.0),(84,9.0)],3.6)
cube('tail_trailing_edge',[-3.55,84,35.7],[3.55,120.4,36.8],'tail')
cube('tail_tip',[-3.7,119.9,23.3],[3.7,121.1,36.8],'tail')
cube('tail_root',[-5.2,81.5,6.8],[5.2,86.0,36.2],'tail')
# The compact hull roof ends at Z=16.35. Seat the entire mounting shoe on it,
# rather than leaving the fin aft at the original long-hull coordinates.
for e in m['elements']:
 if not e['name'].startswith('tail_'):continue
 if e['type']=='mesh':
  for v in e['vertices'].values():v[2]-=20
 else:
  for key in ('from','to','origin'):e[key][2]-=20

# Shared pivot makes each shallow lens and its protective frame align exactly.
# Dedicated lens pixels preserve the concept's amber rim and pale luminous centre.
draw=ImageDraw.Draw(atlas)
draw.rectangle([1920,1800,1935,1815],fill=(134,111,64,255))
draw.rectangle([1921,1801,1934,1814],fill=(207,174,99,255))
draw.rectangle([1923,1803,1932,1812],fill=(255,229,159,255))
draw.rectangle([1925,1804,1931,1810],fill=(255,246,203,255))
draw.rectangle([1960,1800,1991,1831],fill=(44,52,55,255))
for x,y,w,h,shade in [(3,5,5,4,-3),(18,2,6,5,3),(11,20,5,6,-2),(24,25,6,4,2)]:
 draw.rectangle([1960+x,1800+y,1960+x+w,1800+y+h],fill=(44+shade,52+shade,55+shade,255))
lamp_metal={face:{'uv':[1960,1800,1992,1832],'texture':0} for face in dark}
# Join the lamp seats into one continuous canopy brow instead of isolated pods.
top_rib=next(e for e in m['elements'] if e['name']=='cockpit_mount_0')
top_rib['from'][0]=-22.0;top_rib['to'][0]=22.0;top_rib['from'][2]=-62.96
for sign in (-1,1):
 # Rotate only in the front plane: the optical axis is precisely authored -Z.
 # Rebuild the shoulder rib around the socket, rather than tipping the lamp outwards.
 angle=-sign*45;u=-sign*3.8;lift=3.2
 cx=sign*25.56192+math.cos(math.radians(angle))*u-math.sin(math.radians(angle))*lift
 cy=70.56192+math.sin(math.radians(angle))*u+math.cos(math.radians(angle))*lift
 cz=-63.3
 pivot=[cx,cy,cz];rotation=[0,0,angle]
 rib=next(e for e in m['elements'] if e['name']==f'cockpit_mount_{7 if sign==1 else 1}')
 # Keep a continuous rear spine to preserve the canopy attachment and seal.
 rib['from']=[sign*25.56192-15.423825,70.56192-2.4,-58.0]
 rib['to']=[sign*25.56192+15.423825,70.56192+2.4,-55.89]
 rib['origin']=[sign*25.56192,70.56192,-58.29]
 for label,a,b in [('lower',-15.423825,u-3.15),('upper',u+3.15,15.423825)]:
  if sign==1:a=max(a,-11.86)
  else:b=min(b,11.86)
  cube(f'shoulder_frame_{label}_{sign}',[rib['origin'][0]+a,68.16192,-62.88],
       [rib['origin'][0]+b,77.06192,-55.89],'frame',rib['faces'],rib['origin'],[0,0,angle])
 def part(suffix,lo,hi,material=None):
  lo=[lo[0]*.5,lo[1]*.5,lo[2]*.65];hi=[hi[0]*.5,hi[1]*.5,hi[2]*.65]
  return cube('shoulder_lamp_'+suffix+'_'+str(sign),[cx+lo[0],cy+lo[1],cz+lo[2]],[cx+hi[0],cy+hi[1],cz+hi[2]],'lights',material,pivot,rotation)
 part('mount_back',[-5.5,-5.6,1.4],[5.5,5.6,4.5],lamp_metal)
 # Four octagonal rings make a real sloping outer shell, broad face and recessed well.
 # The small corner bevels retain the Blockbench style without separate floating bars.
 vertices={};fs={}
 def ring(key,w,h,z,bevel):
  pts=[(-w+bevel,-h),(w-bevel,-h),(w,-h+bevel),(w,h-bevel),(w-bevel,h),(-w+bevel,h),(-w,h-bevel),(-w,-h+bevel)]
  for i,(x,y) in enumerate(pts):vertices[key+str(i)]=[x*.5,y*.5,z*.65]
 ring('rear',6.3,6.5,3.8,1.7)
 ring('seat',6.3,2.4,8.25,.3)
 for i in range(8):vertices['seat'+str(i)][1]-=lift
 # One continuous bezel: equal 2.4-unit border on all four sides.
 # Offset the corner bevel too, so the corners do not become thinner.
 ring('outer',6.2,6.4,-1.5,.25+2.4*(2-math.sqrt(2)))
 ring('lip',3.8,4.0,-1.5,.25)
 ring('well',3.2,3.4,.15,.15)
 u0,v0,u1,v1=lamp_metal['north']['uv']
 for band,(a,b) in enumerate([('seat','rear'),('rear','outer'),('outer','lip'),('lip','well')]):
  for i in range(8):
   j=(i+1)%8;vs=[a+str(i),b+str(i),b+str(j),a+str(j)]
   fs[f'{band}_{i}']={'vertices':vs,'uv':dict(zip(vs,[[u0,v1],[u0,v0],[u1,v0],[u1,v1]])),'texture':0}
 name='shoulder_lamp_mount_housing_'+str(sign)
 add(dict(name=name,type='mesh',uuid=uid(name),origin=pivot,rotation=rotation,vertices=vertices,faces=fs,export=True),'lights')
 lens={face:{'uv':[1920,1800,1936,1816],'texture':0} for face in dark}
 part('lens',[-3.2,-3.4,.18],[3.2,3.4,.5],lens)

# Generic Blockbench project supports the genuine tapered mesh and all existing cube bones.
m['meta']['model_format']='free'
buf=io.BytesIO();atlas.save(buf,format='PNG');data=buf.getvalue();TEX.write_bytes(data)
m['textures'][0]['source']='data:image/png;base64,'+base64.b64encode(data).decode()
m['textures'][0]['width']=m['textures'][0]['uv_width']=2048
m['textures'][0]['height']=m['textures'][0]['uv_height']=2048
PATH.write_text(json.dumps(m,indent=2)+'\n')
print('Updated shoulder lamps and tail:',len(m['elements']),'elements')
