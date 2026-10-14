"""Regression guard for the approved requirement: no sideways hip twisting in entry.

This checks joint ranges, not artistic quality. Review moving front/side footage too.
"""
import bpy, math, json
from pathlib import Path
R=Path(__file__).resolve().parents[2]
bpy.ops.wm.open_mainfile(filepath=str(R/'src/main/blender/deepseeker_boarding_runtime.blend'),use_scripts=False)
s=bpy.context.scene
report={'max_hip_twist_degrees':0,'max_hip_abduction_degrees':0,'max_knee_flexion_degrees':0,'max_torso_lean_degrees':0}
for f in range(55):
 s.frame_set(f)
 report['max_torso_lean_degrees']=max(report['max_torso_lean_degrees'],abs(math.degrees(bpy.data.objects['torso'].rotation_euler.x)))
 assert abs(bpy.data.objects['torso_bend'].rotation_euler.x)<1e-5,'Vault must not fold the mid torso'
 for side in ('right','left'):
  hip=bpy.data.objects[side+'Leg'].rotation_euler
  knee=bpy.data.objects[side+'Leg_bend'].rotation_euler
  report['max_hip_twist_degrees']=max(report['max_hip_twist_degrees'],abs(math.degrees(hip.z)))
  report['max_hip_abduction_degrees']=max(report['max_hip_abduction_degrees'],abs(math.degrees(hip.y)))
  report['max_knee_flexion_degrees']=max(report['max_knee_flexion_degrees'],abs(math.degrees(knee.x)))
  assert abs(hip.z)<.001,'Sideways hip twist returned'
  assert abs(hip.y)<math.radians(50),'Hip abduction exceeds entry range'
  assert -math.radians(145)<=knee.x<=.001,'Knee folds past range or bends backward'
  assert abs(knee.y)+abs(knee.z)<.001,'Knee must remain a hinge'
assert report['max_torso_lean_degrees']<=50.01
(R/'output/animation/boarding-runtime/rear-anatomy.json').write_text(json.dumps(report,indent=2)+'\n')
print('ANATOMY',report)
