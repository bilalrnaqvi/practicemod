#!/usr/bin/env python3
"""Build the approved cabin and synchronize the canonical runtime Blockbench source."""
import base64, copy, json, math, struct, uuid, zlib
import sys
if "--allow-legacy-overwrite" not in sys.argv:
    raise SystemExit("Legacy reconstruction would overwrite the current Deepseeker model. "
                     "Use tools/generate_deepseeker_geometry.py to export current art. "
                     "For intentional historical reconstruction in an isolated copy, pass --allow-legacy-overwrite.")
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/blockbench/deepseeker-expedition'
OUT.mkdir(parents=True,exist_ok=True)
source=json.loads((ROOT/'src/main/blockbench/archive/deepseeker_pre_expedition.bbmodel').read_text())
m=copy.deepcopy(source); groups={g['uuid']:g for g in m['groups']}; parents={}
def walk(nodes,chain):
 for n in nodes:
  if isinstance(n,str): parents[n]=chain
  else: walk(n['children'],chain+[groups[n['uuid']]['name']])
walk(m['outliner'],[])
def uid(s):return str(uuid.uuid5(uuid.NAMESPACE_URL,'deepseeker-expedition/'+s))
# Replace the old machinery partition, center hatch and redundant roof layers.
def remove(e):
 n=e['name'];p=parents[e['uuid']]
 return ('aft_machinery'in p or 'hatch'in p or n.startswith(('side_inset_','roof_panel','hatch_base','hatch_handle_post','boarding_paint','upper_rail','pressure_panel_0','seal_pressure_liner_0')) or ('ceiling_unit'in p and n.startswith('inner_pressure_liner') and not any(e.get('rotation',[0,0,0]))))
removed={e['uuid'] for e in m['elements'] if remove(e)}
m['elements']=[e for e in m['elements'] if e['uuid'] not in removed]
def clean(nodes):
 result=[]
 for n in nodes:
  if isinstance(n,str):
   if n not in removed:result.append(n)
  else:
   n['children']=clean(n['children'])
   if n['children']:result.append(n)
 return result
m['outliner']=clean(m['outliner'])
# Cut only the starboard upper slope over the entry bay. Local Z equals world Z.
cut_names={'pressure_panel_7','seal_pressure_liner_7','structural_band_13_7','structural_band_13_0','band_trim_11.2_7','band_trim_11.2_0'}
replacements={}; added=[]
for e in m['elements'][:]:
 if e['name'] in cut_names or (e['name']=='inner_pressure_liner' and e.get('rotation')==[0,0,315]):
  ids=[]
  for suffix,lo,hi in [('fore',e['from'][2],min(-13,e['to'][2])),('aft',max(12,e['from'][2]),e['to'][2])]:
   if hi>lo:
    c=copy.deepcopy(e);c['name']+='_'+suffix;c['uuid']=uid(c['name']);c['from'][2]=lo;c['to'][2]=hi;added.append(c);ids.append(c['uuid'])
  replacements[e['uuid']]=ids
m['elements']=[e for e in m['elements'] if e['uuid'] not in replacements]+added
def rewrite(nodes):
 out=[]
 for n in nodes:
  if isinstance(n,str):out.extend(replacements.get(n,[n]))
  else:n['children']=rewrite(n['children']);out.append(n)
 return out
m['outliner']=rewrite(m['outliner'])
# Move the roof mast to port so its socket cannot obstruct the offset hatch.
for e in m['elements']:
 if 'mast' in parents.get(e['uuid'],[]):
  for key in ('from','to','origin'):e[key][0]-=27
for g in m['groups']:
 if g['name']=='mast':g['origin'][0]-=27
from deepseeker_interior_materials import COLORS, SIZE, atlas, uv
colors=COLORS;materials=list(colors);sz=SIZE;png=atlas()
(OUT/'expedition-details.png').write_bytes(png)
t=copy.deepcopy(m['textures'][0]);t.update(name='expedition-details.png',width=sz,height=sz,uv_width=sz,uv_height=sz,id='1',uuid=uid('texture'),source='data:image/png;base64,'+base64.b64encode(png).decode(),path='',internal=True)
m['textures'].append(t)
rootnode=m['outliner'][0]
def group(name,parent=None,origin=None):
 g=copy.deepcopy(source['groups'][0]);g.update(name=name,uuid=uid('group/'+name),origin=origin or [0,0,0],rotation=[0,0,0],children=[],isOpen=True)
 m['groups'].append(g);n={'uuid':g['uuid'],'isOpen':True,'children':[]};(parent or rootnode)['children'].append(n);return n
