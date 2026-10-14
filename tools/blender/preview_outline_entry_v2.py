"""Bounded five-second outside/cutaway draft; preserves the previous animation source."""
import bpy, json, math, re, sys
from pathlib import Path
from mathutils import Vector, Matrix
R=Path(__file__).resolve().parents[2]
O=R/'output/animation/outline-entry-v2';O.mkdir(parents=True,exist_ok=True)
bpy.ops.wm.open_mainfile(filepath=str(R/'src/main/blender/deepseeker_entry.blend'),load_ui=False,use_scripts=False)
s=bpy.context.scene
xyz=lambda p:Vector((p[0],-p[2],p[1]))
root=bpy.data.objects['PLAYER — hull-space root']
hatch=bpy.data.objects['HATCH — hinge'];chair=bpy.data.objects['CHAIR — swivel']
names=['torso_bend','head','rightArm','rightArm_bend','leftArm','leftArm_bend','rightLeg','rightLeg_bend','leftLeg','leftLeg_bend']
player={n:bpy.data.objects[n] for n in names}
# Reuse the existing interior performance, sampled before replacing curves.
interior=[]
for f in range(63,145):
 s.frame_set(f)
 interior.append((f,root.location.copy(),root.rotation_euler.copy(),{n:o.rotation_euler.copy() for n,o in player.items()},chair.rotation_euler.copy()))
for o in [root,hatch,chair]+list(player.values()):o.animation_data_clear()
for colname in ['Hull — hide for interior review','Interior — original runtime mesh','Contact controls — Blender IK']:
 col=bpy.data.collections.get(colname)
 if col:
  for o in list(col.objects):bpy.data.objects.remove(o,do_unlink=True)
  bpy.data.collections.remove(col)
# Read the exported runtime mesh, avoiding any older model in the Blender file.
java=(R/'src/main/java/com/bilal/practicemod/DeepseekerGeometry.java').read_text()
cubes=[[float(v.strip().rstrip('F')) for v in a.split(',') if v.strip()] for a in re.findall(r'private static float\[\] cube\d+\(\) \{ return new float\[\] \{(.*?)\};',java,re.S)]
def array(name):return re.search(r'\b'+name+r' = \{(.*?)\};',java,re.S).group(1)
labels=json.loads('['+array('NAMES')+']')
parts=[int(v) for v in array('ENTRY_PARTS').split(',')]
glass=[v.strip()=='true' for v in array('GLASS').split(',')]
assert len(cubes)==len(labels)==len(parts)==len(glass)
model=json.loads((R/'src/main/blockbench/deepseeker.bbmodel').read_text())
groups={g['uuid']:g for g in model['groups']};outer_names=set()
def walk(ns,chain):
 for n in ns:
  if isinstance(n,str):
   if not any(x in chain for x in ('interior_root','EXPEDITION_INTERIOR')):
    outer_names.add(next(e['name'] for e in model['elements'] if e['uuid']==n))
  else:walk(n['children'],chain+[groups[n['uuid']]['name']])
