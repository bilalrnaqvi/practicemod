#!/usr/bin/env python3
"""Author the Panorama dolphin as a native, textured, animation-ready Blockbench project.

This staged art source does not replace the runtime placeholder. Re-running
rebuilds the authored project; preserve subsequent manual edits before doing so.
"""
import base64, copy, hashlib, io, json, math, random, uuid
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/blockbench/mechanical-dolphin'; OUT.mkdir(parents=True,exist_ok=True)
SIZE=1024
PALETTE={'blue':(48,91,139,255),'blue_light':(66,117,170,255),'blue_dark':(32,61,92,255),
 'white':(185,192,192,255),'white_light':(212,216,210,255),'dark':(39,44,48,255),
 'rubber':(24,30,34,255),'steel':(103,114,120,255),'seat':(49,59,63,255),
 'cyan':(72,227,246,255),'lamp':(183,253,255,255),'glass':(69,185,219,92),
 'screen':(20,68,79,255),'teal':(36,91,100,255),'amber':(239,171,68,255)}
atlas=Image.new('RGBA',(SIZE,SIZE),(20,27,32,255)); draw=ImageDraw.Draw(atlas)
shelves=[];bottom=2;elements=[];groups=[];outliner=[];current=None
def uid(n):return str(uuid.uuid5(uuid.NAMESPACE_URL,'practicemod:panorama/'+n))
def group(name,origin=(0,0,0),parent=None):
 global current
 g=dict(name=name,uuid=uid(name),origin=list(origin),rotation=[0,0,0],export=True,visibility=True)
 groups.append(g);node=dict(uuid=g['uuid'],isOpen=False,children=[])
 (parent['children'] if parent else outliner).append(node);current=node;return node