cabin=group('EXPEDITION_INTERIOR');shell=group('Entry_roof_and_structure');deck=group('Cabin_deck',cabin);bunk=group('Starboard_bunk_and_storage',cabin);desk=group('Port_chart_repair_workstation',cabin);air=group('Port_aft_air_supply',cabin);storage=group('Aft_expedition_storage',cabin);ladder=group('Starboard_interior_ladder',cabin);lights=group('Cabin_lights_and_pipes',cabin);lid=group('Hatch_lid_rotate_X_to_open',origin=[15,87,12]);entry=group('Starboard_exterior_boarding')
new=[]
def box(name,a,b,mat='dark',node=cabin,rot=None,origin=None,faces=None):
 dims=[b[i]-a[i] for i in range(3)]
 pairs={'north':(0,1),'south':(0,1),'east':(2,1),'west':(2,1),'up':(0,2),'down':(0,2)}
 e={'name':name,'type':'cube','uuid':uid(name),'box_uv':False,'from':list(a),'to':list(b),'origin':origin or [(x+y)/2 for x,y in zip(a,b)],'autouv':0,'color':materials.index(mat)%8,'export':True,'faces':{f:{'uv':uv((faces or {}).get(f,mat),dims[i],dims[j]),'texture':1} for f,(i,j) in pairs.items()}}
 if rot:e['rotation']=rot
 m['elements'].append(e);node['children'].append(e['uuid']);new.append(e);return e
# Continuous floor, full headroom and open cockpit threshold.
box('cabin_floor',(-23,20.1,-58.5),(23,21.3,18),'dark',deck)
for z in range(-56,9,8):box('aisle_grate_'+str(z),(-13.8,21.31,z),(13.8,21.55,z+7.6),'grate',deck)
box('ladder_landing_grate',(14,21.31,-13),(25,21.55,9),'grate',deck)
for x in (-14.5,14):box('aisle_edge_'+str(x),(x,21.3,-56),(x+.5,21.65,-14),'ochre',deck)
# Roof repairs replace the former center opening, and form an offset hatch shaft.
box('roof_port',(-14.8,75.6,-58.5),(4,82.5,20.25),'ochre',shell)
box('roof_starboard_fore',(4,75.6,-58.5),(14.8,82.5,-13),'ochre',shell)
box('roof_starboard_aft',(4,75.6,12),(14.8,82.5,20.25),'ochre',shell)
# Raised surround connects the cut slope to roof, preserving a 20 x 22 clear opening.
box('hatch_trunk_outboard',(26,57,-13),(29,87,12),'ochre',shell)
box('hatch_trunk_inboard',(2,76,-13),(4,87,12),'dark',shell)
for z in (-15,10):
 for x in range(4,29,5):
  bottom=min(75.6,90.6-(x+5))
  box(f'hatch_trunk_end_{z}_{x}',(x,bottom,z),(x+5,87,z+2),'ochre',shell)
# Cover the outboard remainder of the removed sloping panel, without closing the shaft.
box('starboard_slope_patch',(29,58,-13),(35,62,12),'ochre',shell)
for z in (-15,12):box('hatch_coaming_end_'+str(z),(2,85,z),(29,88,z+2),'steel',shell)
for x in (2,26):box('hatch_coaming_side_'+str(x),(x,85,-13),(x+2,88,12),'steel',shell)
box('hatch_lid',(1,88,-16),(30,91,14),'ochre',lid)
box('hatch_lid_inner',(4,87.7,-12),(26,88,10),'dark',lid)
for z in (-10,8):box('hatch_lid_brace_'+str(z),(3,91,z),(28,92,z+1.8),'dark',lid)
box('hatch_handle',(21,94,-10),(30,96,-8),'steel',lid)
for x in (21,28):box('hatch_handle_post_'+str(x),(x,91,-10),(x+2,94,-8),'dark',lid)
# Bunk oriented along the hull. Mattress 2.25 blocks long; drawers set inside lower bevel.
box('bunk_plinth',(15,21.3,-53),(23,28,-16),'dark',bunk)
box('bunk_frame',(14,28,-54),(28,30,-16),'steel',bunk)
box('bunk_mattress',(14.5,30,-49),(28,34,-17),'teal',bunk)
box('bunk_pillow',(15,34,-24),(27.5,36,-18),'cream',bunk)
box('bunk_foot_pad',(14.5,30,-53),(28,34,-49),'cream',bunk)
for z in (-52,-34):
 box('bunk_drawer_'+str(z),(14.7,22,z),(23,27.7,z+16),'ochre',bunk)
 box('bunk_drawer_face_'+str(z),(14.2,22.3,z+.6),(14.7,27.4,z+15.4),'dark',bunk)
 box('bunk_drawer_latch_'+str(z),(13.8,24,z+7),(14.2,26,z+9),'steel',bunk)
