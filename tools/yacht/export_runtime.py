"""Partition canonical White Horizon cuboids into native Minecraft block cells."""
import base64, collections, hashlib, itertools, json, math
from pathlib import Path
R=Path(__file__).resolve().parents[2]
source=R/'src/main/blockbench/white_horizon/white_horizon.bbmodel'
m=json.loads(source.read_text()); out=R/'build/generated/yachtResources'; assets=out/'assets/practicemod'
def write(path,value):
 path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,separators=(',',':'))+'\n')
# Minecraft face UV axes, including their direction on each face.
axes={'north':(0,-1,1,-1),'south':(0,1,1,-1),'west':(2,1,1,-1),'east':(2,-1,1,-1),'up':(0,1,2,1),'down':(0,1,2,-1)}
def partition(target,e,info):
 assert e.get('rotation',[0,0,0])==[0,0,0]
 a=[v/16 for v in e['from']];b=[v/16 for v in e['to']]
 for p in itertools.product(*(range(math.floor(a[i]),math.ceil(b[i])) for i in range(3))):
  lo=[max(a[i],p[i]) for i in range(3)];hi=[min(b[i],p[i]+1) for i in range(3)]
  faces={}
  for f,(u,us,v,vs) in axes.items():
   uv=e['faces'][f]['uv']; uv2=[]
   for axis,sign,L,H in [(u,us,uv[0],uv[2]),(v,vs,uv[1],uv[3])]:
    t0=(lo[axis]-a[axis])/(b[axis]-a[axis]);t1=(hi[axis]-a[axis])/(b[axis]-a[axis])
    if sign<0:t0,t1=1-t1,1-t0
    uv2.append((L+(H-L)*t0,L+(H-L)*t1))
   faces[f]={'uv':[uv2[0][0],uv2[1][0],uv2[0][1],uv2[1][1]],'texture':'#'+info['material']}
   # The rectangular pool's source cuboids also meet at half-cell boundaries.
   if info['material']=='water':faces[f]['cullface']=f
  target[p].append({'from':[(lo[i]-p[i])*16 for i in range(3)],'to':[(hi[i]-p[i])*16 for i in range(3)],'faces':faces,'material':info['material'],'solid':info['solid'],'category':info['category'],'ladder':info.get('construction')=='ladder','fixture':info.get('fixture') if info['category']=='doors' else None})
cells=collections.defaultdict(list)
for e in m['elements']:partition(cells,e,m['practicemod']['elements'][e['uuid']])
palette=[];lookup={};positions=[]
def variant(elements):
 if not elements:return -1
 key=json.dumps(elements,sort_keys=True)
 if key not in lookup:lookup[key]=len(palette);palette.append(elements)
 return lookup[key]
for pos,elements in sorted(cells.items()):positions.append([*pos,variant(elements)])
doors=[]
for name in sorted({d['name'].rsplit('_',2)[0] for d in m['practicemod']['layout']['doors']}):
 leaves=[d for d in m['practicemod']['layout']['doors'] if d['name'].startswith(name+'_')]
 fixtures={d['name'] for d in leaves};closed=collections.defaultdict(list)
 for d in leaves:
  bounds=d['closed_bounds'][:];mid=(bounds[2]+bounds[5])/2
  if d['pivot'][2]<mid:bounds[5]=mid
  else:bounds[2]=mid
  for e in m['elements']:
   info=m['practicemod']['elements'][e['uuid']]
   if info.get('fixture')!=d['name']:continue
   cube=dict(e);cube['from']=[bounds[0]*16,e['from'][1],bounds[2]*16];cube['to']=[bounds[3]*16,e['to'][1],bounds[5]*16]
   partition(closed,cube,info)
 affected=set(closed)|{p for p,es in cells.items() if any(e['fixture'] in fixtures for e in es)}
 patches=[]
 for pos in sorted(affected):
  base=cells.get(pos,[]);shut=[e for e in base if e['fixture'] not in fixtures]+closed.get(pos,[])
  patches.append([*pos,variant(base),variant(shut)])
 doors.append({'name':name,'pivot':leaves[0]['pivot'],'patches':patches})
textures={t['name'].removesuffix('.png'):'practicemod:block/white_horizon/'+t['name'].removesuffix('.png') for t in m['textures']}
textures['particle']=textures['quartz']
for i,elements in enumerate(palette):
 children={}
 for layer in ('solid','translucent'):
  group=[{k:e[k] for k in ('from','to','faces')} for e in elements if (e['material'] in ('glass','water'))==(layer=='translucent')]
  if group:children[layer]={'textures':textures,'render_type':'minecraft:'+layer,'elements':group}
 write(assets/f'models/block/white_horizon/cell_{i}.json',{'loader':'neoforge:composite','textures':textures,'children':children})
