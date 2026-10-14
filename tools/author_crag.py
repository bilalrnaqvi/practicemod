#!/usr/bin/env python3
"""Recreate CRAG's editable Blockbench source. Later artist edits belong in the bbmodel."""
import base64, hashlib, json, math, random, struct, uuid, zlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SIZE=1024
PALETTE=[(31,71,0),(51,99,16),(48,48,51),(66,65,68),(31,32,34),(115,116,120),(160,159,155),(255,226,175),(183,54,34),(92,83,65),(43,44,46),(19,46,0),(65,68,70),(220,212,188),(25,27,29),(137,138,139)]
def uid(n): return str(uuid.uuid5(uuid.NAMESPACE_URL,'practicemod:crag/'+n))
# Pack individual face islands so panel edge wear and seams belong to actual parts.
islands=[]
shelves=[]
pack_bottom=1
def island(name,face,uw,vh,material):
 global pack_bottom
 w=max(2,min(64,round(uw*2)));h=max(2,min(64,round(vh*2)))
 height=((h+2+3)//4)*4
 shelf=next((q for q in shelves if q[2]==height and q[0]+w+2<SIZE),None)
 if shelf is None:
  assert pack_bottom+height<SIZE,'Texture atlas overflow'
  shelf=[1,pack_bottom,height];shelves.append(shelf);pack_bottom+=height
 ox,oy=shelf[0],shelf[1]+1
 islands.append((name,face,material,ox,oy,w,h));shelf[0]+=w+2
 return [ox,oy,ox+w,oy+h]
def texture():
 pixels=[[(20,24,22,255) for x in range(SIZE)] for y in range(SIZE)]
 for name,face,material,ox,oy,w,h in islands:
  seed=int.from_bytes(hashlib.sha256((name+face).encode()).digest()[:8],'big')
  rng=random.Random(seed);base=PALETTE[material]
  patches={(x,y):rng.choice([-3,-1,0,2,4]) for y in range((h+3)//4) for x in range((w+3)//4)}
  for y in range(h):
   for x in range(w):
    delta=patches[(x//4,y//4)];c=base
    if material in (0,1,11):
     if y==0 or x==0:delta+=10
     if y==h-1 or x==w-1:delta-=8
     # Sparse chips on a few large armor plates, never all six faces of every part.
     if w>9 and h>8 and seed%4==0 and y<2 and x in (w-4,w-5):c=(99,111,86)
    elif material==10:
     delta+=7 if y%6==0 else 0
     if x in (1,w-2) and y%4==0:delta+=10
    elif material in (2,3,5,6,12):
     if y==0:delta+=7
     if y==h-1:delta-=6
    elif material==7:
     c=(255,239,207) if 1<x<w-2 and 1<y<h-2 else (223,188,127)
    pixels[oy+y][ox+x]=tuple(max(0,min(255,v+delta)) for v in c)+(255,)
  # Extend border texels into a one-pixel gutter.
  for yy in range(-1,h+1):
   for xx in range(-1,w+1):
    if xx in (-1,w) or yy in (-1,h):pixels[oy+yy][ox+xx]=pixels[oy+min(h-1,max(0,yy))][ox+min(w-1,max(0,xx))]
 data=bytearray()
 for row in pixels:
  data.append(0)
  for pixel in row:data.extend(pixel)
 def chunk(n,d):return struct.pack('>I',len(d))+n+d+struct.pack('>I',zlib.crc32(n+d))
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',SIZE,SIZE,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(data,9))+chunk(b'IEND',b'')
elements=[];groups=[];outliner=[]
def group(n,origin=(0,0,0)):
 groups.append(dict(name=n,uuid=uid(n),origin=list(origin),rotation=[0,0,0],export=True,visibility=True))
 outliner.append(dict(uuid=uid(n),isOpen=False,children=[]))
def cube(n,lo,hi,m=2,rotation=(0,0,0),origin=(0,0,0)):
 tx=m%4*32;ty=m//4*32
 e=dict(name=n,uuid=uid(n),type='cube',to=list(hi),origin=list(origin),rotation=list(rotation),box_uv=False,autouv=0,color=m%8,export=True,visibility=True,faces={f:dict(uv=[tx+1,ty+1,tx+31,ty+31],texture=0) for f in ('north','south','east','west','up','down')})
 dx,dy,dz=[hi[i]-lo[i] for i in range(3)]
 for face,(uw,vh) in dict(north=(dx,dy),south=(dx,dy),east=(dz,dy),west=(dz,dy),up=(dx,dz),down=(dx,dz)).items():
  fm=1 if m==0 and face=='up' else m
  ox,oy=(fm%4)*32,(fm//4)*32
  u,v=min(28,max(1,round(uw*2))),min(28,max(1,round(vh*2)))
  e['faces'][face]['uv']=island(n,face,uw,vh,fm)
 e['from']=list(lo);elements.append(e);outliner[-1]['children'].append(e['uuid'])
def beam(n,a,b,width,m=2):
 # All cage and suspension beams lie in a YZ plane or along a cardinal axis.
 mid=[(a[i]+b[i])/2 for i in range(3)];delta=[b[i]-a[i] for i in range(3)]
 length=math.sqrt(sum(d*d for d in delta));rot=(math.degrees(math.atan2(delta[2],delta[1])),0,0)
 if abs(delta[0])>0: cube(n,[min(a[0],b[0]),mid[1]-width/2,mid[2]-width/2],[max(a[0],b[0]),mid[1]+width/2,mid[2]+width/2],m)
 else:cube(n,[mid[0]-width/2,mid[1]-length/2,mid[2]-width/2],[mid[0]+width/2,mid[1]+length/2,mid[2]+width/2],m,rot,mid)
group('chassis')
cube('central_skid',(-5.8,5.1,-12),(5.8,7.2,13),4)
cube('floor',(-7.8,7.2,-10),(7.8,8.4,8),2)
cube('rear_engine',(-5.4,8,-12.5),(5.4,12.8,-7.5),3)
for s in (-1,1):
 x=s*8.1
 cube('rock_slider'+str(s),(x-1.0,6.8,-5),(x+1.0,8.2,4.2),5)
 cube('footwell'+str(s),(min(s*3,s*7.6),8.4,-4.7),(max(s*3,s*7.6),8.7,5),10)
 for z in (-10.88,10.88):
  beam('axle'+str(s)+str(z),(0,7.1,z),(s*10.7,7.1,z),1.7,2)
  beam('spring'+str(s)+str(z),(s*7.4,7.2,z),(s*7.4,13,z-1.8),1.1,5)
group('painted_armor')
cube('nose_lower',(-4.4,9.8,9),(4.4,12.7,16),11)
cube('nose_upper',(-4.8,12.7,8),(4.8,14.7,15.8),0)
cube('hood',(-4.8,14.7,4.7),(4.8,16.0,14.7),0,(7,0,0),(0,16,4.7))
cube('hood_center_plate',(-3.5,16.01,5.4),(3.5,16.45,12.6),0,(7,0,0),(0,16,4.7))
for side in (-1,1):
 a,b=sorted((side*3.5,side*4.6))
 cube('hood_edge_'+str(side),(a,15.8,5.4),(b,16.28,13.4),0,(7,0,0),(0,16,4.7))
 cube('hood_latch_'+str(side),(side*3.8-.3,14.4,13.9),(side*3.8+.3,15.15,14.35),5)
 cube('hood_vent_recess_'+str(side),(side*2.2-.65,16.47,6),(side*2.2+.65,16.6,9.2),4,(7,0,0),(0,16,4.7))
 for v in range(3):cube('hood_vent_fin_'+str(side)+'_'+str(v),(side*2.2-.6,16.6,6.25+v*.9),(side*2.2+.6,16.73,6.55+v*.9),2,(7,0,0),(0,16,4.7))
cube('dashboard',(-5.3,11.5,4),(5.3,15.5,6),2)
cube('instrument_recess',(-2.3,15.51,4.1),(2.3,15.7,5.8),4)
cube('instrument_display',(-1.8,15.71,4.35),(.8,15.76,5.5),12)
for j in range(3):cube('gauge_mark_'+str(j),(-1.6+j*.65,15.77,4.7),(-1.3+j*.65,15.82,5.1),13)
cube('warning_lamp',(1.35,15.71,4.5),(1.9,15.85,5.15),8)
cube('rear_deck',(-7.7,12.8,-14.2),(7.7,14.6,-8),0)
for s in (-1,1):
 def side(n,x0,x1,y0,y1,z0,z1,m=0,rotation=(0,0,0),origin=(0,0,0)):
  a,b=sorted((s*x0,s*x1));cube(n+str(s),(a,y0,z0),(b,y1,z1),m,rotation,origin)
 for z,label in ((10.88,'front'),(-10.88,'rear')):
  side(label+'_shoulder',5.3,9.2,13.4,16.6,z-3.5,z+3.5)
  side(label+'_arch_crown',9.2,13.4,14.9,16.6,z-3.2,z+3.2)
  side(label+'_raised_armor',9.1,12.9,16.61,17.0,z-2.9,z+2.9)
  side(label+'_outer_lip',13.4,13.75,14.7,16.4,z-3.1,z+3.1,11)
  # Outer arch has chunky sloping wings, rather than full-width flat mud flaps.
  for dz in (-1,1):
   pivot=(s*11.8,15.8,z+dz*3.0)
   side(label+'_arch_wing_'+str(dz),9.2,13.4,10.8,15.8,pivot[2]-.85,pivot[2]+.85,0,(-dz*35,0,0),pivot)
  side(label+'_rubber_pad',6.2,8.6,16.61,17.2,z-2.6,z+1.8,3)
  for dz in (-1,1):
   side(label+'_mount_bolt_'+str(dz),6.45,6.9,17.21,17.32,z+dz*1.3-.2,z+dz*1.3+.2,5)
 side('lamp_cheek',5.0,8.8,10.3,14.8,13.2,16.2)
 side('headlight_housing',5.3,8.5,10.7,14.2,16.21,16.9,4)
 side('headlight_bezel',5.65,8.15,11.05,13.85,16.91,17.05,5)
 side('warm_headlight',5.85,7.95,11.25,13.65,17.06,17.16,7)
 side('tail_lamp',5.8,8.0,12.5,14,-15.0,-14.4,8)
group('bumpers')
beam('front_bar',(-6.6,9.2,17.1),(6.6,9.2,17.1),1.7,5)
beam('rear_bar',(-8,8.6,-15.6),(8,8.6,-15.6),1.5,2)
cube('front_skid_plate',(-4.1,5.6,14.9),(4.1,8.5,15.9),6,(-14,0,0),(0,7,15.4))
for s in (-1,1):
 beam('brush_guard'+str(s),(s*6.6,9.2,17.1),(s*8.9,11.0,16.2),1.5,3)
 beam('guard_support'+str(s),(s*4.8,6.7,15.2),(s*6.6,9.2,17.1),1.1,2)
 cube('skid_slot'+str(s),(s*2-.65,6.8,16.12),(s*2+.65,7.5,16.24),4)
group('bucket_seat')
cube('seat_base',(-4.5,8.4,-8),(4.5,9.3,-.4),4)
cube('seat_cushion',(-4.2,9.3,-7.4),(4.2,10.8,-.5),10)
cube('seat_back',(-4.5,10,-8),(4.5,18.8,-6.5),2,(-14,0,0),(0,10,-7))
cube('seat_back_pad',(-3.6,11,-6.5),(3.6,17.8,-5.9),10,(-14,0,0),(0,10,-7))
cube('headrest',(-3.0,18.3,-10.4),(3.0,21,-8.7),3)
for side in (-1,1):
 x=side*2.3
 cube('harness_slot_'+str(side),(x-.6,16.8,-8.2),(x+.6,17.5,-5.8),4,(-14,0,0),(0,10,-7))
 cube('seat_side_wing_'+str(side),(x*1.7-.65,11.8,-7.5),(x*1.7+.65,17.4,-5.3),3,(-14,0,0),(0,10,-7))
cube('seat_cushion_center',(-2.8,10.81,-6.5),(2.8,11.04,-1),10)
for s in (-1,1):
 a,b=sorted((s*3.7,s*4.8))
 cube('seat_bolster'+str(s),(a,10,-7),(b,12,-.7),3)
group('roll_cage')
for s in (-1,1):
 x=s*7.6
 beam('front_pillar'+str(s),(x,15.8,6.8),(x,27.4,2.2),2.0)
 beam('rear_pillar'+str(s),(x,14.5,-11.5),(x,27.4,-7.5),2.0)
 beam('roof_rail'+str(s),(x,27.4,-7.5),(x,27.4,2.2),1.8)
 for z in (2.2,-7.5):
  cube('cage_joint'+str(s)+str(z),(x-1.12,26.28,z-1.12),(x+1.12,28.52,z+1.12),3)
  cube('cage_joint_band'+str(s)+str(z),(x-1.24,26.12,z-.6),(x+1.24,28.68,z+.6),5)
  cube('cage_joint_bolt'+str(s)+str(z),(x+s*1.3-.08,27.08,z-.3),(x+s*1.3+.08,27.72,z+.3),6)
beam('roof_front',(-7.6,27.4,2.2),(7.6,27.4,2.2),1.8)
beam('roof_rear',(-7.6,27.4,-7.5),(7.6,27.4,-7.5),1.8)
group('steering',(0,15,2.2))
for n,lo,hi in [('upper',(-3.1,17.1,1.7),(3.1,18,2.7)),('lower',(-3.1,12,1.7),(3.1,12.9,2.7)),('left',(-3.1,12.9,1.7),(-2.2,17.1,2.7)),('right',(2.2,12.9,1.7),(3.1,17.1,2.7)),('spoke',(-2.2,14.6,1.9),(2.2,15.4,2.7))]:
 cube('steering_'+n,lo,hi,2,(-28,0,0),(0,15,2.2))
cube('steering_hub',(-.7,14.3,1.8),(.7,15.7,3),5)
for i,(x,z) in enumerate(((-10.7,10.88),(10.7,10.88),(-10.7,-10.88),(10.7,-10.88))):
 y=7.1;group('wheel_'+str(i),(x,y,z))
 for j in range(4):
  cube(f'tire_{i}_{j}',(x-3.45,y-6.35,z-2.63),(x+3.45,y+6.35,z+2.63),4,(j*45,0,0),(x,y,z))
 # Offset blocks on the shoulders keep the tread visibly three dimensional.
 for j in range(12):
  for s in (-1,1):
   a,b=sorted((x+s*.2,x+s*3.65))
   cube(f'tread_{i}_{j}_{s}',(a,y+6.15,z-1.25),(b,y+7.0,z+1.25),3,(j*30+s*3,0,0),(x,y,z))
 for s in (-1,1):
  # Raised rubber sidewall and a hollow steel rim lip around a deeper dark hub.
  for j in range(12):
   angle=j*30
   cube(f'sidewall_{i}_{s}_{j}',(x+s*3.5-.12,y+3.2,z-1.47),(x+s*3.5+.12,y+5.55,z+1.47),2,(angle,0,0),(x,y,z))
   cube(f'rim_lip_{i}_{s}_{j}',(x+s*3.62-.16,y+2.6,z-.79),(x+s*3.62+.16,y+3.13,z+.79),5,(angle,0,0),(x,y,z))
  for j in range(4):
   cube(f'hub_recess_{i}_{s}_{j}',(x+s*3.48-.04,y-2.5,z-1.035),(x+s*3.48+.04,y+2.5,z+1.035),4,(j*45,0,0),(x,y,z))
  cube(f'hub_{i}_{s}',(x+s*3.55-.08,y-1.15,z-1.15),(x+s*3.55+.08,y+1.15,z+1.15),3)
  cube(f'axle_cap_{i}_{s}',(x+s*3.66-.07,y-.55,z-.55),(x+s*3.66+.07,y+.55,z+.55),5)
  for j in range(4):
   a=math.radians(j*90+45);by=y+math.cos(a)*1.75;bz=z+math.sin(a)*1.75
   cube(f'lug_bolt_{i}_{s}_{j}',(x+s*3.6-.08,by-.21,bz-.21),(x+s*3.6+.08,by+.21,bz+.21),6)
png=texture()
model=dict(meta=dict(format_version='5.0',model_format='free',box_uv=False),name='CRAG',model_identifier='crag',resolution=dict(width=SIZE,height=SIZE),elements=elements,groups=groups,outliner=outliner,textures=[dict(name='crag.png',id='0',uuid=uid('texture'),width=SIZE,height=SIZE,uv_width=SIZE,uv_height=SIZE,internal=True,source='data:image/png;base64,'+base64.b64encode(png).decode())])
(ROOT/'src/main/blockbench/crag.bbmodel').write_text(json.dumps(model,indent=2)+'\n')
print(f'Authored {len(elements)} cuboids in {len(groups)} editable groups')
