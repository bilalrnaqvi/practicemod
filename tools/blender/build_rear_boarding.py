"""Revise the integrated Blender performance around the approved rear entry.

Preserves the integrated chair approach/swivel verbatim. Named, editable contact
controls and fixed-length limb solutions constrain the replacement entry.
"""
import bpy,json,math,re,sys
from pathlib import Path
from mathutils import Vector,Matrix,Euler
R=Path(__file__).resolve().parents[2]
O=R/'output/animation/boarding-runtime'; O.mkdir(exist_ok=True,parents=True)
bpy.ops.wm.open_mainfile(filepath=str(R/'src/main/blender/baselines/deepseeker_before_rear_entry.blend'),load_ui=False,use_scripts=False)
s=bpy.context.scene; xyz=lambda p:Vector((p[0],-p[2],p[1]))
root=bpy.data.objects['PLAYER — hull-space root'];hatch=bpy.data.objects['HATCH — hinge'];chair=bpy.data.objects['CHAIR — swivel']
names=['torso','torso_bend','head','rightArm','rightArm_bend','leftArm','leftArm_bend','rightLeg','rightLeg_bend','leftLeg','leftLeg_bend']
player={n:bpy.data.objects[n] for n in names}
tail=[]
for f in range(120,193):
 s.frame_set(f);tail.append((f+96,root.location.copy(),root.rotation_euler.copy(),{n:o.rotation_euler.copy() for n,o in player.items()},chair.rotation_euler.copy()))
for o in [root,hatch,chair]+list(player.values()):o.animation_data_clear()
for c in list(bpy.data.collections):
 if c.name not in ('player','capturing stuff'):
  for o in list(c.objects):bpy.data.objects.remove(o,do_unlink=True)
  bpy.data.collections.remove(c)
# Import the exact generated runtime geometry, including clipped shoulder meshes.
java=(R/'src/main/java/com/bilal/practicemod/DeepseekerGeometry.java').read_text()
def array(name):return re.search(r'\b'+name+r' = \{(.*?)\};',java,re.S).group(1)
labels=json.loads('['+array('NAMES')+']');parts=[int(v) for v in array('ENTRY_PARTS').split(',')]
glass=[v.strip()=='true' for v in array('GLASS').split(',')]
cubes=[[float(v.strip().rstrip('F')) for v in a.split(',') if v.strip()] for a in re.findall(r'private static float\[\] cube\d+\(\) \{ return new float\[\] \{(.*?)\};',java,re.S)]
model=json.loads((R/'src/main/blockbench/deepseeker.bbmodel').read_text());groups={g['uuid']:g for g in model['groups']};els={e['uuid']:e for e in model['elements']};outer=set()
def walk(nodes,chain):
 for n in nodes:
  if isinstance(n,str):
   if 'interior_root' not in chain and 'EXPEDITION_INTERIOR' not in chain:outer.add(els[n]['name'])
  else:walk(n['children'],chain+[groups[n['uuid']]['name']])
