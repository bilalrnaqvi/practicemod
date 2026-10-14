#!/usr/bin/env python3
"""Ray-audit the pressure shell, deliberately excluding interior props that can hide holes."""
import json, math
from pathlib import Path
m=json.loads((Path(__file__).resolve().parents[1]/'src/main/blockbench/deepseeker.bbmodel').read_text())
prefixes=('pressure_panel_','rear_shoulders_','stern_bulkhead','cockpit_mount_',
          'canopy_facet_','forward_glass','front_octagonal_rim_','side_port_','seal_')
def inverse(v,rotation):
    v=list(v)
    for axis in [2,1,0]:
        a,b=((1,2),(2,0),(0,1))[axis];c,s=math.cos(-rotation[axis]),math.sin(-rotation[axis])
        v[a],v[b]=c*v[a]-s*v[b],s*v[a]+c*v[b]
    return v
shell=[]
for e in m['elements']:
    if e.get('export',True) and e['name'].startswith(prefixes):
        shell.append((e['from'],e['to'],e['origin'],[math.radians(v) for v in e.get('rotation',[0,0,0])]))
def hit(eye,ray,box):
    lo,hi,pivot,rot=box
    o=inverse([eye[i]-pivot[i] for i in range(3)],rot);d=inverse(ray,rot)
    near,far=0,1000
    for i in range(3):
        a,b=lo[i]-pivot[i],hi[i]-pivot[i]
        if abs(d[i])<1e-10:
            if not a<=o[i]<=b:return False
        else:
            t0,t1=sorted(((a-o[i])/d[i],(b-o[i])/d[i]))
            near,far=max(near,t0),min(far,t1)
            if far<near:return False
    return far>0
failures=[]
pilot=tuple(next(g for g in m['groups'] if g['name']=='pilot_camera')['origin'])
scale=json.loads((Path(__file__).resolve().parents[1]/'src/main/blockbench/deepseeker_screen_bindings.json').read_text())['hull_scale_from_original']
binding=json.loads((Path(__file__).resolve().parents[1]/'src/main/blockbench/deepseeker_screen_bindings.json').read_text())
def compact(point):
    p=list(point)
    aft=binding.get('aft_compaction')
    if aft and p[2]>aft['start_model_units']:
        p[2]=aft['start_model_units']+(p[2]-aft['start_model_units'])*aft['scale']
    return tuple(p)
for eye in [pilot]+[compact(tuple(v*scale for v in p)) for p in [(0,15,-30),(0,15,25),(7,16,2),(-7,16,2)]]:
    missed=[]
    for i in range(4096):
        y=1-2*(i+.5)/4096;a=i*math.pi*(3-math.sqrt(5));r=math.sqrt(1-y*y)
        ray=(r*math.cos(a),y,r*math.sin(a))
        if not any(hit(eye,ray,box) for box in shell):missed.append(ray)
    print(f'{eye}: {4096-len(missed)}/4096 rays enclosed')
    failures.extend((eye,r) for r in missed)
if failures:
    print('Example leaks:',failures[:6]);raise SystemExit(1)
print('PASS: 20,480 shell-only rays enclosed; interior fixtures were not counted as hull.')