walk(model['outliner'],[])
hatch.location=xyz([0,93/16,-14.85/16]);hatch.rotation_euler=(0,0,0)
s.frame_set(0);bpy.context.view_layer.update()
tex=bpy.data.images.load(str(R/'src/main/resources/assets/practicemod/textures/entity/deepseeker.png'),check_existing=False);tex.pack()
mat=bpy.data.materials.new('Restored vehicle pixels');mat.use_nodes=True
node=mat.node_tree.nodes.new('ShaderNodeTexImage');node.image=tex;node.interpolation='Closest'
mat.node_tree.links.new(node.outputs['Color'],mat.node_tree.nodes.get('Principled BSDF').inputs['Base Color'])
col=bpy.data.collections.new('Restored vehicle');s.collection.children.link(col)
for name,flat,part,isglass in zip(labels,cubes,parts,glass):
 mesh=bpy.data.meshes.new(name);mesh.from_pydata([xyz(flat[i:i+3]) for i in range(0,len(flat),8)],[],[tuple(range(i,i+4)) for i in range(0,len(flat)//8,4)])
 mesh.materials.append(mat);uv=mesh.uv_layers.new()
 for poly in mesh.polygons:
  for li in poly.loop_indices:
   vi=mesh.loops[li].vertex_index*8;uv.data[li].uv=(flat[vi+3],1-flat[vi+4])
 obj=bpy.data.objects.new(name,mesh);col.objects.link(obj)
 if part in (1,3):
  prop=hatch if part==1 else chair;obj.parent=prop;obj.matrix_parent_inverse=prop.matrix_world.inverted()
 if (name in outer_names or ('pressure_liner' in name) or ('roof' in name.lower()) or ('ceiling' in name.lower())) and part!=1:
  for f,hidden in [(0,False),(65,False),(66,True),(150,True)]:
   obj.hide_render=hidden;obj.keyframe_insert(data_path='hide_render',frame=f)
 # Canopy is omitted in this workbench draft so the interior remains readable.
 if isglass:obj.hide_render=True;obj.animation_data_clear()
def rot(o,f,a):
 o.rotation_euler=[math.radians(v) for v in a];o.keyframe_insert(data_path='rotation_euler',frame=f)
def pose(f,hip,heading,lean,arms,legs):
 root.location=xyz(hip);root.keyframe_insert(data_path='location',frame=f);rot(root,f,(0,0,heading))
 rot(player['torso_bend'],f,(lean,0,0));rot(player['head'],f,(12,0,0))
 for side,(shoulder,elbow),(thigh,knee) in zip(('right','left'),arms,legs):
  rot(player[side+'Arm'],f,(shoulder,0,-8 if side=='right' else 8));rot(player[side+'Arm_bend'],f,(elbow,0,0))
  rot(player[side+'Leg'],f,(thigh,0,0));rot(player[side+'Leg_bend'],f,(-knee,0,0))
# Approach at the upper painted guide; climb, lift, turn, lower and descend.
poses=[
 (0,(2.55,3.95,-2.50),90,-30,((145,25),(125,40)),((55,95),(30,75))),
 (9,(2.20,4.40,-2.23),90,-58,((100,65),(115,50)),((85,115),(60,100))),
 (18,(1.80,4.95,-1.98),90,-72,((75,70),(90,55)),((95,120),(75,115))),
 (27,(1.05,5.75,-2.10),90,-65,((75,65),(55,60)),((85,110),(75,110))),
 (36,(1.00,6.00,-1.75),90,-50,((125,25),(65,55)),((70,100),(65,95))),
 (45,(.85,6.35,-1.00),90,-35,((130,25),(105,40)),((60,85),(45,75))),
 (54,(0,5.95,-1.68),180,-8,((150,20),(140,30)),((25,45),(50,70))),
 (63,(0,4.45,-1.68),145,-5,((140,30),(150,20)),((45,65),(10,25))),
 (72,(.95,3.50,-.15),-90,-5,((150,20),(130,40)),((10,25),(45,65))),
 (81,(.95,2.55,-.05),-90,-10,((105,50),(130,30)),((35,55),(10,25))),
 (87,(.60,1.90,-1.05),-25,-14,((40,60),(-20,40)),((40,60),(-15,30))),
]
for p in poses:pose(*p)
# Reuse the original seat and swivel, shortening the walk for the center hatch.
for f,loc,heading,channels,seat in interior:
 if f<81:continue
 nf=round(90+(f-81)*60/63)
 root.location=loc;root.keyframe_insert(data_path='location',frame=nf)
 root.rotation_euler=heading;root.keyframe_insert(data_path='rotation_euler',frame=nf)
 for n,a in channels.items():player[n].rotation_euler=a;player[n].keyframe_insert(data_path='rotation_euler',frame=nf)
 chair.rotation_euler=seat;chair.keyframe_insert(data_path='rotation_euler',frame=nf)
for f,a in [(0,0),(27,0),(36,45),(45,90),(79,90),(92,0),(150,0)]:rot(hatch,f,(a,0,0))
for f,a in [(0,0),(70,0),(87,180)]:rot(chair,f,(0,0,a))
for action in bpy.data.actions:
 for fc in action.fcurves:
  for k in fc.keyframe_points:
   if fc.data_path=='hide_render':k.interpolation='CONSTANT'
   else:k.interpolation='BEZIER';k.handle_left_type=k.handle_right_type='AUTO_CLAMPED'
exec(compile((R/'tools/blender/outline_hand_contacts.py').read_text(),'outline_hand_contacts.py','exec'))
s.timeline_markers.clear()
cam=bpy.data.objects['Review — cutaway'];s.camera=cam
cam.constraints.clear();cam.location=xyz((11,10,-14));target=xyz((0,3.6,-2.1));cam.rotation_euler=(target-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.type='ORTHO';cam.data.ortho_scale=10
s.render.engine='BLENDER_WORKBENCH';s.render.resolution_x=800;s.render.resolution_y=700;s.render.resolution_percentage=100
s.render.fps=30;s.frame_start=0;s.frame_end=150
s.display.shading.light='STUDIO';s.display.shading.color_type='TEXTURE';s.display.shading.show_shadows=True;s.display.shading.show_cavity=True;s.display.shading.background_type='WORLD';s.world.color=(.055,.075,.10)
s.render.film_transparent=False
s.display.shading.background_type='VIEWPORT';s.display.shading.background_color=(.055,.075,.10)
for f,pos,target,scale in [(0,(11,10,-14),(0,3.6,-2.1),10),(65,(11,10,-14),(0,3.6,-2.1),10),(96,(10,7,-3.6),(0,3,-3),7.7),(150,(10,7,-3.6),(0,3,-3),7.7)]:
 cam.location=xyz(pos);cam.rotation_euler=(xyz(target)-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.ortho_scale=scale
 cam.keyframe_insert(data_path='location',frame=f);cam.keyframe_insert(data_path='rotation_euler',frame=f);cam.data.keyframe_insert(data_path='ortho_scale',frame=f)
s.render.image_settings.file_format='PNG';s.frame_set(0)
notes=bpy.data.texts.new('OUTLINE REVIEW SCOPE');notes.write('Five-second outside motion draft. Shell cutaway from frame 66; canopy omitted for readability. Exterior FK blocking, not contact-polished or runtime-integrated. Original interior performance reused. Previous Blender source preserved.')
bpy.ops.wm.save_as_mainfile(filepath=str(R/'src/main/blender/deepseeker_outline_entry_v2.blend'))
frames=list(range(0,151,2)) if '--motion' in sys.argv else [0,18,36,54,72,90,112,134,150]
for f in frames:
 s.frame_set(f);s.render.filepath=str(O/f'frame-{f:03}.png');bpy.ops.render.render(write_still=True)
print('OUTLINE_PREVIEW_DONE')
