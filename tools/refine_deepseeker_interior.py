"""Executed in the cabin author's namespace: retain layout, finish geometry and surface treatment."""
# Remove the visibly unfinished placeholders; replace them with constructed objects.
remove_names={'bunk_mattress','bunk_pillow','bunk_foot_pad','desk_cabinet','desk_worktop','tool_backboard','diagnostic_monitor','air_gauge','air_gauge_needle'}
def discard(e):
 n=e['name']
 return n in remove_names or n.startswith(('air_bottle_body_','air_bottle_shoulder_','air_bottle_base_','air_band_','interior_wall_rib_','interior_roof_rib_','secured_tool_','desk_drawer_'))
bad={e['uuid'] for e in new if discard(e)}
m['elements']=[e for e in m['elements'] if e['uuid'] not in bad];new[:]=[e for e in new if e['uuid'] not in bad]
def prune(ns):
 for n in ns[:]:
  if isinstance(n,str):
   if n in bad:ns.remove(n)
  else:prune(n['children'])
prune(m['outliner'])
# Thin fitted interior framing follows the octagonal shell, rather than floating posts.
for z in (-54,-14,16.8):
 for side in (-1,1):
  box(f'pressure_rib_upright_{side}_{z}',(side*29.7-1,32,z),(side*29.7+1,58,z+2.2),'dark',lights)
  box(f'pressure_rib_foot_{side}_{z}',(side*26-1.5,24,z-.2),(side*26+1.5,33,z+2.4),'steel',lights)
  if not (side==1 and z==-14):
   box(f'pressure_rib_slope_{side}_{z}',(side*22-10,65.8,z),(side*22+10,68,z+2.2),'dark',lights,rot=[0,0,-45*side])
  for y in (35,55):box(f'rib_clamp_{side}_{z}_{y}',(side*29.4-1.25,y,z-.15),(side*29.4+1.25,y+2.3,z+2.35),'steel',lights)
# Side window inner reveals, matching the exterior's layered black/steel frames.
for side in (-1,1):
 x=side*30.4
 for y in (40.5,54.6):box(f'window_inner_rail_{side}_{y}',(x-.8,y,-35),(x+.8,y+1.2,-16),'dark',lights)
 for z in (-35,-17.2):box(f'window_inner_jamb_{side}_{z}',(x-.8,41.7,z),(x+.8,54.6,z+1.2),'steel',lights)
# Stern equipment panel breaks up the empty bulkhead with layered ribs and service access.
box('stern_panel_frame',(-12.7,53,17.3),(11,70,18.6),'dark',storage)
box('stern_panel_inset',(-11.3,54.2,17),(9.6,68.8,17.3),'panel',storage)
box('stern_panel_label',(-8,63.5,16.8),(2,66.5,17),'cream',storage)
for i in range(4):box('stern_vent_'+str(i),(-8,55.5+i*1.4,16.6),(6,56.15+i*1.4,17),'rubber',storage)
# Bunk: padded body, stitched top, inset seams, bed frame and travel buckles.
box('mattress_lower',(14.7,30,-52.7),(27.7,32.5,-17.3),'dark',bunk)
box('mattress_cushion',(14.8,32,-52.2),(27.5,34.3,-17.8),'teal',bunk)
box('mattress_top',(15.4,34.3,-51.6),(27,34.7,-18.4),'canvas',bunk)
box('pillow_lower',(15.5,34.7,-24.1),(26.8,35.7,-18.8),'cream',bunk)
box('pillow_crown',(16.2,35.7,-23.6),(26.1,36.8,-19.3),'cream',bunk)
for x in (14.3,27.5):box('bed_frame_edge_'+str(x),(x,28.5,-53.7),(x+.65,30.6,-16.3),'dark',bunk)
for z in (-53.8,-17.1):box('bed_end_rail_'+str(z),(14.3,28.5,z),(28,30.6,z+.7),'steel',bunk)
# A single continuous belt and an inset buckle wrap the foot of the bunk.
for e in new:
 if e['name']=='bunk_safety_strap':e['from'][1]=34.72;e['to'][1]=35.05
