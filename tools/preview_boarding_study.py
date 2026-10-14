#!/usr/bin/env python3
"""Validate native two-segment IK reach and prepare textured review geometry.

Reads the editable .bbmodel, including manual keyframe edits. Animation sampling
supports the linear keys used by this study; rejects other interpolation rather
than showing a misleading preview. The two-bone solution uses the authored pole.
"""
import base64
import io
import json
import math
from pathlib import Path
import subprocess
import numpy as np
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/animation/boarding'
BUILD=ROOT/'build/boarding-study'


def rotation(v):
    x,y,z=np.radians(v);cx,cy,cz=np.cos([x,y,z]);sx,sy,sz=np.sin([x,y,z])
    return np.array([[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx],
                     [sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx],[-sy,cy*sx,cy*cx]])


def align(a,b):
    a=a/np.linalg.norm(a);b=b/np.linalg.norm(b);v=np.cross(a,b);c=np.dot(a,b)
    if c < -.999999:
        axis=np.cross(a,np.array([1,0,0]) if abs(a[0])<.8 else np.array([0,0,1]));axis/=np.linalg.norm(axis)
        return 2*np.outer(axis,axis)-np.eye(3)
    k=np.array([[0,-v[2],v[1]],[v[2],0,-v[0]],[-v[1],v[0],0]])
    return np.eye(3)+k+k@k/(1+c)