for z in (-51,-19):box('bunk_hinge_'+str(z),(27,28,z),(29,31,z+2),'dark',bunk)
box('bunk_safety_strap',(14.4,34.05,-48),(28,34.35,-46),'dark',bunk)
# Integrated map and maintenance desk, shallow footprint, tools fixed to rear panel.
box('desk_cabinet',(-23,21.3,-53),(-15,34,-17),'ochre',desk)
box('desk_worktop',(-29,34,-54),(-14.7,36,-16),'dark',desk)
box('chart_inset',(-28.2,36.05,-51),(-15.5,36.2,-33),'map',desk)
box('repair_mat',(-28,36.05,-31),(-16,36.2,-18),'steel',desk)
for z in (-51,-40,-29):
 box('desk_drawer_'+str(z),(-15.15,23,z),(-14.85,32,z+9),'ochre',desk)
 box('desk_drawer_pull_'+str(z),(-14.8,29,z+2),(-14.15,30,z+7),'dark',desk)
box('tool_backboard',(-29.8,37,-31),(-28.8,49,-17),'dark',desk)
for z in (-28,-23):
 box('secured_tool_shaft_'+str(z),(-28.7,39,z),(-28.2,46,z+1),'steel',desk)
 box('secured_tool_head_'+str(z),(-28.8,45,z-.7),(-28.1,47,z+1.7),'steel',desk)
box('diagnostic_monitor',(-29.4,40,-51),(-27.4,50,-36),'dark',desk,faces={'east':'screen'})
box('repair_vise_base',(-22,36.2,-22),(-17,37.2,-18),'dark',desk)
for z in (-22,-19):box('repair_vise_jaw_'+str(z),(-22,37,z),(-17,39,z+1),'steel',desk)
# Two faceted vertical bottles recessed to port aft.
for j,z in enumerate((-4,6)):
 x=-23
 box('air_bottle_body_'+str(j),(x-3,25,z-3),(x+3,48,z+3),'cream',air)
 box('air_bottle_shoulder_'+str(j),(x-2.3,48,z-2.3),(x+2.3,49,z+2.3),'cream',air)
 box('air_bottle_neck_'+str(j),(x-1.4,48,z-1.4),(x+1.4,51,z+1.4),'steel',air)
 box('air_bottle_base_'+str(j),(x-3,23,z-3),(x+3,25,z+3),'dark',air)
 for y in (28,43):box(f'air_band_{j}_{y}',(x-3.4,y,z-3.4),(x+3.4,y+2,z+3.4),'ochre' if y==43 else 'dark',air)
box('air_manifold',(-24,51,-5),(-22,53,8),'steel',air)
box('air_gauge',(-22,51,-2),(-20,57,4),'dark',air,faces={'east':'cream'})
box('air_gauge_needle',(-19.9,52.5,0),(-19.6,55.5,.6),'red',air)
# Cases are attached to rear bulkhead, outside both aisle and ladder landing.
for j,(x,y,z,w,h,d,mat) in enumerate([(-8,21.3,10,16,12,7,'dark'),(-8,34,10,15,10,7,'ochre'),(-7,45,11,14,7,6,'olive')]):
 box('expedition_case_'+str(j),(x,y,z),(x+w,y+h-2.05,z+d),mat,storage)
 box('case_lid_'+str(j),(x-.3,y+h-2,z-.3),(x+w+.3,y+h,z+d+.3),'dark',storage)
 for xx in (x+2,x+w-3):box(f'case_latch_{j}_{xx}',(xx,y+h-4,z-.7),(xx+1.5,y+h-.5,z),'steel',storage)
