import bpy,math,json
from pathlib import Path
R=Path(__file__).resolve().parents[2]
bpy.ops.wm.open_mainfile(filepath=str(R/'src/main/blender/deepseeker_boarding_runtime.blend'),load_ui=False,use_scripts=False)
s=bpy.context.scene
last={};gaps=[]
for f in range(s.frame_end+1):
 s.frame_set(f)
 for n in ['head','torso_bend','rightArm','leftArm','rightLeg','leftLeg']:
  q=bpy.data.objects[n].rotation_euler.to_quaternion()
  if n in last:
   d=math.degrees(last[n].rotation_difference(q).angle);d=min(d,360-d)
   gaps.append(dict(frame=f,part=n,degrees=d))
  last[n]=q
print('ROTATION_GAPS', sorted(gaps,key=lambda p:p['degrees'])[-15:])
(R/'output/animation/boarding-runtime/rear-rotation-continuity.json').write_text(json.dumps(gaps,indent=2))
