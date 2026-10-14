"""Clip a convex Blockbench cuboid around an axis-aligned opening, preserving UVs."""
import copy
import math
import uuid

CORNERS={'north':[(1,1,0),(0,1,0),(0,0,0),(1,0,0)],'south':[(0,1,1),(1,1,1),(1,0,1),(0,0,1)],
         'west':[(0,1,0),(0,1,1),(0,0,1),(0,0,0)],'east':[(1,1,1),(1,1,0),(1,0,0),(1,0,1)],
         'up':[(0,1,0),(1,1,0),(1,1,1),(0,1,1)],'down':[(0,0,1),(1,0,1),(1,0,0),(0,0,0)]}

def rotate(p,e):
    o=e.get('origin',[0,0,0]);p=[p[i]-o[i] for i in range(3)]
    for axis,deg in enumerate(e.get('rotation',[0,0,0])):
        a,b=((1,2),(2,0),(0,1))[axis];c=math.cos(math.radians(deg));s=math.sin(math.radians(deg))
        p[a],p[b]=c*p[a]-s*p[b],s*p[a]+c*p[b]
    return [p[i]+o[i] for i in range(3)]

def polygons(e):
    result=[]
    for name,face in e['faces'].items():
        if face.get('texture') is None:continue
        u,v,U,V=face['uv'];uv=[(u,v),(U,v),(U,V),(u,V)]
        shift=face.get('rotation',0)//90;uv=uv[shift:]+uv[:shift]
        points=[rotate([e['to'][i] if c[i] else e['from'][i] for i in range(3)],e)+list(t) for c,t in zip(CORNERS[name],uv)]
        result.append((list(reversed(points)),face['texture']))
    return result

def clip(polys,axis,value,keep_less):
    output=[];cuts=[];sign=1 if keep_less else -1
    for points,tex in polys:
        new=[]
        for a,b in zip(points,points[1:]+points[:1]):
            da=(a[axis]-value)*sign;db=(b[axis]-value)*sign
            if da<=1e-7:new.append(a)
            if (da<-1e-7 and db>1e-7) or (da>1e-7 and db<-1e-7):
                t=da/(da-db);p=[a[i]+t*(b[i]-a[i]) for i in range(5)]
                new.append(p);cuts.append(p[:3])
        if len(new)>=3:output.append((new,tex))
    unique={tuple(round(v,7) for v in p):p for p in cuts}
    if len(unique)>=3:
        pts=list(unique.values());center=[sum(p[i] for p in pts)/len(pts) for i in range(3)]
        a,b=((1,2),(2,0),(0,1))[axis]
        pts.sort(key=lambda p:math.atan2(p[b]-center[b],p[a]-center[a]),reverse=not keep_less)
        # Cross-section material comes from the adjacent face; caps remain inside the collar.
        ref,tex=polys[0];u0,v0=ref[0][3:]
        output.append(([p+[u0+(p[a]-center[a])*.5,v0+(p[b]-center[b])*.5] for p in pts],tex))
    return output

def mesh(name,polys):
    vertices={};faces={}
    for points,tex in polys:
        clean=[]
        for p in points:
            if not clean or sum((p[i]-clean[-1][i])**2 for i in range(3))>1e-12:clean.append(p)
        if len(clean)>1 and sum((clean[0][i]-clean[-1][i])**2 for i in range(3))<1e-12:clean.pop()
        if len(clean)<3:continue
        chunks=[clean] if len(clean)==4 else [[clean[0],clean[i],clean[i+1]] for i in range(1,len(clean)-1)]
        for chunk in chunks:
            a=[chunk[1][i]-chunk[0][i] for i in range(3)];b=[chunk[2][i]-chunk[0][i] for i in range(3)]
            n=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
            if sum(x*x for x in n)<1e-12:continue
            if len(chunk)==3:chunk=chunk+[[.5*(chunk[2][i]+chunk[0][i]) for i in range(5)]]
            keys=[];uv={}
            for p in chunk:
                key='v'+str(len(vertices));vertices[key]=[round(v,7) for v in p[:3]];keys.append(key);uv[key]=[round(v,5) for v in p[3:]]
            faces['f'+str(len(faces))]={'vertices':keys,'uv':uv,'texture':tex}
    return {'name':name,'type':'mesh','uuid':str(uuid.uuid5(uuid.NAMESPACE_URL,'rear-entry/'+name)),
            'origin':[0,0,0],'rotation':[0,0,0],'vertices':vertices,'faces':faces,'export':True}

def subtract_box(e,lo,hi):
    original=polygons(e);points=[p for ps,_ in original for p in ps]
    if any(max(p[i] for p in points)<=lo[i] or min(p[i] for p in points)>=hi[i] for i in range(3)):return [e]
    remaining=original;pieces=[]
    for axis in range(3):
        for bound,less in [(lo[axis],True),(hi[axis],False)]:
            outside=clip(remaining,axis,bound,less)
            if outside:pieces.append(outside)
            remaining=clip(remaining,axis,bound,not less)
            if not remaining:break
        if not remaining:break
    result=[mesh(e['name']+'_entry_cut_'+str(i),p) for i,p in enumerate(pieces)]
    return [e for e in result if e['faces']]