class Scene:
    def __init__(self,path):
        self.model=json.loads(path.read_text());self.groups={g['uuid']:g for g in self.model['groups']}
        self.elements={e['uuid']:e for e in self.model['elements']};self.parent={};self.order=[]
        def walk(nodes,parent=None):
            for n in nodes:
                key=n if isinstance(n,str) else n['uuid'];self.parent[key]=parent
                if not isinstance(n,str):self.order.append(key);walk(n['children'],key)
        walk(self.model['outliner'])
        self.animation=next(a for a in self.model['animations'] if a['name']=='animation.deepseeker.ladder_contact_study')
        self.duration=self.animation['length'];self.tracks={}
        for key,a in self.animation['animators'].items():
            for f in a['keyframes']:
                if f['interpolation']!='linear':raise ValueError('Preview requires baked linear keys; use Blockbench for other curves')
                self.tracks.setdefault((key,f['channel']),[]).append((f['time'],np.array([float(f['data_points'][0][axis]) for axis in 'xyz'])))
        for track in self.tracks.values():track.sort(key=lambda k:k[0])
        textures=[Image.open(io.BytesIO(base64.b64decode(t['source'].split(',')[1]))).convert('RGBA') for t in self.model['textures']]
        self.offsets=[];width=sum(i.width for i in textures);height=max(i.height for i in textures)
        self.atlas=Image.new('RGBA',(width,height));x=0
        for im in textures:self.offsets.append(x);self.atlas.paste(im,(x,0));x+=im.width
        self.texture_sizes=[i.size for i in textures]
        self.rig=set(key for key,g in self.groups.items() if g['name'].startswith('PLAYER_'))

    def sample(self,key,channel,t):
        track=self.tracks.get((key,channel))
        if not track:return np.ones(3) if channel=='scale' else np.zeros(3)
        if t<=track[0][0]:return track[0][1].copy()
        for (a,x),(b,y) in zip(track,track[1:]):
            if t<=b:return x+(y-x)*((t-a)/(b-a))
        return track[-1][1].copy()

    def pose(self,t):
        local={};world={};positions={};errors={};endpoints={}
        def update():
            for key in self.order:
                g=self.groups[key];p=self.parent[key];o=np.array(g['origin'],dtype=float)
                offset=self.sample(key,'position',t)
                if p:
                    positions[key]=positions[p]+world[p]@(o-np.array(self.groups[p]['origin'])+offset)
                    world[key]=world[p]@local[key]
                else:positions[key]=o+offset;world[key]=local[key]
        for key,g in self.groups.items():local[key]=rotation(np.array(g.get('rotation',[0,0,0]))+self.sample(key,'rotation',t))
        update()
        for e in self.elements.values():
            if e['type']!='null_object' or not e.get('ik_target'):continue
            end=e['ik_target'];lower=self.parent[end];upper=e['ik_source']
            assert self.parent[lower]==upper, 'Only two-segment chains are supported by this review renderer'
            start=positions[upper].copy();mid=positions[lower].copy();tip=positions[end].copy()
            target=np.array(e['position'])+self.sample(e['uuid'],'position',t)
            pole=self.elements[e['ik_pole']];pole=np.array(pole['position'])+self.sample(pole['uuid'],'position',t)
            a=np.linalg.norm(mid-start);b=np.linalg.norm(tip-mid);distance=np.linalg.norm(target-start)
            errors[e['name']]=max(0,distance-a-b,abs(a-b)-distance)
            length=np.clip(distance,abs(a-b)+.00001,a+b-.00001);axis=(target-start)/max(distance,.00001)
            bend=pole-start-axis*np.dot(pole-start,axis);bend/=max(np.linalg.norm(bend),.00001)
            along=(a*a-b*b+length*length)/(2*length)
            joint=start+axis*along+bend*math.sqrt(max(0,a*a-along*along));tip=start+axis*length
            saved_world=world[end].copy()
            p=self.parent[upper];parent_rotation=world[p] if p else np.eye(3)
            local[upper]=align(parent_rotation.T@(mid-start),parent_rotation.T@(joint-start))@local[upper]
            update()
            parent_rotation=world[upper]
            local[lower]=align(parent_rotation.T@(positions[end]-positions[lower]),parent_rotation.T@(tip-positions[lower]))@local[lower]
            update()
            if e.get('lock_ik_target_rotation'):
                local[end]=world[lower].T@saved_world;update()
            endpoints[e['name']]=(positions[end].copy(),target.copy())
        return world,positions,errors,endpoints

    def geometry(self,world,positions,rig):
        faces={'north':[(1,1,0),(0,1,0),(0,0,0),(1,0,0)],'south':[(0,1,1),(1,1,1),(1,0,1),(0,0,1)],
            'west':[(0,1,0),(0,1,1),(0,0,1),(0,0,0)],'east':[(1,1,1),(1,1,0),(1,0,0),(1,0,1)],
            'up':[(0,1,0),(1,1,0),(1,1,1),(0,1,1)],'down':[(0,0,1),(1,0,0),(1,0,1),(0,0,0)]}
        cubes=[];glass=[]
        for key,e in self.elements.items():
            p=self.parent[key]
            if e['type']!='cube' or (p in self.rig)!=rig or not e.get('visibility',True):continue
            lo=np.array(e['from']);hi=np.array(e['to']);origin=np.array(e.get('origin',[0,0,0]));rot=rotation(e.get('rotation',[0,0,0]))
            data=[]
            for side,indices in faces.items():
                face=e['faces'][side];texture=face.get('texture')
                if texture is None:continue
                u1,v1,u2,v2=face['uv'];uv=[(u2,v1),(u1,v1),(u1,v2),(u2,v2)]
                n=(face.get('rotation',0)//90)%4;uv=uv[-n:]+uv[:-n] if n else uv
                pts=[]
                for mask in indices:
                    point=origin+rot@(lo+(hi-lo)*mask-origin)
                    if p:point=positions[p]+world[p]@(point-np.array(self.groups[p]['origin']))
                    pts.append(point/16)
                normal=np.cross(pts[1]-pts[0],pts[2]-pts[0]);normal/=np.linalg.norm(normal)
                for point,(u,v) in zip(pts,uv):
                    data.extend([*point,(u+self.offsets[texture])/self.atlas.width,v/self.atlas.height,*normal])
            cubes.append(data);glass.append('glass' in e['name'].lower())
        return cubes,glass


def main():
    BUILD.mkdir(parents=True,exist_ok=True)
    scene=Scene(ROOT/'src/main/blockbench/deepseeker_boarding_study.bbmodel')
    scene.atlas.save(BUILD/'atlas.png')
    world,pos,_,_=scene.pose(0);hull,glass=scene.geometry(world,pos,False)
    maximum={};max_contact=0
    for frame in range(round(scene.duration*120)+1):
        _,_,errors,ends=scene.pose(frame/120)
        for name,error in errors.items():maximum[name]=max(maximum.get(name,0),error)
        for point,target in ends.values():max_contact=max(max_contact,float(np.linalg.norm(point-target)))
    report={'samples':round(scene.duration*120)+1,'unreachable_distance_pixels':maximum,
            'maximum_contact_error_pixels':max_contact,'runtime_integrated':False}
    (OUT/'rig-validation.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))
    if max_contact>.02:raise SystemExit('Unreachable contact: adjust staging before rendering')
    frames=[]
    for i in range(round(scene.duration*20)+1):
        t=i/20;world,pos,_,_=scene.pose(t);rig,_=scene.geometry(world,pos,True)
        frames.append({'time':t,'geometry':rig})
    (BUILD/'render-data.json').write_text(json.dumps({'hull':hull,'glass':glass,'frames':frames},separators=(',',':')))
    cp=':'.join([str(ROOT/'build/classes/java/main'),str(ROOT/'build/moddev/artifacts/neoforge-21.1.235-merged.jar'),*(ROOT/'build/moddev/clientLegacyClasspath.txt').read_text().splitlines()])
    java=Path('/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin')
    subprocess.run([str(java/'javac'),'-proc:none','-cp',cp,'-d',str(BUILD),str(ROOT/'tools/PreviewDeepseeker.java'),str(ROOT/'tools/RenderBoardingStudy.java')],check=True,cwd=ROOT)
    subprocess.run([str(java/'java'),'-Xmx1G','-Djava.awt.headless=true','-cp',cp+':'+str(BUILD),'com.bilal.practicemod.RenderBoardingStudy'],check=True,cwd=ROOT)
    images=[Image.open(p).convert('RGB') for p in sorted(BUILD.glob('frame-*.png'))]
    # A single shared palette avoids frame-to-frame colour flicker.
    sheet=Image.new('RGB',(images[0].width*4,images[0].height))
    for i,index in enumerate([0,30,60,len(images)-1]):sheet.paste(images[index],(i*images[0].width,0))
    palette=sheet.quantize(colors=160)
    animation=[im.quantize(palette=palette,dither=Image.Dither.NONE) for im in images]
    animation[0].save(OUT/'ladder-study.gif',save_all=True,append_images=animation[1:],duration=[50]*(len(images)-1)+[1200],loop=0,optimize=False,disposal=2)
    poster=Image.new('RGB',(images[0].width*2,images[0].height*2))
    for i,index in enumerate([0,30,60,len(images)-1]):poster.paste(images[index],((i%2)*images[0].width,(i//2)*images[0].height))
    poster.save(OUT/'ladder-study-poses.png')
    print('Rendered ladder-study.gif and ladder-study-poses.png')


if __name__=='__main__':main()