box('bunk_belt_drop',(14.2,28,-48),(14.65,35.05,-46),'rubber',bunk)
box('bunk_buckle',(13.9,31,-48.4),(14.25,33,-45.6),'steel',bunk)
box('bunk_buckle_inset',(13.75,31.4,-47.9),(13.9,32.6,-46.1),'dark',bunk)
for z in (-51.5,-33.5):
 box('bed_drawer_inset_'+str(z),(13.99,22.8,z),(14.18,27.1,z+14.7),'drawer',bunk)
 for zz in (z+.4,z+13):box(f'bed_drawer_guard_{z}_{zz}',(13.7,22.8,zz),(14,27.1,zz+1.2),'steel',bunk)
# Workbench: separate frame, recessed drawer units, edge guards and ergonomic map tray.
box('bench_frame',(-23,21.3,-53),(-15,33.6,-17),'dark',desk)
for z in (-52,-40.5,-29):
 box('bench_drawer_casing_'+str(z),(-23.3,22,z),(-14.9,33,z+10.2),'ochre',desk)
 box('bench_drawer_face_'+str(z),(-14.89,23,z+.6),(-14.55,32.3,z+9.6),'drawer',desk)
 box('bench_recessed_pull_'+str(z),(-14.53,29.5,z+2.8),(-14.3,31,z+7.5),'rubber',desk)
 box('bench_pull_bar_'+str(z),(-14.28,30.2,z+3.2),(-13.95,30.9,z+7.1),'steel',desk)
box('worktop_lower',(-29,33.8,-54),(-14.7,35,-16),'steel',desk)
box('worktop_surface',(-28.7,35,-53.7),(-15,36,-16.3),'dark',desk)
box('bench_ochre_edge',(-14.99,34.1,-53.6),(-14.4,35.2,-16.4),'ochre',desk)
for z in (-54,-17.4):box('worktop_corner_'+str(z),(-29.2,34,z),(-14.4,36.35,z+1.4),'steel',desk)
for x in (-28.6,-15.6):box('chart_clamp_'+str(x),(x,36.25,-48),(x+.6,36.7,-45),'dark',desk)
# Recessed screen, bezel, fasteners and a separate mounting bracket.
box('monitor_mount',(-30,38,-46),(-28,43,-40),'steel',desk)
box('monitor_housing',(-29.5,40,-51.8),(-27.3,51,-35.6),'dark',desk)
box('monitor_bezel',(-27.25,40.6,-51.2),(-26.85,50.4,-36.2),'steel',desk)
box('monitor_display',(-26.8,41.2,-50.5),(-26.65,49.8,-36.9),'screen',desk)
for z in (-50.3,-48.8,-47.3):box('monitor_buttons_'+str(z),(-26.6,40.65,z),(-26.35,41.0,z+.7),'cyan',desk)
# Tool rack is relieved from the wall with recognizable screwdriver and open wrench.
box('tool_rack_frame',(-29.8,37,-31.5),(-28.5,49.6,-17),'steel',desk)
box('tool_rack_panel',(-28.45,37.7,-30.8),(-28.1,49,-17.7),'dark',desk)
box('screwdriver_grip',(-28,39,-22),(-26.8,43,-20.6),'red',desk)
box('screwdriver_shaft',(-27.7,43,-21.7),(-27.1,47.5,-21.1),'steel',desk)
box('wrench_shaft',(-28,39,-28),(-27,46.2,-26.8),'steel',desk)
for z in (-28.8,-26.3):box('wrench_jaw_'+str(z),(-28.1,45.3,z),(-26.9,48,z+1),'steel',desk)
box('wrench_head_bridge',(-28.1,45.3,-28.8),(-26.9,46.2,-25.3),'steel',desk)
box('bench_tasklight_arm',(-29,50,-31.4),(-26,51.2,-18),'steel',desk)
box('bench_tasklight_hood',(-29,51,-32),(-23,53,-17.5),'dark',desk)
box('bench_tasklight_lens',(-28.3,50.75,-31.2),(-23.7,51,-18.3),'lamp',desk)
# Small clipboard and canister are secured at the desk's aft edge.
box('clipboard',(-28,37,-17),(-27.4,43,-12.5),'paper',desk)
box('clipboard_clip',(-27.3,42,-16),(-27,43.2,-13.5),'steel',desk)
# True eight-sided bottles with tapered shoulders. Mesh caps remain planar quads.
def octagonal(name,cx,cz,rings,material,node):
 vertices={};faces={}
 for j,(y,r) in enumerate(rings):
  for k in range(8):
   a=math.radians(22.5+45*k);vertices[f'{j}_{k}']=[cx+r*math.cos(a),y,cz+r*math.sin(a)]
 for j in range(len(rings)-1):
  for k in range(8):
   keys=[f'{j+1}_{k}',f'{j+1}_{(k+1)%8}',f'{j}_{(k+1)%8}',f'{j}_{k}'];U=uv(material,3,rings[j+1][0]-rings[j][0]);u,v,a,b=U
   faces[f'{j}_{k}']={'vertices':keys,'uv':dict(zip(keys,[(u,b),(a,b),(a,v),(u,v)])),'texture':1}
 for j in (0,len(rings)-1):
  for k in (1,3,5):
   keys=[f'{j}_0',f'{j}_{k}',f'{j}_{k+1}',f'{j}_{k+2}'];keys=keys[::-1] if j!=0 else keys;u,v,a,b=uv(material,6,6)
   faces[f'cap_{j}_{k}']={'vertices':keys,'uv':dict(zip(keys,[(u,v),(a,v),(a,b),(u,b)])),'texture':1}
 e={'name':name,'type':'mesh','uuid':uid(name),'origin':[0,0,0],'rotation':[0,0,0],'vertices':vertices,'faces':faces,'export':True,'color':2}
 m['elements'].append(e);new.append(e);node['children'].append(e['uuid'])
