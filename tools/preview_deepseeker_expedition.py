#!/usr/bin/env python3
"""Bake the staged Blockbench revision for the local software preview, with no runtime export."""
import base64,json,math,struct,sys
from pathlib import Path
R=Path(__file__).resolve().parents[1];O=R/'output/blockbench/deepseeker-expedition'
source=R/'src/main/blockbench/deepseeker_expedition.bbmodel'
if len(sys.argv)>1: source=Path(sys.argv[1])
if len(sys.argv)>2: O=Path(sys.argv[2])
O.mkdir(parents=True,exist_ok=True)
m=json.loads(source.read_text()); gs={g['uuid']:g for g in m['groups']};ps={}
def walk(ns,chain):
 for n in ns:
  if isinstance(n,str):ps[n]=chain
  else:walk(n['children'],chain+[gs[n['uuid']]])
walk(m['outliner'],[])
for i,t in enumerate(m['textures']):(O/f'preview-texture-{i}.png').write_bytes(base64.b64decode(t['source'].split(',')[1]))
faces={'north':[(1,1,0),(0,1,0),(0,0,0),(1,0,0)],'south':[(0,1,1),(1,1,1),(1,0,1),(0,0,1)],'west':[(0,1,0),(0,1,1),(0,0,1),(0,0,0)],'east':[(1,1,1),(1,1,0),(1,0,0),(1,0,1)],'up':[(0,1,0),(1,1,0),(1,1,1),(0,1,1)],'down':[(0,0,1),(1,0,1),(1,0,0),(0,0,0)]}
def rotate(p,e):
 o=e.get('origin',[0,0,0]);p=[p[i]-o[i] for i in range(3)]
 for axis,deg in enumerate(e.get('rotation',[0,0,0])):
  a,b=((1,2),(2,0),(0,1))[axis];c,s=math.cos(math.radians(deg)),math.sin(math.radians(deg));p[a],p[b]=c*p[a]-s*p[b],s*p[a]+c*p[b]
 return [p[i]+o[i] for i in range(3)]
gauge_anim=next(a for a in m.get('animations',[]) if a['name']=='animation.deepseeker.air_supply_idle')
gauge_keys=next(iter(gauge_anim['animators'].values()))['keyframes']
for mode in ['full','interior','plan']+[f'gauge{i:02d}' for i in range(24)]:
 records=[]
 for e in m['elements']:
  names=[g['name'] for g in ps[e['uuid']]]
  review_groups=['EXPEDITION_INTERIOR','Hatch_lid_rotate_X_to_open','Starboard_exterior_boarding']
  if m.get('practicemod',{}).get('entry_redesign'):review_groups.append('hatch')
  entry_sill=m.get('practicemod',{}).get('entry_redesign') and e['name'].startswith('hatch_base_')
  if mode in ('interior','plan') and not (any(n in names for n in review_groups) or entry_sill):continue
  if mode=='plan' and ('EXPEDITION_INTERIOR' not in names or 'Cabin_lights_and_pipes' in names):continue
  if mode.startswith('gauge') and 'Port_aft_air_supply' not in names:continue
  data=[]
  for f,face in e['faces'].items():
   tex=face.get('texture')
   if tex is None:continue
   if e['type']=='mesh':
    keys=face['vertices'];raw=[[e['vertices'][k][i]+e.get('origin',[0,0,0])[i] for i in range(3)] for k in keys];uv=[face['uv'][k] for k in keys]
   else:
    raw=[[e['to'][i] if c[i] else e['from'][i] for i in range(3)] for c in faces[f]];u,v,U,V=face['uv'];uv=[(u,v),(U,v),(U,V),(u,V)];s=face.get('rotation',0)//90;uv=uv[s:]+uv[:s]
   points=[]
   for p in raw:
    p=rotate(p,e)
    for g in reversed(ps[e['uuid']]):
     if g['name']=='Hatch_lid_rotate_X_to_open':g=dict(g,rotation=[float(sys.argv[3]) if len(sys.argv)>3 else (0 if 'practicemod' in m else 105),0,0])
     if mode.startswith('gauge') and g['name']=='Air_supply_gauge_needle':g=dict(g,rotation=[float(gauge_keys[int(mode[5:])]['data_points'][0]['x']),0,0])
     p=rotate(p,g)
    points.append([v/16 for v in p])
   if e['type']=='cube':points.reverse();uv.reverse()
   a=[points[1][i]-points[0][i] for i in range(3)];b=[points[2][i]-points[0][i] for i in range(3)];n=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];l=math.sqrt(sum(x*x for x in n));n=[x/l for x in n]
   for p,(u,v) in zip(points,uv):data+=p+([(u+2048)/4096,v/2048] if tex==1 else [u/4096,v/2048])+n
  records.append((data,'cockpit_glass'in names))
 with (O/f'{mode}.bin').open('wb') as f:
  f.write(struct.pack('>i',len(records)))
  for data,glass in records:f.write(struct.pack('>?i',glass,len(data)));f.write(struct.pack('>'+str(len(data))+'f',*data))
print('Baked preview geometry from staged model.')