walk(model['outliner'],[])
hatch.location=xyz((19/16,89.4/16,-16.7/16));hatch.rotation_euler=(0,0,0);chair.rotation_euler=(0,0,0)
s.frame_set(0);bpy.context.view_layer.update()
tex=bpy.data.images.load(str(R/'src/main/resources/assets/practicemod/textures/entity/deepseeker.png'),check_existing=False);tex.pack()
mat=bpy.data.materials.new('Rear entry — runtime atlas');mat.use_nodes=True
node=mat.node_tree.nodes.new('ShaderNodeTexImage');node.image=tex;node.interpolation='Closest'
mat.node_tree.links.new(node.outputs['Color'],mat.node_tree.nodes.get('Principled BSDF').inputs['Base Color'])
col=bpy.data.collections.new('Rear entry — actual runtime geometry');s.collection.children.link(col)
for name,flat,part,isglass in zip(labels,cubes,parts,glass):
 mesh=bpy.data.meshes.new(name);mesh.from_pydata([xyz(flat[i:i+3]) for i in range(0,len(flat),8)],[],[tuple(range(i,i+4)) for i in range(0,len(flat)//8,4)])
 mesh.materials.append(mat);uv=mesh.uv_layers.new()
 for poly in mesh.polygons:
  for li in poly.loop_indices:
   vi=mesh.loops[li].vertex_index*8;uv.data[li].uv=(flat[vi+3],1-flat[vi+4])
 obj=bpy.data.objects.new(name,mesh);col.objects.link(obj)
 obj['review_shell']=name in outer or 'pressure_liner' in name or 'roof' in name.lower() or 'ceiling' in name.lower()
 obj['glass']=isglass;obj['entry_part']=part
 if part in (1,3):
  prop=hatch if part==1 else chair;obj.parent=prop;obj.matrix_parent_inverse=prop.matrix_world.inverted()
def rot(o,f,a):
 o.rotation_euler=[math.radians(v) for v in a];o.keyframe_insert(data_path='rotation_euler',frame=f)
def pose(f,p,heading,lean):
 root.location=xyz(p);root.keyframe_insert(data_path='location',frame=f);rot(root,f,(0,0,heading))
 rot(player['torso'],f,(lean,0,0));rot(player['torso_bend'],f,(0,0,0));rot(player['head'],f,(max(0,-lean*.45),0,0))
 for side in ('right','left'):
  rot(player[side+'Arm'],f,(-20 if side=='left' and f<=24 else 35,0,-8 if side=='right' else 8));rot(player[side+'Arm_bend'],f,(25 if side=='left' and f<=24 else 55,0,0))
  rot(player[side+'Leg'],f,(25,0,0));rot(player[side+'Leg_bend'],f,(-50,0,0))
# Anticipation, palm-supported side vault, load the rungs, descend, step off.
for p in [
 (0,(1.18,5.96,1.18),0,-25),(10,(1.18,5.96,1.18),0,-25),
 (24,(1.18,5.96,1.18),0,-25),(36,(1.18,5.75,1.03),0,-50),
 (46,(1.14,6.03,.78),-25,-35),(56,(1.07,5.65,.30),-65,-25),
 (66,(1.06,5.28,-.23),-90,-18),(76,(1.06,5.14,-.23),-90,-22),
 (84,(1.06,4.74,-.23),-90,-4),
 (96,(1.06,4.37,-.23),-90,-4),(108,(1.06,4.00,-.23),-90,-4),
 (120,(1.06,3.63,-.23),-90,-4),(132,(1.06,3.26,-.23),-90,-4),
 (144,(1.06,2.89,-.23),-90,-4),(156,(1.06,2.52,-.23),-90,-4),
 (168,(1.05,2.17,-.39),-90,0),(178,(.90,2.17,-.53),-65,0),
 (190,(.42,2.17,-.53),-15,-10),(194,(.20,2.04,-.64),-6,-12),(198,(0,2.02,-.80),0,-12),
 (207,(0,2.00,-1.46),0,-12),
]:pose(*p)
for f,loc,heading,channels,seat in tail:
 root.location=loc.copy();root.location.z+=.15*max(0,(232-f)/16);root.keyframe_insert(data_path='location',frame=f);root.rotation_euler=heading;root.keyframe_insert(data_path='rotation_euler',frame=f)
 for n,a in channels.items():player[n].rotation_euler=a;player[n].keyframe_insert(data_path='rotation_euler',frame=f)
 chair.rotation_euler=seat;chair.keyframe_insert(data_path='rotation_euler',frame=f)
# Remove inherited transient thigh twists, preserving root, chair and final pose.
for side in ('right','left'):
 for suffix in ('Leg','Leg_bend'):
  player[side+suffix].animation_data_clear()
 for f,thigh,knee in ([(216,28,42),(222,-22,25),(228,25,40),(234,65,80)] if side=='right' else [(216,-20,25),(222,30,45),(228,-10,28),(234,60,75)]):
  rot(player[side+'Leg'],f,(thigh,0,0));rot(player[side+'Leg_bend'],f,(-knee,0,0))
 for f in (242,288):
  for suffix in ('Leg','Leg_bend'):
   o=player[side+suffix];o.rotation_euler=tail[-1][3][side+suffix];o.keyframe_insert(data_path='rotation_euler',frame=f)
# Smooth the short inherited arm reach into the seat, retaining its end contacts.
for side in ('right','left'):
 for suffix in ('Arm','Arm_bend'):
  name=side+suffix;o=player[name]
  a=tail[228-216][3][name].to_quaternion();b=tail[244-216][3][name].to_quaternion();previous=tail[228-216][3][name]
  for f in range(229,244):
   w=(f-228)/16;w=w*w*(3-2*w)
   o.rotation_euler=a.slerp(b,w).to_euler('XYZ',previous);previous=o.rotation_euler.copy();o.keyframe_insert(data_path='rotation_euler',frame=f)
for f,a in [(0,0),(10,0),(23,-30),(34,-87),(38,-90),(110,-90),(135,-90),(158,0),(288,0)]:rot(hatch,f,(a,0,0))
for f,a in [(0,0),(165,0),(207,180)]:rot(chair,f,(0,0,a))
for o in [root,hatch,chair]+list(player.values()):
 for fc in o.animation_data.action.fcurves:
  for k in fc.keyframe_points:k.interpolation='BEZIER';k.handle_left_type=k.handle_right_type='AUTO_CLAMPED'
# Contact controls are retained as editable named world-space targets.
controls=bpy.data.collections.new('Rear entry — hand and foot contacts');s.collection.children.link(controls)
def control(name,keys):
 o=bpy.data.objects.new(name,None);controls.objects.link(o);o.empty_display_type='SPHERE';o.empty_display_size=.06
 for f,p in keys:o.location=xyz(p);o.keyframe_insert(data_path='location',frame=f)
 for fc in o.animation_data.action.fcurves:
  for k in fc.keyframe_points:k.interpolation='LINEAR'
 return o
# Feet hold their supports while the opposite foot moves. Lift keys clear the sill.
feet={
 'right':[(0,(1.39,5.30,1.40)),(36,(1.39,5.30,1.40)),(46,(1.29,5.64,.82)),(56,(1.30,5.22,.10)),(66,(1.30,4.70,-.05)),(72,(1.30,4.60,-.05)),(78,(1.30,4.40,-.05))],
 'left':[(0,(.98,5.30,1.40)),(40,(.98,5.30,1.40)),(54,(1.01,5.54,.55)),(64,(1.28,4.74,-.40)),(76,(1.30,4.56,-.40)),(84,(1.30,4.12,-.40))]}
for side in ('right','left'):
 z=-.05 if side=='right' else -.40
 feet[side]=[k for k in feet[side] if k[0]<84]
 feet[side]+=[(84,(1.30,4.12,z)),(156,(1.30,1.90,z))]
feet['right'] += [(162,(1.20,1.80,-.20)),(168,(1.08,1.53,-.28)),(180,(1.08,1.53,-.28)),(187,(.72,1.75,-.48)),(194,(.20,1.38,-.72)),(201,(.20,1.38,-.72)),(208,(.12,1.60,-1.32)),(214,(.117,1.36,-2.02))]
feet['left'] += [(160,(1.34,1.95,-.40)),(168,(1.00,1.70,-.56)),(174,(.92,1.53,-.66)),(183,(.92,1.53,-.66)),(190,(.20,1.60,-.78)),(198,(-.117,1.38,-1.00)),(203,(-.117,1.38,-1.00)),(211,(-.117,1.50,-1.82)),(215,(-.117,1.32,-2.10))]
fc={side:control(side+' foot — planted supports',sorted(dict(keys).items())) for side,keys in feet.items()}
hands={
 'right':[(0,(1.60,6.12,1.02)),(23,(1.60,6.12,1.02)),(30,(1.60,5.90,.90)),(36,(1.60,5.52,.70)),(44,(1.66,5.49,.46)),(58,(1.66,5.49,.46)),(70,(1.51,5.34,-.02)),(84,(1.51,5.34,-.02))],
 'left':[(0,(.83,5.50,.73)),(28,(.83,5.50,.73)),(40,(.76,5.49,.36)),(48,(.76,5.49,.36)),(56,(1.40,5.46,-.24)),(60,(1.49,5.42,-.43)),(86,(1.49,5.34,-.43))]}
for side in ('right','left'):
 z=.10 if side=='right' else -.62
 hands[side]=[k for k in hands[side] if k[0]<84]
 hands[side]+=[(84,(1.41,5.19,z)),(156,(1.41,2.97,z)),(165,(1.40,2.59,z))]
hc={side:control(side+' hand — rim and rungs',sorted(dict(keys).items())) for side,keys in hands.items()}
def basis(a,b):
 c=b-a*a.dot(b)
 if c.length<1e-5:c=a.cross(Vector((0,0,1)))
 c.normalize();return Matrix((a,c,a.cross(c))).transposed()
prev={};world_arm={};report=[]
def solve(side,kind,target,f):
 upper=player[side+kind];lower=player[side+kind+'_bend'];delta=target-upper.matrix_world.translation
 if kind=='Arm':
  a=.05859375 if side=='right' else -.05859375;b=.234375;l=.3515625
  cosine=(delta.length_squared-a*a-b*b-l*l)/(2*b*l);bend=math.acos(max(-1,min(1,cosine)))
  rest=Vector((a,l*math.sin(bend),-b-l*math.cos(bend)));restu=Vector((a,0,-b))
  # Elbows flare gently to either side while palms stay on fixed supports.
  pole=root.matrix_world.to_quaternion() @ Vector((.18 if side=='right' else -.18,-.35,-1))
 else:
  l=.3515625;bend=-2*math.acos(max(0,min(1,delta.length/(2*l))))
  rest=Vector((0,l*math.sin(bend),-l-l*math.cos(bend)));restu=Vector((0,0,-1))
  # Knees follow the pelvis forward plane; an upward world-space pole
  # can invert the hip as the torso leans over a planted foot.
  pole=(upper.parent.matrix_world @ upper.matrix_parent_inverse).to_quaternion() @ Vector((0,1,0))
 desired=(basis(delta.normalized(),pole) @ basis(rest.normalized(),restu).transposed()).to_quaternion()
 key=(side,kind)
 if kind=='Arm' and key in world_arm:
  transported=(world_arm[key] @ rest).normalized().rotation_difference(delta.normalized()) @ world_arm[key]
  desired=transported.slerp(desired,.12)
 world_arm[key]=desired.copy()
 parent=upper.parent.matrix_world @ upper.matrix_parent_inverse
 if kind=='Leg':
  # Solve in the pelvis sagittal plane without a pole singularity at a straight knee.
  local=parent.to_quaternion().inverted() @ delta
  radial=math.hypot(local.x,local.z)
  pitch=math.atan2(-radial,local.y)-math.atan2(rest.z,rest.y)
  pitch=(pitch+math.pi)%(2*math.pi)-math.pi
  yaw=math.atan2(-local.x,-local.z)
  desired=parent.to_quaternion() @ Euler((pitch,yaw,0),'XYZ').to_quaternion()
 upper.rotation_euler=(parent.to_quaternion().inverted() @ desired).to_euler('XYZ',prev.get(upper.name,upper.rotation_euler));prev[upper.name]=upper.rotation_euler.copy()
 lower.rotation_euler=(bend,0,0)
 for o in (upper,lower):o.keyframe_insert(data_path='rotation_euler',frame=f)
 bpy.context.view_layer.update();actual=lower.matrix_world @ Vector((0,0,-1.5))
 return dict(frame=f,part=side+kind,error=(actual-target).length,reach=delta.length)
# Cache FK before adding baked keys; blend only during genuine release spans.
free={}
for f in range(216):
 s.frame_set(f);free[f]={side+kind:player[side+kind+'_bend'].matrix_world @ Vector((0,0,-1.5)) for side in ('right','left') for kind in ('Arm','Leg')}
for f in range(216):
 s.frame_set(f)
 for side in ('right','left'):
  target=hc[side].location.copy();weight=1
  if side=='right' and f<=32:
   handle=hatch.matrix_world @ xyz((.16,(97-89.4)/16,(9.55+16.7)/16))
   release=max(0,min(1,(f-10)/16));weight_handle=1-release*release*(3-2*release)
   target=target.lerp(handle,weight_handle)
  if side=='left' and f<40:weight=max(0,min(1,(f-26)/14))
  if side=='left' and 40<=f<60:weight=max(0,1-(f-40)/4) if f<50 else min(1,(f-50)/10)
  # Release the rim after push-off; catch the rails as the body drops.
  if 38<f<66:
   release=max(0,1-(f-38)/6);catch=max(0,(f-54)/12)
   weight=min(weight,max(release,catch))
  if f>165:weight=max(0,(178-f)/13)
  if weight:report.append(solve(side,'Arm',free[f][side+'Arm'].lerp(target,weight),f))
  target=fc[side].location.copy();weight=min(1,(216-f)/4)
  wanted=free[f][side+'Leg'].lerp(target,weight)
  result=solve(side,'Leg',wanted,f)
  keys=sorted(dict(feet[side]).items())
  support=next((p[1]-.03 for (a,p),(b,q) in zip(keys,keys[1:]) if a<=f<=b and (Vector(p)-Vector(q)).length<.001),None)
  if support is None and f>=190:
   sole=min((player[side+'Leg_bend'].matrix_world @ Vector(p)).z for p in player[side+'Leg_bend'].bound_box)
   if sole<1.346875:support=1.346875
  if support is not None:
   for iteration in range(8):
    lower=player[side+'Leg_bend']
    sole=min((lower.matrix_world @ Vector(p)).z for p in lower.bound_box)
    if abs(support-sole)<.001 or result['error']>.005:break
    wanted.z+=support-sole
    result=solve(side,'Leg',wanted,f)
  report.append(result)
# Baked samples interpolate linearly; no overshoot between planted contacts.
for n in names:
 for curve in player[n].animation_data.action.fcurves:
  for k in curve.keyframe_points:
   if k.co.x<216:k.interpolation='LINEAR'
(O/'rear-contact-errors.json').write_text(json.dumps(report,indent=2))
print('CONTACT_MAX',max(p['error'] for p in report),'WORST',sorted(report,key=lambda p:p['error'])[-8:])
look=bpy.data.objects.new('Gameplay — first-person look',None);s.collection.objects.link(look)
# Eye stays on the head. Deliberate gaze keeps rungs/feet visible, with no camera cuts.
for f,pitch in [(0,35),(24,35),(36,48),(56,48),(76,42),(90,40),(156,40),(174,25),(194,8),(216,6),(238,7),(260,3),(288,0)]:
 look.rotation_euler=(math.radians(pitch),0,0);look.keyframe_insert(data_path='rotation_euler',frame=f)
for curve in look.animation_data.action.fcurves:
 for k in curve.keyframe_points:k.interpolation='BEZIER';k.handle_left_type=k.handle_right_type='AUTO_CLAMPED'
# Piecewise beat timing, not uniform acceleration of the rejected descent.
beats=[(0,0),(34,21),(76,48),(84,54),(168,81),(216,105),(288,150)]
def retime(f):
 for (a,x),(b,y) in zip(beats,beats[1:]):
  if f<=b:return x+(f-a)*(y-x)/(b-a)
 return 150
for obj in bpy.data.objects:
 if obj.animation_data and obj.animation_data.action:
  for curve in obj.animation_data.action.fcurves:
   for key in curve.keyframe_points:
    key.co.x=retime(key.co.x)
    key.handle_left.x=retime(key.handle_left.x)
    key.handle_right.x=retime(key.handle_right.x)
   curve.update()
s.render.fps=30;s.frame_start=0;s.frame_end=150

s.timeline_markers.clear()
for name,f in [('Supported start',0),('Lift hatch',10),('Palm support / vault',36),('Load ladder',76),('Rail slide',90),('Landing',168),('Cabin walk',190),('Preserved approach / swivel',216),('Controls',288)]:s.timeline_markers.new(name,frame=round(retime(f)))
s.render.engine='BLENDER_WORKBENCH';s.render.resolution_x=900;s.render.resolution_y=700;s.render.resolution_percentage=100
s.display.shading.light='STUDIO';s.display.shading.color_type='TEXTURE';s.display.shading.show_shadows=True;s.display.shading.show_cavity=True;s.display.shading.background_type='VIEWPORT';s.display.shading.background_color=(.055,.075,.10)
s.render.image_settings.file_format='PNG'
notes=bpy.data.texts.new('REAR ENTRY — review and gameplay distinction');notes.write('Rear entry replaces frames before the retained integrated approach/swivel. Runtime geometry is complete. Named support controls bake fixed-length limbs; inspect contact report and moving views. Outside cutaway is a review tool only. First-person uses the exported eye track in the full hull.')
s.frame_set(0);bpy.ops.wm.save_as_mainfile(filepath=str(R/'src/main/blender/deepseeker_boarding_runtime.blend'))
exec(compile((R/'tools/blender/export_boarding_runtime.py').read_text(),'export_boarding_runtime.py','exec'))