for j,z in enumerate((-4,6)):
 octagonal('air_cylinder_'+str(j),-23,z,[(23,2.7),(25,3.6),(46.5,3.6),(49,2.5),(50,1.3)],'cream',air)
 for y,mat in [(24,'dark'),(28,'dark'),(42,'ochre')]:octagonal(f'air_retaining_band_{j}_{y}',-23,z,[(y,3.8),(y+1.7,3.8)],mat,air)
 box('air_rack_'+str(j),(-28,23,z-3),(-26.3,49,z+3),'dark',air)
 box('air_valve_'+str(j),(-24.5,51,z-1.5),(-21.5,52,z+1.5),'ochre',air)
 box('air_valve_handle_'+str(j),(-25,52,z-.5),(-21,52.8,z+.5),'red',air)
box('pressure_gauge_housing',(-22,52,-1.5),(-19.5,58,4.5),'dark',air)
box('pressure_gauge_face',(-19.45,52.4,-1.1),(-19.2,57.6,4.1),'gauge',air)
# Storage cases get edge guards, reinforced corners, lid seams, and inset handles.
for j,(x,y,z,w,h,d) in enumerate([(-8,21.3,10,16,12,7),(-8,34,10,15,10,7),(-7,45,11,14,7,6)]):
 for xx in (x,x+w-1.5):
  box(f'case_corner_{j}_{xx}',(xx,y,z-.35),(xx+1.5,y+h,z+.7),'steel',storage)
  box(f'case_bottom_corner_{j}_{xx}',(xx,y,z-.55),(xx+1.5,y+2,z+d+.25),'dark',storage)
 box('case_front_panel_'+str(j),(x+2,y+1.3,z-.2),(x+w-2,y+h-3,z-.05),'drawer' if j==1 else 'dark',storage)
 box('case_handle_recess_'+str(j),(x+w/2-2.5,y+h-4,z-.5),(x+w/2+2.5,y+h-2.6,z-.25),'rubber',storage)
 box('case_handle_grip_'+str(j),(x+w/2-2,y+h-3.4,z-.8),(x+w/2+2,y+h-2.8,z-.5),'steel',storage)