def uv_tile(name,face,w,h,mat):
 global bottom
 w=max(2,min(128,round(w*2)));h=max(2,min(128,round(h*2)));sh=(h+5)//4*4
 shelf=next((s for s in shelves if s[2]==sh and s[0]+w+2<SIZE),None)
 if shelf is None:
  shelf=[2,bottom,sh];shelves.append(shelf);bottom+=sh
 assert bottom<SIZE, bottom
 x,y=shelf[0],shelf[1]+1;shelf[0]+=w+2
 seed=int.from_bytes(hashlib.sha256((name+face).encode()).digest()[:8],'big');rng=random.Random(seed)
 base=PALETTE[mat]
 patches={(a,b):rng.choice([-5,-2,0,2,4]) for a in range((w+5)//6) for b in range((h+5)//6)}
 for v in range(h):
  for u in range(w):
   d=patches[u//6,v//6];alpha=base[3]
   if mat=='glass':
    d=patches[u//6,v//6]*2
    if (u+v//2+seed%20)%max(25,w+13) in (2,3):d=50;alpha=130
    elif u==0 or v==0:d=30;alpha=110
   elif mat in ('cyan','lamp'):
    d=12 if 0<u<w-1 and 0<v<h-1 else -25
   elif mat=='seat':
    if v%8==0:d-=10
   else:
    if u==0 or v==0:d+=13
    if u==w-1 or v==h-1:d-=15
    if mat.startswith('blue') and w>16 and h>12:
     if (u==w//2 and w>38) or (v==h//2 and h>38):d-=9
   atlas.putpixel((x+u,y+v),tuple(max(0,min(255,c+d)) for c in base[:3])+(alpha,))
 if mat in ('blue','blue_light','white') and w>20 and h>12:
  for xx,yy in [(x+2,y+2),(x+w-4,y+h-4)]:draw.rectangle((xx,yy,xx+1,yy+1),fill=(127,148,159,255))
 if mat=='screen' and w>7 and h>4:
  draw.line((x+2,y+2,x+w-3,y+2),fill=PALETTE['cyan']);draw.line((x+3,y+4,x+3,y+h-2),fill=PALETTE['teal'])
 for v in range(-1,h+1):
  for u in range(-1,w+1):
   if u in (-1,w) or v in (-1,h):atlas.putpixel((x+u,y+v),atlas.getpixel((x+min(w-1,max(0,u)),y+min(h-1,max(0,v)))))
 return [x,y,x+w,y+h]
def add(e):elements.append(e);current['children'].append(e['uuid']);return e
def cube(n,lo,hi,mat='dark',rotation=(0,0,0),origin=None):
 lo=list(lo);hi=list(hi);dx,dy,dz=np.subtract(hi,lo)
 assert min(dx,dy,dz)>0,n
 faces={f:dict(uv=uv_tile(n,f,w,h,'blue_light' if mat=='blue' and f=='up' else mat),texture=0) for f,w,h in [('north',dx,dy),('south',dx,dy),('west',dz,dy),('east',dz,dy),('up',dx,dz),('down',dx,dz)]}
 e=dict(name=n,uuid=uid(n),type='cube',to=hi,origin=list(origin or (np.add(lo,hi)/2)),rotation=list(rotation),faces=faces,box_uv=False,autouv=0,export=True,visibility=True)
 e['from']=lo;return add(e)
def mesh(n,verts,polys,mat='blue',closed=True):
 verts=[np.array(v,dtype=float) for v in verts];center=np.mean(verts,axis=0);fs={}
 for i,poly in enumerate(polys):
  p=[verts[j] for j in poly];normal=np.cross(p[1]-p[0],p[2]-p[0])
  if closed and np.dot(normal,np.mean(p,axis=0)-center)<0:poly=list(reversed(poly));p=[verts[j] for j in poly]
  # Orthogonal per-face UVs retain consistent pixel density on tapered faces.
  u=p[1]-p[0];u/=max(np.linalg.norm(u),1e-9);nn=np.cross(p[1]-p[0],p[2]-p[0]);nn/=max(np.linalg.norm(nn),1e-9);v=np.cross(nn,u)
  coords=np.array([[np.dot(q-p[0],u),np.dot(q-p[0],v)] for q in p]);lo=coords.min(axis=0);extent=coords.max(axis=0)-lo
  material=mat[i] if isinstance(mat,list) else mat
  tx,ty,ex,ey=uv_tile(n,str(i),extent[0],extent[1],material)
  coords=(coords-lo)/np.maximum(extent,.001)*[ex-tx,ey-ty]+[tx,ty]
  keys=[str(j) for j in poly];fs[str(i)]=dict(vertices=keys,uv={k:list(map(float,q)) for k,q in zip(keys,coords)},texture=0)
 return add(dict(name=n,type='mesh',uuid=uid(n),origin=[0,0,0],rotation=[0,0,0],vertices={str(i):list(map(float,v)) for i,v in enumerate(verts)},faces=fs,export=True,visibility=True))
def prism(n,outline,axis,low,high,mat):
 # outline supplies the two coordinates other than the extrusion axis.
 axes=[i for i in range(3) if i!=axis];verts=[]
 for d in (low,high):
  for a,b in outline:
   q=[0,0,0];q[axis]=d;q[axes[0]]=a;q[axes[1]]=b;verts.append(q)
 k=len(outline);polys=[]
 # convex polygons triangulate into quads/triangles for native Blockbench mesh faces
 for layer in (0,k):
  for i in range(1,k-1):polys.append([layer,layer+i,layer+i+1])
 for i in range(k):j=(i+1)%k;polys.append([i,j,k+j,k+i])
 e=mesh(n,verts,polys,mat)
 # All coplanar cap triangles share one planar UV island, avoiding fan-shaped seams.
 coords=np.array(outline);mn=coords.min(axis=0);mx=coords.max(axis=0)
 for layer in (0,1):
  tx,ty,ex,ey=uv_tile(n,'cap'+str(layer),*(mx-mn),mat)
  mapped=(coords-mn)/(mx-mn)*[ex-tx,ey-ty]+[tx,ty]
  for i in range(k-2):
   f=e['faces'][str(layer*(k-2)+i)];f['uv']={key:list(map(float,mapped[int(key)%k])) for key in f['vertices']}
 return e
def beam(n,a,b,w,mat='dark',depth=None):
 a=np.array(a);b=np.array(b);mid=(a+b)/2;d=b-a;length=np.linalg.norm(d)
 rx=math.degrees(math.atan2(d[2],math.hypot(d[0],d[1])));rz=-math.degrees(math.atan2(d[0],d[1]))
 return cube(n,mid-[w/2,length/2,(depth or w)/2],mid+[w/2,length/2,(depth or w)/2],mat,(rx,0,rz),list(mid))
def section(z,w,bottom,top):
 bevel=min(1.6,(top-bottom)*.25)
 return [[-w*.70,bottom,z],[w*.70,bottom,z],[w,bottom+bevel,z],[w,top-bevel,z],[w*.72,top,z],[-w*.72,top,z],[-w,top-bevel,z],[-w,bottom+bevel,z]]
def loft(n,sections,mat,cap=True):
 k=len(sections[0]);verts=sum(sections,[]);p=[]
 for a in range(len(sections)-1):
  for j in range(k):p.append([a*k+j,a*k+(j+1)%k,(a+1)*k+(j+1)%k,(a+1)*k+j])
 if cap:
  for base in (0,(len(sections)-1)*k):
   for j in range(1,k-1):p.append([base,base+j,base+j+1])
 return mesh(n,verts,p,mat)

root=group('MECHANICAL_DOLPHIN_PANORAMA')
group('01_pressure_hull',parent=root)
stations=[(-34,2.3,4,6.4),(-28,4.5,3.7,8),(-23,7.3,2.8,9.2),(-15,9.0,2.2,9.7),(0,10,1.8,9.7),(14,9.5,2.2,9.7),(22,7.4,3,10.4),(29,4.2,4.5,10.4),(32,3.2,5.5,10)]
for i,(a,b) in enumerate(zip(stations,stations[1:])):loft('belly_pressure_panel_%02d'%i,[section(*a),section(*b)],'white_light' if i%3==0 else 'white')
cube('keel_skid',[-4.5,1.3,-13],[4.5,2.1,16],'dark')
loft('blue_sonar_beak',[section(-34,2.3,6.3,7.8),section(-28,4.5,7.9,10.1),section(-24.3,6.4,9.1,12.6)],'blue')
cube('dolphin_snout_white_tip',[-2.25,4.5,-36.2],[2.25,6.1,-32.5],'white_light')
cube('dolphin_snout_blue_tip',[-2.25,6.1,-36.2],[2.25,7.25,-32.5],'blue')
for s in (-1,1):
 for i,(z0,z1,x0,x1) in enumerate([(-24,-16,6.7,8.8),(-16,-5,8.8,9.8),(-5,6,9.8,10),(6,15,10,9.6)]):
  verts=[[s*x,y,z] for x,y,z in [(x0,6.9,z0),(x1,6.9,z1),(x1,10.9,z1),(x0,10.9,z0),(x0-1.15,7.2,z0),(x1-1.15,7.2,z1),(x1-1.15,10.8,z1),(x0-1.15,10.8,z0)]]
  mesh(f'blue_sill_{s}_{i}',verts,[[0,1,2,3],[4,7,6,5],[0,4,5,1],[3,2,6,7],[0,3,7,4],[1,5,6,2]],'blue')
 beam(f'canopy_seal_rail_{s}',[s*8.8,11.05,-15.8],[s*9.45,11.05,14.8],.5,'rubber')
 # Slim cyan sensor eyes and restrained side markers match the original design.
 for i,(x,y,z,l) in enumerate([(5.5,8.8,-26,3),(9.5,7.7,-12,4.2),(10,8,8.5,3.2)]):
  cube(f'light_housing_{s}_{i}',[s*x-.45,y-.8,z-l/2-.55],[s*x+.45,y+.8,z+l/2+.55],'dark')
  cube(f'cyan_lens_{s}_{i}',[s*x-.5,y-.36,z-l/2],[s*x+.5,y+.36,z+l/2],'cyan')
  cube(f'lens_core_{s}_{i}',[s*x-.53,y-.15,z-l/2+.25],[s*x+.53,y+.15,z+l/2-.25],'lamp')

group('02_prone_cockpit',parent=root)
cube('cockpit_floor',[-7.7,8,-20],[7.7,8.6,15],'dark')
cube('prone_couch_chassis',[-4.7,8.6,-12.5],[4.7,9.5,14.5],'rubber')
for i,(z0,z1,y) in enumerate([(-12.2,-6,10.0),(-5.6,1.8,9.7),(2.2,8,9.5),(8.4,14,9.3)]):
 cube(f'prone_pad_{i}',[-3.9,9.45,z0],[3.9,y+1.1,z1],'seat')
for s in (-1,1):
 cube(f'chest_side_bolster_{s}',[s*4.65-.55,9.3,-10],[s*4.65+.55,11.1,1.3],'dark')
 cube(f'forearm_support_{s}',[s*5.0-1.1,9,-18.3],[s*5.0+1.1,10.3,-11.4],'seat')
 cube(f'control_pedestal_{s}',[s*4.8-.65,9,-20.5],[s*4.8+.65,11.5,-18.6],'steel')
 beam(f'control_grip_{s}',[s*6,11.7,-19.7],[s*3.7,11.7,-19.7],.8,'rubber')
 cube(f'control_switch_{s}',[s*5.8-.24,12.05,-19.95],[s*5.8+.24,12.25,-19.4],'cyan' if s==1 else 'amber')
 cube(f'foot_stop_{s}',[s*2.3-1.65,9.5,13],[s*2.3+1.65,12.3,14.3],'steel',(-15,0,0),[s*2.3,9.5,13])
cube('dashboard_block',[-3.3,8.8,-23],[3.3,11.2,-20.3],'dark')
cube('navigation_display',[-2.8,11.2,-22.6],[2.8,11.35,-20.5],'screen')
cube('rear_cabin_bulkhead',[-7.7,8.4,14.6],[7.7,17.1,16],'dark')
for x in (-5.7,5.7):
 cube(f'bulkhead_mount_{x}',[x-.75,10.8,14.2],[x+.75,16.2,14.7],'steel')
cube('bulkhead_center_cover',[-3.5,11.4,14.1],[3.5,15.7,14.7],'blue_dark')

# Six-point arch: wide transparent sides, chamfered roof, flat center crown.
def arch(z,w,low,high):
 shoulder=high-min(3.4,(high-low)*.65)
 return [[-w,low,z],[-w,shoulder,z],[-w*.58,high,z],[w*.58,high,z],[w,shoulder,z],[w,low,z]]
def glazing(name,rings):
 for i,(a,b) in enumerate(zip(rings,rings[1:])):
  for j in range(5):mesh(f'{name}_glass_{i}_{j}',[a[j],a[j+1],b[j+1],b[j]],[[0,1,2,3]],'glass',False)
def rib(name,ring,width=.6):
 for i in range(5):beam(f'{name}_{i}',ring[i],ring[i+1],width,'dark')
fixed=[arch(-24.4,6.25,10.4,13.1),arch(-18.9,8.35,10.95,19.5)]
group('03_fixed_front_windscreen',parent=root);glazing('windscreen',fixed)
for i,r in enumerate(fixed):rib('windscreen_frame_'+str(i),r,.65)
for side in (0,5):beam('windscreen_lower_seal_'+str(side),fixed[0][side],fixed[1][side],.55,'dark')

HINGE=[0,11.15,15.4]
door=group('04_CANOPY_DOOR_rotate_X',HINGE,root)
rings=[arch(-18.1,8.35,11.05,19.5),arch(-8,9.1,11.05,21.6),arch(3.7,9.4,11.05,22.1),arch(14.7,8.95,11.05,19.9)]
glazing('panorama_canopy',rings)
for i,r in enumerate(rings):rib('door_arch_'+str(i),r,.65 if i in (1,2) else .85)
for s in (0,5):
 for i in range(3):beam(f'door_lower_rim_{s}_{i}',rings[i][s],rings[i+1][s],.72,'dark')
# Roof shoulder stringers follow the facets, leaving the panoramic side glass broad.
for j in (2,3):
 for i in range(3):beam(f'door_roof_edge_{j}_{i}',rings[i][j],rings[i+1][j],.38,'steel')
for s in (-1,1):
 beam(f'door_hinge_arm_{s}',[s*8.95,11.15,14.7],[s*8.95,11.15,15.4],1.35,'steel')
 cube(f'door_latch_{s}',[s*8.6-.55,10.55,-17.8],[s*8.6+.55,11.6,-15.8],'steel')
 beam(f'door_strut_attachment_lug_{s}',[s*9.2,11.5,3.7],[s*10.2,11.5,3.7],.65,'steel')

hardware=group('05_hinge_hardware',parent=root)
for s in (-1,1):
 current=hardware
 cube(f'hinge_mount_shoe_{s}',[s*9-.85,8.6,13.6],[s*9+.85,12.6,17.3],'dark')
 poly=[(11.15+1.1*math.cos(a*math.pi/4),15.4+1.1*math.sin(a*math.pi/4)) for a in range(8)]
 prism(f'hinge_axle_cap_{s}',poly,0,s*9-.95,s*9+.95,'steel')
 cube(f'hinge_axle_center_{s}',[s*9-1,10.7,14.95],[s*9+1,11.6,15.85],'dark')
 # Independent objects for the later two-point strut constraint/animation.
 beam(f'strut_fixed_mount_{s}',[s*7.8,10.7,20],[s*10.2,10.7,20],.8,'steel')
 group(f'STRUT_{s}_barrel', [s*10.2,10.7,20],root)
 beam(f'canopy_lift_barrel_{s}',[s*10.2,10.7,20],[s*10.2,11.093,12],.75,'dark')
 group(f'STRUT_{s}_rod',[s*10.2,11.093,12],root)
 beam(f'canopy_lift_rod_{s}',[s*10.2,11.093,12],[s*10.2,11.5,3.7],.35,'steel')

group('06_aft_engine_armor',parent=root)
loft('rear_canopy_abutment',[arch(15.35,8.95,8.5,19.9),arch(16.15,8.7,8.5,18.7)],'dark')
engine=[(16.1,8.7,8.5,18.7),(20.5,8.1,8.5,18.1),(25.5,6.3,7.8,15.9),(30.2,4.4,7.2,12.9)]
for i,(a,b) in enumerate(zip(engine,engine[1:])):loft('engine_blue_cowl_'+str(i),[section(*a),section(*b)],'blue')
for z,w,lo,hi in [(16,8.9,8.6,18.8),(25.6,6.35,7.8,15.95),(30.2,4.45,7.3,13)]:
 loft('engine_band_'+str(z),[section(z-.32,w,lo,hi),section(z+.32,w,lo,hi)],'dark')
for s in (-1,1):
 cube(f'engine_intake_{s}',[s*7.85-.4,10,20.5],[s*7.85+.4,13.2,23.7],'rubber')
 for z in [21,22,23]:cube(f'intake_louvre_{s}_{z}',[s*7.85-.45,10.4,z],[s*7.85+.45,12.9,z+.23],'steel')
 cube(f'engine_hinge_light_housing_{s}',[s*8.75-.75,12.4,15.5],[s*8.75+.75,15,19.8],'dark')
 cube(f'engine_hinge_cyan_{s}',[s*8.75-.8,13.05,16.4],[s*8.75+.8,14.05,19],'cyan')

group('07_dorsal_fin',[0,18,20],root)
cube('dorsal_foot',[-2.1,17.6,18],[2.1,19.1,26],'dark')
prism('swept_dorsal_dark_edge',[(18.6,18),(29.4,24.7),(30.2,30),(26.8,30),(18.6,26)],0,-1,1,'dark')
prism('swept_dorsal_blue_panel',[(19.1,19),(28.6,25),(29.3,29.4),(26.8,29.4),(19.1,25.5)],0,-1.08,1.08,'blue')
for s in (-1,1):
 cube(f'dorsal_light_socket_{s}',[s*1.1-.15,22.2,27.5],[s*1.1+.15,27,29],'dark')
 cube(f'dorsal_cyan_bar_{s}',[s*1.1-.18,22.8,27.8],[s*1.1+.18,26.6,28.6],'cyan')

for s,label in [(-1,'port'),(1,'starboard')]:
 group('08_'+label+'_pectoral_fin',[s*9,6.5,2],root)
 cube(label+'_fin_root',[s*10-2,5.2,-3],[s*10+2,8,5],'dark')
 for z in (-2,3):cube(label+'_root_cuff_'+str(z),[s*11-1.6,5,z-.5],[s*11+1.6,8.2,z+.5],'steel')
 outline=[(s*x,z) for x,z in [(11,-3.3),(17,-2),(23,3.5),(28,10),(28,12),(22,10.8),(16,7),(11,5.8)]]
 prism(label+'_flipper_dark_underside',outline,1,4.65,5.8,'dark')
 # Broad connected tapered armor sections with narrow dark seams.
 strips=[[(11,-3.3),(16,-2.25),(16,7),(11,5.8)],[(16.2,-2.1),(21,1.6),(21,10),(16.2,7.15)],[(21.2,1.8),(25.3,6.55),(25.3,11.45),(21.2,10.1)],[(25.5,6.8),(28,10),(28,12),(25.5,11.5)]]
 for i,strip in enumerate(strips):prism(label+'_flipper_plate_'+str(i),[(s*x,z) for x,z in strip],1,5.7,6.85-i*.17,'blue')
 x=s*23.2
 cube(label+'_fin_tip_lamp_socket',[x-1.2,6.45,6.5],[x+1.2,6.75,9.7],'dark')
 cube(label+'_fin_tip_lamp',[x-.73,6.75,6.9],[x+.73,6.87,9.3],'cyan')
 cube(label+'_fin_tip_core',[x-.42,6.87,7.1],[x+.42,6.92,9.1],'lamp')

tail=group('09_TAIL_peduncle',[0,8.5,30],root)
for i,(z,w) in enumerate([(31,3.5),(35,2.6),(39,2.3)]):
 loft('tail_joint_collar_'+str(i),[section(z,w,6.2,10.8),section(z+1.65,w,6.2,10.8)],'steel' if i==0 else 'dark')
 cube('tail_flexible_link_'+str(i),[-1.6,7,z+1.6],[1.6,10,z+4],'rubber')
 for s in (-1,1):cube(f'tail_joint_pin_{i}_{s}',[s*w-.3,7.4,z+.4],[s*w+.3,9.6,z+1.2],'steel')
flukes=group('10_TAIL_FLUKES_pitch_X',[0,8.5,42],tail)
cube('fluke_center_yoke',[-3,7.5,41],[3,10,45],'dark')
for s in (-1,1):
 outline=[(s*x,z) for x,z in [(1.4,42),(6,40),(13.3,42),(17,46),(16.7,49),(10.5,48),(5,46.2),(1.4,45.5)]]
 prism(f'tail_fluke_edge_{s}',outline,1,7.65,8.75,'dark')
 panels=[[(2,42),(6,40.4),(6,45.9),(2,45.2)],[(6.2,40.5),(11.1,41.7),(11.1,47.7),(6.2,46)],[(11.3,41.8),(16.5,46),(16.3,48.6),(11.3,47.8)]]
 for i,p in enumerate(panels):prism(f'tail_fluke_blue_{s}_{i}',[(s*x,z) for x,z in p],1,8.7,9.75,'blue')
 cube(f'tail_tip_lamp_mount_{s}',[s*13.3-1.9,8.2,48],[s*13.3+1.9,9.5,48.7],'dark')
 cube(f'tail_tip_cyan_{s}',[s*13.3-1.5,8.55,48.7],[s*13.3+1.5,9.1,48.83],'cyan')

buf=io.BytesIO();atlas.save(buf,format='PNG');data=buf.getvalue();(OUT/'panorama-atlas.png').write_bytes(data)
# Runtime-matched fin/tail clips are added after geometry authoring below.
animations=[]
model=dict(meta=dict(format_version='5.0',model_format='free',box_uv=False),name='Mechanical Dolphin — Panorama',model_identifier='mechanical_dolphin_panorama',resolution=dict(width=SIZE,height=SIZE),elements=elements,groups=groups,outliner=outliner,animations=animations,textures=[dict(name='panorama-atlas.png',id='0',uuid=uid('atlas'),width=SIZE,height=SIZE,uv_width=SIZE,uv_height=SIZE,source='data:image/png;base64,'+base64.b64encode(data).decode(),internal=True,render_mode='default',render_sides='double',visible=True,saved=True)])
path=ROOT/'src/main/blockbench/mechanical_dolphin.bbmodel';path.write_text(json.dumps(model,indent=2)+'\n')
opened=copy.deepcopy(model);opened['name']='Panorama — static open canopy review'
next(g for g in opened['groups'] if g['name']=='04_CANOPY_DOOR_rotate_X')['rotation']=[68,0,0]
# Static review struts connect their base and moving canopy attachment exactly.
theta=math.radians(68);rot=np.array([[1,0,0],[0,math.cos(theta),-math.sin(theta)],[0,math.sin(theta),math.cos(theta)]])
for s in (-1,1):
 a=np.array([s*10.2,10.7,20]);b=np.array(HINGE)+rot@(np.array([s*10.2,11.5,3.7])-HINGE);mid=a+(b-a)*.55
 for label,p,q,width in [('barrel',a,mid,.75),('rod',mid,b,.35)]:
  e=next(e for e in opened['elements'] if e['name']==f'canopy_lift_{label}_{s}');c=(p+q)/2;d=q-p;l=np.linalg.norm(d)
  e['from']=list(c-[width/2,l/2,width/2]);e['to']=list(c+[width/2,l/2,width/2]);e['origin']=list(c);e['rotation']=[math.degrees(math.atan2(d[2],d[1])),0,0]
(OUT/'mechanical_dolphin_open_review.bbmodel').write_text(json.dumps(opened,indent=2)+'\n')
bindings={'source':str(path.relative_to(ROOT)),'model_units_per_block':16,'forward':'-Z','door_group':'04_CANOPY_DOOR_rotate_X','door_uuid':uid('04_CANOPY_DOOR_rotate_X'),'hinge_origin':HINGE,'closed_rotation':[0,0,0],'open_review_rotation':[68,0,0],'animations_authored':False,'runtime_integrated':False,'strut_base_points':[[s*10.2,10.7,20] for s in (-1,1)],'strut_door_points_closed':[[s*10.2,11.5,3.7] for s in (-1,1)],'note':'Strut barrel/rod are independently grouped. Solve their two-point alignment and extension during the later animation pass.'}
(OUT/'door-bindings.json').write_text(json.dumps(bindings,indent=2)+'\n')
# Keep regenerated artist models on the current five-joint animation revision.
import runpy
runpy.run_path(str(ROOT/'tools/author_dolphin_swim_clips.py'), run_name='__main__')
print(f'Authored {len(elements)} elements, {len(groups)} groups, atlas height {bottom}/{SIZE}; current idle/swim fin and tail clips, no door animation.')
