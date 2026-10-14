#!/usr/bin/env python3
"""Render the actual .bbmodel faces/UVs, including glass and static group poses."""
import base64,io,json,math
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont
R=Path(__file__).resolve().parents[1];O=R/'output/blockbench/mechanical-dolphin'
def rotation(r):
 x,y,z=np.radians(r);cx,cy,cz=np.cos([x,y,z]);sx,sy,sz=np.sin([x,y,z])
 return np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],[sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])
def geometry(path,animation=None,time=0):
 m=json.loads(path.read_text());gs={g['uuid']:dict(g) for g in m['groups']};parents={}
 if animation:
  clip=next(a for a in m['animations'] if a['name']==animation);t=time%clip['length']
  for key,track in clip['animators'].items():
   keys=sorted(track['keyframes'],key=lambda k:k['time'])
   for a,b in zip(keys,keys[1:]):
    if a['time']<=t<=b['time']:
     f=(t-a['time'])/(b['time']-a['time']);va=np.array([float(a['data_points'][0][axis]) for axis in 'xyz']);vb=np.array([float(b['data_points'][0][axis]) for axis in 'xyz'])
     gs[key]['rotation']=list(np.array(gs[key]['rotation'])+va+(vb-va)*f);break
 def walk(nodes,chain):
  for n in nodes:
   if isinstance(n,str):parents[n]=chain
   else:walk(n['children'],chain+[gs[n['uuid']]])
 walk(m['outliner'],[])
 tex=np.array(Image.open(io.BytesIO(base64.b64decode(m['textures'][0]['source'].split(',')[1]))))
 masks={'north':[(1,1,0),(0,1,0),(0,0,0),(1,0,0)],'south':[(0,1,1),(1,1,1),(1,0,1),(0,0,1)],'west':[(0,1,0),(0,1,1),(0,0,1),(0,0,0)],'east':[(1,1,1),(1,1,0),(1,0,0),(1,0,1)],'up':[(0,1,0),(1,1,0),(1,1,1),(0,1,1)],'down':[(0,0,1),(1,0,1),(1,0,0),(0,0,0)]}
 faces=[]
 for e in m['elements']:
  for name,f in e['faces'].items():
   if e['type']=='mesh':
    p=np.array([e['vertices'][k] for k in f['vertices']])+e['origin'];uv=np.array([f['uv'][k] for k in f['vertices']])
   else:
    p=np.array(e['from'])+(np.array(e['to'])-e['from'])*np.array(masks[name]);u,v,U,V=f['uv'];uv=np.array([[u,v],[U,v],[U,V],[u,V]])
   for g in [e]+list(reversed(parents[e['uuid']])):
    origin=np.array(g.get('origin',[0,0,0]));p=(p-origin)@rotation(g.get('rotation',[0,0,0])).T+origin
   n=np.cross(p[1]-p[0],p[2]-p[0]);n/=max(1e-9,np.linalg.norm(n))
   if e['type']=='cube':n=-n
   shade=.63+.37*max(0,np.dot(n,np.array([.3,.8,-.5])))
   if any(s in e['name'] for s in ('cyan','lens','lamp','core')):shade=1
   for j in range(1,len(p)-1):faces.append((p[[0,j,j+1]],uv[[0,j,j+1]],shade,'glass' in e['name']))
 return faces,tex,m