write(assets/'blockstates/white_horizon_cell.json',{'variants':{f'cell={i}':{'model':f'practicemod:block/white_horizon/cell_{i}'} for i in range(len(palette))}})
def alpha_png(data,alpha):
 import struct,zlib
 at=8;compressed=b'';header=None
 while at<len(data):
  n=struct.unpack('>I',data[at:at+4])[0];kind=data[at+4:at+8];payload=data[at+8:at+8+n];at+=n+12
  if kind==b'IHDR':header=payload
  if kind==b'IDAT':compressed+=payload
 w,h,depth,color,*_=struct.unpack('>IIBBBBB',header);assert depth==8 and color==6
 raw=zlib.decompress(compressed);stride=w*4;previous=[0]*stride;rows=[]
 for y in range(h):
  mode=raw[y*(stride+1)];row=list(raw[y*(stride+1)+1:(y+1)*(stride+1)])
  for x in range(stride):
   left=row[x-4] if x>=4 else 0;up=previous[x];corner=previous[x-4] if x>=4 else 0
   if mode==1:row[x]=(row[x]+left)&255
   elif mode==2:row[x]=(row[x]+up)&255
   elif mode==3:row[x]=(row[x]+(left+up)//2)&255
   elif mode==4:
    p=left+up-corner;values=[left,up,corner];best=min(values,key=lambda v:abs(p-v));row[x]=(row[x]+best)&255
   else:assert mode==0
  previous=row[:]
  for x in range(3,stride,4):row[x]=min(row[x],alpha)
  rows.append(bytes([0]+row))
 def chunk(kind,payload):return struct.pack('>I',len(payload))+kind+payload+struct.pack('>I',zlib.crc32(kind+payload)&0xffffffff)
 return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',header)+chunk(b'IDAT',zlib.compress(b''.join(rows)))+chunk(b'IEND',b'')
for t in m['textures']:
 path=assets/'textures/block/white_horizon'/t['name'];path.parent.mkdir(parents=True,exist_ok=True)
 pixels=base64.b64decode(t['source'].split(',')[1])
 if t['name'] in ('glass.png','water.png'):pixels=alpha_png(pixels,56 if t['name']=='glass.png' else 166)
 path.write_bytes(pixels)
write(assets/'models/item/white_horizon_deed.json',{'parent':'minecraft:item/generated','textures':{'layer0':'minecraft:item/map'}})
write(out/'data/practicemod/recipe/white_horizon_deed.json',{'type':'minecraft:crafting_shaped','pattern':['DID','INI','DID'],'key':{'D':{'item':'minecraft:diamond_block'},'I':{'item':'minecraft:iron_block'},'N':{'item':'minecraft:nether_star'}},'result':{'id':'practicemod:white_horizon_deed','count':1}})
# Physical rails close the visual gaps, matching Minecraft fence/pane semantics.
shapes=[]
for es in palette:
 boxes=[e['from']+e['to'] for e in es if e['solid']]
 if any(e['category']=='rails' for e in es):
  for e in es:
   if e['category']=='rails':
    a=e['from'][:];b=e['to'][:]
    a[1]=0;b[1]=max(16,b[1]);boxes.append(a+b)
 shapes.append({'ladder':any(e['ladder'] for e in es),'boxes':boxes,'water':any(e['material']=='water' for e in es),'light':15 if any(e['material']=='lamp' for e in es) else 0})
write(out/'white_horizon_runtime.json',{'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'palette':shapes,'cells':positions,'doors':doors,'layout':m['practicemod']['layout']})
print(f'White Horizon: {len(positions)} cells, {len(palette)} render variants, canonical geometry preserved')

# The captain controller holds the design waterline; avoid a second buoyancy controller.
write(out/'data/practicemod/physics_block_properties/white_horizon.json',{'selector':'practicemod:white_horizon_cell','properties':{'sable:volume':0.0}})

write(out/'practicemod.yacht.mixins.json',{'required':True,'minVersion':'0.8','package':'com.bilal.practicemod.yacht.runtime.mixin','compatibilityLevel':'JAVA_21','client':['YachtLiquidRendererMixin'],'injectors':{'defaultRequire':1}})