# Entry hardware reads as a load-bearing assembly: standoffs, serrated steps, wear guards.
for z in (-10,8):
 for y in (27,53,76):box(f'ladder_wall_mount_{z}_{y}',(25.8,y,z-.4),(28,y+2.4,z+2),'steel',ladder)
for j,y in enumerate(range(25,85,6)):
 box('ladder_tread_'+str(j),(22.85,y+1.51,-8.3),(25.15,y+1.7,7.9),'grate',ladder)
for e in new:
 if e['name'].startswith('hatch_trunk_end_'):
  e['faces']['north']={'uv':uv('panel'),'texture':1};e['faces']['south']={'uv':uv('panel'),'texture':1}
# A single inset ceiling spine and rib flanges tie lamps/pipes into the hull structure.
box('ceiling_spine',(-5,73.8,-55),(3,75.5,-17),'dark',lights)
for z in (-48,-25):
 box('ceiling_lamp_frame_'+str(z),(-3,72.8,z),(1,74,z+8),'steel',lights)
 box('ceiling_lamp_lens_'+str(z),(-2.6,72.5,z+.6),(.6,72.8,z+7.4),'lamp',lights)
# Do not repaint the exterior; interior roof panel down-faces get panel seams.
for e in new:
 if e['type']=='cube' and e['name'].startswith('roof_'):e['faces']['down']={'uv':uv('panel'),'texture':1}
# Replace the coarse stair-step hatch apron with two fitted sloped shell sections.
old_aprons={e['uuid'] for e in new if e['name'].startswith('hatch_trunk_end_')}
m['elements']=[e for e in m['elements'] if e['uuid'] not in old_aprons];new[:]=[e for e in new if e['uuid'] not in old_aprons]
bad=old_aprons;prune(m['outliner'])
def wedge(name,z0,z1,profile,node):
 vertices={f'{layer}_{i}':[x,y,z] for layer,z in enumerate((z0,z1)) for i,(x,y) in enumerate(profile)}
 keys=list(vertices);quads=[[f'0_{i}' for i in (3,2,1,0)],[f'1_{i}' for i in (0,1,2,3)]]
 for i in range(4):quads.append([f'0_{i}',f'0_{(i+1)%4}',f'1_{(i+1)%4}',f'1_{i}'])
 faces={}
 for i,q in enumerate(quads):
  u,v,a,b=uv('ochre',14,12);faces[str(i)]={'vertices':q,'uv':dict(zip(q,[(u,v),(a,v),(a,b),(u,b)])),'texture':1}
 e={'name':name,'type':'mesh','uuid':uid(name),'origin':[0,0,0],'rotation':[0,0,0],'vertices':vertices,'faces':faces,'export':True,'color':0}
 m['elements'].append(e);new.append(e);node['children'].append(e['uuid'])
for z in (-15,10):
 box('hatch_trunk_end_inboard_'+str(z),(4,75.6,z),(14.8,87,z+2),'ochre',shell)
 wedge('hatch_trunk_end_slope_'+str(z),z,z+2,[(14.8,75.6),(29,61.4),(29,87),(14.8,87)],shell)
 box('hatch_apron_edge_'+str(z),(10.6,67.7,z-.15),(30.7,69,z+2.15),'dark',shell,rot=[0,0,-45],origin=[20.65,68.35,z+1])
for j,z in enumerate((-4,6)):
 box('air_band_buckle_'+str(j),(-19.25,28,z-1),(-18.8,30,z+1),'steel',air)
 box('air_identification_'+str(j),(-19.58,36,z-1.4),(-19.45,39,z+1.4),'dark',air)
 box('air_identity_stripe_'+str(j),(-19.4,37,z-1),(-19.25,37.6,z+1),'cyan',air)
