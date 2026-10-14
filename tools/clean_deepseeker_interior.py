"""Final geometry-fit, simplification, and gauge animation pass in author namespace."""
# Count the replaced groups before simplification, including their actual quads.
def ids(node):
 r=set()
 for n in node['children']:
  if isinstance(n,str):r.add(n)
  else:r|=ids(n)
 return r
old_storage=ids(storage)
pipe_prefix=('ceiling_pipe_','pipe_clip_','ceiling_spine','ceiling_lamp_')
old_pipes={e['uuid'] for e in new if e['name'].startswith(pipe_prefix)}
def stats(selected):
 es=[e for e in m['elements'] if e['uuid'] in selected]
 return {'elements':len(es),'quads':sum(sum(f.get('texture') is not None for f in e['faces'].values()) for e in es)}
before={'storage':stats(old_storage),'pipes':stats(old_pipes)}
bad=old_storage|old_pipes|{e['uuid'] for e in new if e['name'].startswith(('pressure_rib_','rib_clamp_'))}
m['elements']=[e for e in m['elements'] if e['uuid'] not in bad];new[:]=[e for e in new if e['uuid'] not in bad];prune(m['outliner'])
# Rugged concept-matched cases: broad staggered silhouettes and substantial lids.
case_bindings=[]
def expedition_case(key,x,y,z,w,h,d,material):
 case=group('Storage_'+key,storage)
 body=group('Storage_'+key+'_body',case)
 pivot=[x+w/2,y+1.6,z-.3]
 door=group('Storage_'+key+'_front_access',case,origin=pivot)
 t=1.0
 box(key+'_bottom',(x,y,z),(x+w,y+t,z+d),material,body)
 box(key+'_front_sill',(x,y+t,z-.05),(x+w,y+1.6,z+.65),'dark',body)
 box(key+'_back',(x,y+t,z+d-t),(x+w,y+h-2.2,z+d),material,body)
 box(key+'_left',(x,y+t,z),(x+t,y+h-2.2,z+d-t),material,body)
 box(key+'_right',(x+w-t,y+t,z),(x+w,y+h-2.2,z+d-t),material,body)
 box(key+'_lid_gasket',(x-.25,y+h-2.2,z-.15),(x+w+.25,y+h-1.35,z+d+.1),'rubber',body)
 # Sloped lid corners are actual geometry, not a flat painted border.
 wedge(key+'_lid',z-.2,z+d+.1,[(x-.4,y+h-1.35),(x+w+.4,y+h-1.35),(x+w-.35,y+h+.35),(x+.35,y+h+.35)],body)
 lid_part=new[-1]
 for face in lid_part['faces'].values():
  u,v,U,V=uv('olive' if material=='olive' else 'dark',w,d)
  face['uv']=dict(zip(face['vertices'],[(u,v),(U,v),(U,V),(u,V)]))
 # Broad corner shoes wrap the lid edges, matching the reinforced concept cases.
 for j,xx in enumerate((x-.4,x+w-2.3)):
  for k,zz in enumerate((z-.25,z+d-2.3)):
   box(f'{key}_lid_corner_{j}_{k}',(xx,y+h-1.3,zz),(xx+2.7,y+h+.4,zz+2.45),'olive' if material=='olive' else 'case_shell',body)
 box(key+'_front',(x+.1,y+1.65,z-.62),(x+w-.1,y+h-2.25,z-.25),'case_green' if material=='olive' else 'case_shell',door)
 # Heavy corner guards meet the inset panel without overlapping its face.
 for xx in (x-.15,x+w-2.55):
  for yy in (y+1.7,y+h-4.35):
   box(f'{key}_guard_{xx}_{yy}',(xx,yy,z-1.25),(xx+2.7,yy+2.1,z-.67),'dark',door)
 if material!='olive':
  sx=x+w*.54
  box(key+'_ochre_band',(sx-1.4,y+1.7,z-.94),(sx+1.4,y+h-2.3,z-.65),'ochre',door)
  box(key+'_buckle_frame',(sx-1.7,y+h-5.5,z-1.65),(sx+1.7,y+h-2.5,z-.98),'ochre',door)
  box(key+'_buckle_inset',(sx-1.05,y+h-4.65,z-1.73),(sx+1.05,y+h-3.45,z-1.67),'dark',door)
  box(key+'_lid_strap_front',(sx-1.4,y+h-2.2,z-.98),(sx+1.4,y+h+.36,z-.3),'ochre',body)
  box(key+'_lid_band',(sx-1.4,y+h+.36,z+.4),(sx+1.4,y+h+.62,z+d-.3),'ochre',body)
 else:
  for xx in (x+2,x+w-3):box(key+'_latch_'+str(xx),(xx,y+h-4,z-1.15),(xx+1.4,y+h-2.4,z-.65),'steel',door)
 box(key+'_carry_handle',(x+2.4,y+h*.57,z-1.06),(x+5.8,y+h*.57+.7,z-.7),'steel',door)
 case_bindings.append({'id':key,'group_uuid':case['uuid'],'body_group_uuid':body['uuid'],
  'access_group_uuid':door['uuid'],'access_pivot':pivot,'opening_axis':'X',
  'opening_direction':'negative rotation: front panel folds outward/down',
  'bounds':[[x-.4,y,z-1.73],[x+w+.4,y+h+.62,z+d+.1]],'gameplay':'not implemented'})
