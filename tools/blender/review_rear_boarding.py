"""Render separate outside/cutaway and first-person inspection footage."""
import bpy,sys,math,json
from mathutils import Vector
from pathlib import Path
R=Path(__file__).resolve().parents[2];O=R/'output/animation/boarding-runtime/rear-review';O.mkdir(parents=True,exist_ok=True)
bpy.ops.wm.open_mainfile(filepath=str(R/'src/main/blender/deepseeker_boarding_runtime.blend'),load_ui=False,use_scripts=False)
s=bpy.context.scene;xyz=lambda p:Vector((p[0],-p[2],p[1]));root=bpy.data.objects['PLAYER — hull-space root']
cam=bpy.data.objects.get('Review — cutaway');cam.animation_data_clear();cam.data.animation_data_clear();cam.constraints.clear();s.camera=cam
mode='first-person' if '--first-person' in sys.argv else 'outside';angle=next((a for a in ('front','side') if '--'+a in sys.argv),None)
folder=O/(angle or mode);folder.mkdir(exist_ok=True)
frames=list(range(0,s.frame_end+1,3)) if '--motion' in sys.argv else [0,18,30,46,60,76,96,120,156,174,192,216,240,270,288]
if '--frames' in sys.argv:frames=[int(v) for v in sys.argv[sys.argv.index('--frames')+1].split(',')]
for f in frames:
 s.frame_set(f)
 for o in bpy.data.collections['Rear entry — actual runtime geometry'].objects:
  o.hide_render=bool(o.get('glass')) or (mode=='outside' and f>=38 and o.get('review_shell') and o.get('entry_part')!=1)
 for name in ('head','torso','torso_bend'):bpy.data.objects[name].hide_render=mode=='first-person'
 if mode=='outside':
  cam.data.type='ORTHO';cam.data.ortho_scale=4.8 if f<105 else 5.6
  target=(1.1,5.8,.35) if f<38 else (root.location.x,root.location.z+.45,-root.location.y)
  p=(6.8,8.8,7.5) if f<38 else (target[0]-6,target[1]+2.5,target[2]-4)
  if angle:
   target=(root.location.x,root.location.z+.2,-root.location.y)
   p=(target[0]-6,target[1]+.6,target[2]) if angle=='side' else (target[0],target[1]+.6,target[2]-6)
   cam.data.ortho_scale=2.8
   for obj in bpy.data.collections['Rear entry — actual runtime geometry'].objects:obj.hide_render=True
  cam.location=xyz(p);cam.rotation_euler=(xyz(target)-cam.location).to_track_quat('-Z','Y').to_euler()
 else:
  head=bpy.data.objects['head'];cam.data.type='PERSP';cam.data.lens=22;cam.data.clip_start=.025
  cam.location=head.matrix_world @ Vector((0,0,.75))
  pitch=bpy.data.objects['Gameplay — first-person look'].rotation_euler.x
  forward=root.rotation_euler.to_quaternion() @ Vector((0,math.cos(pitch),-math.sin(pitch)))
  cam.rotation_euler=forward.to_track_quat('-Z','Y').to_euler()
 s.render.filepath=str(folder/f'{f:03}.png');bpy.ops.render.render(write_still=True)
print('REVIEW_DONE',mode)