def render(path,eye=(95,60,-120),size=(1000,690),pad=30,scene=None,bounds=None):
 fs,tex,m=scene if scene is not None else geometry(path);w,h=size;eye=np.array(eye,dtype=float);forward=-eye/np.linalg.norm(eye);right=np.cross(forward,[0,1,0]);right/=np.linalg.norm(right);up=np.cross(right,forward)
 basis=np.array([right,-up,forward]);points=np.array(bounds) if bounds is not None else np.concatenate([p for p,_,_,_ in fs]);projected=points@basis.T;lo=projected[:,:2].min(axis=0);hi=projected[:,:2].max(axis=0);scale=min((w-pad*2)/(hi[0]-lo[0]),(h-pad*2)/(hi[1]-lo[1]));center=(lo+hi)/2
 rgb=np.full((h,w,3),[23,37,49],dtype=float);depth=np.full((h,w),np.inf)
 # Rasterize opaque surfaces first, then blend transparent panes back to front.
 fs.sort(key=lambda f:(f[3],-np.mean(f[0]@forward)))
 for points,uv,shade,glass in fs:
  pp=points@basis.T;pp[:,:2]=(pp[:,:2]-center)*scale+[w/2,h/2]
  a,b,c=pp;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
  if abs(den)<1e-8:continue
  x0,y0=np.maximum([0,0],np.floor(pp[:,:2].min(axis=0)).astype(int));x1,y1=np.minimum([w-1,h-1],np.ceil(pp[:,:2].max(axis=0)).astype(int))
  if x1<x0 or y1<y0:continue
  yy,xx=np.mgrid[y0:y1+1,x0:x1+1];xx=xx+.5;yy=yy+.5
  aa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
  bb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;cc=1-aa-bb
  z=aa*a[2]+bb*b[2]+cc*c[2];d=depth[y0:y1+1,x0:x1+1];mask=(aa>=0)&(bb>=0)&(cc>=0)&(z<d+1e-6)
  coords=aa[:,:,None]*uv[0]+bb[:,:,None]*uv[1]+cc[:,:,None]*uv[2];coords=np.clip(coords.astype(int),0,tex.shape[0]-1);sample=tex[coords[:,:,1],coords[:,:,0]]
  alpha=sample[:,:,3:4]/255.;old=rgb[y0:y1+1,x0:x1+1];new=sample[:,:,:3]*shade*alpha+old*(1-alpha)
  old[mask]=new[mask]
  if not glass:d[mask]=z[mask]
 return Image.fromarray(np.clip(rgb,0,255).astype('uint8'))
def font(n):
 try:return ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf',n)
 except:return ImageFont.load_default()
def main():
 closed=R/'src/main/blockbench/mechanical_dolphin.bbmodel';opened=O/'mechanical_dolphin_open_review.bbmodel'
 board=Image.new('RGB',(1800,1200),(23,37,49));d=ImageDraw.Draw(board)
 d.text((40,25),'PANORAMA / MECHANICAL DOLPHIN',font=font(32),fill=(230,237,241))
 d.text((42,70),'Actual Blockbench geometry  /  blue armor · transparent canopy · prone cockpit',font=font(18),fill=(144,178,196))
 for path,eye,box,title in [(closed,(-100,64,-135),(0,125,900,605),'01 / CLOSED'),(opened,(-100,64,-135),(900,125,900,605),'02 / OPEN — STATIC REVIEW'),(closed,(-160,15,-2),(0,790,1190,330),'03 / SIDE PROFILE'),(closed,(0,180,0.001),(1210,790,565,350),'04 / TOP')]:
  x,y,w,h=box;board.paste(render(path,eye,(w,h),28),(x,y));d.text((x+30,y-25),title,font=font(20),fill=(221,231,238))
 d.text((40,1165),'Prototype idle / swim tail clips included. Door animation and gameplay integration remain separate.',font=font(17),fill=(147,173,187))
 board.save(O/'model-preview.png')
 render(closed,(-100,60,-140),(1600,1050),70).save(O/'closed.png')
 render(opened,(-100,60,-140),(1600,1050),70).save(O/'open.png')
 bounds=[[x,y,z] for x in (-8,8) for y in (3,14) for z in (-36.5,-22)]
 render(closed,(-120,48,-95),(1200,750),35,bounds=bounds).save(O/'eyes-detail.png')
 print('Rendered native model closed, open, side and top.')
if __name__=='__main__':main()