expedition_case('large_supply_case',-11,21.55,6.7,22,11.8,10.6,'case_shell')
expedition_case('equipment_case',-1.7,34.3,7.4,12.6,10.2,9.9,'case_shell')
expedition_case('olive_field_case',-5,45.5,8,15.4,7.8,9.1,'olive')
# Low skids sit on the supporting lid instead of leaving visibly floating cases.
for key,xs,y0,y1,z0,z1 in [('equipment_case',(-.7,8.7),33.72,34.3,9,16),('olive_field_case',(0,7),44.87,45.5,10,16)]:
 node=next(n for n in storage['children'] if isinstance(n,dict) and n['uuid']==uid('group/Storage_'+key))
 body_node=next(n for n in node['children'] if isinstance(n,dict) and n['uuid']==uid('group/Storage_'+key+'_body'))
 for j,xx in enumerate(xs):box(key+'_skid_'+str(j),(xx,y0,z0),(xx+1.2,y1,z1),'rubber',body_node)
medical=group('Storage_medical_case',storage)
for xx in (-9.4,-4.4):box('medical_skid_'+str(xx),(xx,33.72,12),(xx+1.1,34.3,16),'rubber',medical)
box('medical_case_body',(-10.4,34.3,7.4),(-2.5,41.4,16.8),'red',medical)
box('medical_case_lid',(-10.6,41.4,7.3),(-2.3,42.4,16.9),'red',medical)
box('medical_case_front',(-10.2,34.6,7.1),(-2.7,41.1,7.35),'red',medical,faces={'north':'medical'})
box('medical_handle',(-8.5,42.8,12.5),(-4.3,43.5,13.5),'dark',medical)
for xx in (-8.5,-5):box('medical_handle_mount_'+str(xx),(xx,42.4,12.5),(xx+.7,42.8,13.5),'dark',medical)
case_bindings.append({'id':'medical_case','group_uuid':medical['uuid'],
 'bounds':[[-10.6,34.3,7.1],[-2.3,43.5,16.9]],'gameplay':'not implemented'})
(OUT/'storage-bindings.json').write_text(json.dumps({'cases':case_bindings,
 'note':'Separate concept-style cases; front access retained for later gameplay. No inventories implemented.'},indent=2)+'\n')
# Two uninterrupted compact conduits, no separate clips or layered floating spine.
for x in (-9,7):box('ceiling_conduit_'+str(x),(x,72,-54),(x+1.6,73.5,-18),'conduit',lights)
# Place uprights between furniture bays. One slope per side, no overlapping clamps/feet.
for z in (-57,13.5):
 for side in (-1,1):
  box(f'clean_rib_wall_{side}_{z}',(side*28.9-.65,37,z),(side*28.9+.65,55,z+1.2),'dark',lights)
  if not (side==1 and z==13.5):
   box(f'clean_rib_slope_{side}_{z}',(side*20.5-11.8,65.5-.6,z),(side*20.5+11.8,65.5+.6,z+1.2),'dark',lights,rot=[0,0,-45*side])
