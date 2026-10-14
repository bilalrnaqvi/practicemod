"""Bake reachable arm contacts into the preview's original articulated rig."""
# Executed in preview builder namespace, after root/prop curves are authored.
def basis(a,b):
 second=b-a*a.dot(b)
 if second.length<1e-6:second=a.cross(Vector((0,1,0)))
 second.normalize()
 return Matrix((a,second,a.cross(second))).transposed()
def solve_hand(side,point,frame):
 upper=player[side+'Arm'];lower=player[side+'Arm_bend']
 shoulder=upper.matrix_world.translation.copy();delta=point-shoulder
 a,b,length=.05859375,.234375,.3515625
 cosine=max(-1,min(1,(delta.length_squared-a*a-b*b-length*length)/(2*b*length)))
 bend=math.acos(cosine)
 rest_hand=Vector((a if side=='right' else -a,length*math.sin(bend),-b-length*math.cos(bend)))
 rest_u=Vector((.25 if side=='right' else -.25,0,-1)).normalized()
 elbow=lower.matrix_world.translation-shoulder
 desired=(basis(delta.normalized(),elbow) @ basis(rest_hand.normalized(),rest_u).transposed()).to_quaternion()
 parent=upper.parent.matrix_world @ upper.matrix_parent_inverse
 upper.rotation_euler=(parent.to_quaternion().inverted() @ desired).to_euler('XYZ',upper.rotation_euler)
 lower.rotation_euler=(bend,0,0)
 for obj in (upper,lower):obj.keyframe_insert(data_path='rotation_euler',frame=frame)
 bpy.context.view_layer.update()
 return (lower.matrix_world @ Vector((0,0,-1.5))-point).length
# Alternating palms follow the curve of the actual painted marks.
plant={
 'right':[(0,(2.085,4.08,-2.50)),(6,(2.085,4.08,-2.50)),(12,(1.766,4.40,-2.27)),(18,(1.23,5.04,-1.81)),(23,(.80,5.50,-2.15))],
 'left':[(0,(2.085,4.08,-2.28)),(8,(1.766,4.40,-2.05)),(15,(1.447,4.72,-1.81)),(21,(1.23,5.04,-1.58)),(27,(.9,5.50,-1.80))]
}
def interpolate(keys,f):
 for (a,p),(b,q) in zip(keys,keys[1:]):
  if a<=f<=b:return xyz(p).lerp(xyz(q),(f-a)/(b-a))
 return xyz(keys[0 if f<keys[0][0] else -1][1])
# Shift body weight with the grasp so the hand can reach the moving handle.
corrections=[]
for f in range(23,50):
 s.frame_set(f)
 target=hatch.matrix_world @ xyz((5.6/16,(97-93)/16,(-38.4+14.85)/16))
 delta=target-player['rightArm'].matrix_world.translation
 correction=delta.normalized()*max(0,delta.length-.55)
 weight=min(1,(f-23)/4,(49-f)/4)
 corrections.append((f,root.location.copy()+correction*weight))
for f,loc in corrections:
 root.location=loc;root.keyframe_insert(data_path='location',frame=f)
# Capture original FK before adding dense contact keys.
original={}
for f in range(88):
 s.frame_set(f);original[f]={side:player[side+'Arm_bend'].matrix_world @ Vector((0,0,-1.5)) for side in plant}
report=[]
for f in range(88):
 s.frame_set(f)
 for side in plant:
  target=original[f][side];weight=0
  if f<=24:target=interpolate(plant[side],f);weight=1 if f<=21 else (27-f)/6
  if side=='right' and 23<=f<=49:
   # Original handle center, transformed by the reversed lid's actual hinge.
   local=xyz((5.6/16,(97-93)/16,(-38.4+14.85)/16))
   target=hatch.matrix_world @ local
   weight=min(1,(f-23)/4,(49-f)/4)
  if 70<=f<=84:
   target=xyz((1.50,min(4.07,root.location.z+.55),.48 if side=='right' else -.48))
   weight=min(1,(f-70)/3,(84-f)/3)
  if weight>0:
   wanted=original[f][side].lerp(target,weight)
   error=solve_hand(side,wanted,f)
   report.append({'frame':f,'side':side,'weight':weight,'error_blocks':error})
(O/'hand-contacts.json').write_text(json.dumps(report,indent=2))
print('HAND_CONTACT_MAX',max(x['error_blocks'] for x in report))