box('emergency_kit',(-7,35,9.3),(-.5,41,10.4),'red',storage)
box('kit_cross_v',(-4.3,36,9.15),(-3.3,40,9.3),'cream',storage)
box('kit_cross_h',(-5.8,37.5,9.1),(-1.8,38.5,9.3),'cream',storage)
# Ladder faces starboard: rungs run along Z and climb directly into offset hatch.
for z in (-10,8):
 box('interior_ladder_rail_'+str(z),(24,21.3,z),(26,88,z+1.6),'dark',ladder)
 box('interior_ladder_grip_'+str(z),(23.5,70,z-.2),(26.5,89,z+1.8),'ochre',ladder)
for j,y in enumerate(range(25,85,6)):box('interior_ladder_rung_'+str(j),(23, y,-8.4),(25,y+1.5,8),'steel',ladder)
# Same-side exterior approach on the upper hull; thruster below is retained.
for z in (-10,8):box('exterior_ladder_rail_'+str(z),(40,56,z),(42,88,z+1.6),'dark',entry)
for j,y in enumerate(range(58,87,6)):box('exterior_ladder_rung_'+str(j),(40,y,-8.4),(43,y+1.5,8),'steel',entry)
for z in (-10,8):box('hatch_transfer_handhold_'+str(z),(27,90,z),(42,92,z+1.6),'ochre',entry)
box('hatch_transfer_sill',(28,85,-10),(42,87,10),'dark',entry)
# Structural frames remain above furniture and outside the walk corridor.
for z in (-55,-14,17):
 for side in (-1,1):
  box(f'interior_wall_rib_{z}_{side}',(side*29.7-.7,31,z),(side*29.7+.7,59,z+1.4),'dark',lights)
  if not (z==-14 and side==1):
   box(f'interior_roof_rib_{z}_{side}',(side*22-10,66,z),(side*22+10,67.5,z+1.4),'dark',lights,rot=[0,0,-45*side])
# Pipes and lights remain forward of hatch and off the center headroom corridor.
for x in (-13,12):
 box('ceiling_pipe_'+str(x),(x,70,-54),(x+1.5,72,-17),'steel',lights)
 for z in (-50,-30):box(f'pipe_clip_{x}_{z}',(x-.3,69.7,z),(x+1.8,72.3,z+1.5),'ochre',lights)
for x in (-19,17):
 for z in (-48,-21):
  box(f'light_housing_{x}_{z}',(x,64,z),(x+3,66,z+8),'dark',lights)
  box(f'light_lens_{x}_{z}',(x+.3,63.7,z+.6),(x+2.7,64,z+7.4),'lamp',lights)
# Layered modeling refinement shares the same authoring helpers and layout.
exec((ROOT/'tools/refine_deepseeker_interior.py').read_text())
exec((ROOT/'tools/clean_deepseeker_interior.py').read_text())
# Retain the empty authored camera marker used by server seating/export.
for g in source['groups']:
 if g['name']=='pilot_camera':
  m['outliner'].append(dict(g,children=[]))
# Remove orphan groups for a tidy editable outliner.
used=set()
def collect(ns):
 for n in ns:
  if isinstance(n,dict):used.add(n['uuid']);collect(n['children'])
collect(m['outliner']);m['groups']=[g for g in m['groups'] if g['uuid'] in used]
m['name']='Deepseeker — Expedition interior';m['model_identifier']='deepseeker_expedition'
path=ROOT/'src/main/blockbench/deepseeker_expedition.bbmodel';path.write_text(json.dumps(m,indent=2)+'\n')
(ROOT/'src/main/blockbench/deepseeker.bbmodel').write_text(path.read_text())
# Inspection companion: full geometry retained but exterior hidden; hatch opened.
cut=copy.deepcopy(m)
keep={e['uuid'] for e in new}
for e in cut['elements']:
 if e['uuid'] not in keep:e['visibility']=False
for g in cut['groups']:
 if g['uuid']==lid['uuid']:g['rotation']=[105,0,0]
# Hide roof structure only in cutaway; keep the entry coaming for hatch alignment.
for e in cut['elements']:
 if e['name'].startswith(('roof_','hatch_trunk_','starboard_slope_patch')):e['visibility']=False
cut['name']='Deepseeker expedition — interior inspection'
(OUT/'deepseeker-expedition-cutaway.bbmodel').write_text(json.dumps(cut,indent=2)+'\n')
(OUT/'deepseeker-expedition-refined.bbmodel').write_text(json.dumps(cut,indent=2)+'\n')
print(f'Wrote {path}; {len(new)} new elements, {len(m["elements"])} total elements.')