# Fit low cabinets to the octagonal lower shell. The outboard backs become shallow;
# visible door faces retain their design. Keep a small clearance from the liner.
fit_ids=ids(bunk)|ids(desk)
for e in new:
 if e['uuid'] not in fit_ids or e['type']!='cube':continue
 lo,hi=e['from'],e['to'];bound=min(29.2,lo[1]-2.3)
 if lo[0]>0:hi[0]=min(hi[0],bound)
 elif hi[0]<0:lo[0]=max(lo[0],-bound)
 # A pair of hinge cubes was originally sunk into the lower hull. Raise them to the frame.
 if hi[0]<=lo[0]:
  if lo[0]>0:hi[0]=lo[0]+1
  else:lo[0]=hi[0]-1
  lo[1]=max(lo[1],max(abs(lo[0]),abs(hi[0]))+2.35);hi[1]=max(hi[1],lo[1]+1.2)
 e['origin']=[(a+b)/2 for a,b in zip(lo,hi)]
# Move the bottles up clear of the lower hull, keeping gauge and manifold together.
for e in new:
 if e['uuid'] not in ids(air):continue
 if e['type']=='mesh':
  for v in e['vertices'].values():v[1]+=6
 else:
  for key in ('from','to','origin'):e[key][1]+=6
  if e['name'].startswith('air_rack_'):e['from'][0]=-26.5;e['to'][0]=-26.35
box('air_rack_shelf',(-26.4,28.7,-7.6),(-18.4,29,-.4),'dark',air)
box('air_rack_shelf_aft',(-26.4,28.7,2.4),(-18.4,29,9.6),'dark',air)
# Trim the lower faces at the hull boundary rather than allowing the deck to emerge through it.
for e in new:
 if e['type']=='cube' and e['name']=='cabin_floor':e['from'][0]=-17.7;e['to'][0]=17.7
 if e['name']=='ladder_landing_grate':e['to'][0]=18.9
# The original ladder was also partly buried at its foot; clip its bottom to the liner.
for e in new:
 if e['type']=='cube' and e['name'].startswith(('interior_ladder_rail','interior_ladder_rung','ladder_tread','ladder_wall_mount')):
  e['from'][1]=max(e['from'][1],e['to'][0]+2.3)
  if e['to'][1]<=e['from'][1]:e['to'][1]=e['from'][1]+1.2
# Dial texture has no painted needle. Needle rotates about X, normal to the gauge face.
pivot=[-19.0,61,1.5]
needle=group('Air_supply_gauge_needle',air,origin=pivot)
box('air_gauge_needle',(-19.1,60.85,1.36),(-18.94,62.8,1.64),'red',needle,rot=[-38,0,0],origin=pivot)
box('air_gauge_hub',(-18.92,60.7,1.2),(-18.7,61.3,1.8),'dark',air)
# Smooth 2.4-second +/-2-degree idle bobble, sampled sinusoid with matching loop ends.
keys=[]
for i in range(25):
 time=round(i*.1,5);angle=0.0 if i in (0,12,24) else round(2*math.sin(2*math.pi*i/24),6)
 keys.append({'channel':'rotation','data_points':[{'x':str(angle),'y':'0','z':'0'}],'uuid':uid('gauge-key-'+str(i)),'time':time,'color':-1,'interpolation':'linear'})
m['animations']=[{'uuid':uid('gauge-idle-animation'),'name':'animation.deepseeker.air_supply_idle','loop':'loop','override':False,'length':2.4,'snapping':40,'selected':True,'group_name':'','scope':0,'anim_time_update':'','blend_weight':'','start_delay':'','loop_delay':'','animators':{needle['uuid']:{'name':'Air_supply_gauge_needle','type':'bone','rotation_global':False,'quaternion_interpolation':False,'keyframes':keys}}}]
after={'storage':stats(ids(storage)),'pipes':stats({e['uuid'] for e in new if e['name'].startswith('ceiling_conduit_')})}
(OUT/'simplification.json').write_text(json.dumps({'before':before,'after':after},indent=2)+'\n')
