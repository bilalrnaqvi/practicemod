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
# Keep the root low: no automatic rise to chase the lid's full arc.
# The hand lifts the first 30 degrees, releases, and the lid completes its opening.
original={}
for f in range(115):
 s.frame_set(f);original[f]={side:player[side+'Arm_bend'].matrix_world @ Vector((0,0,-1.5)) for side in ('right','left')}
report=[]
for f in range(115):
 s.frame_set(f)
 for side in ('right','left'):
  target=original[f][side];weight=0
  if side=='left' and f<=38:
   target=xyz((.85,5.53,-1.8));weight=min(1,(42-f)/8)
  if side=='right' and f<=28:
   target=hatch.matrix_world @ xyz((5.6/16,(97-93)/16,(-38.4+14.85)/16))
   weight=min(1,(28-f)/5)
  if 34<=f<=65:
   reach=min(1,max(0,(f-34)/(14 if side=='right' else 8)))
   target=xyz((.62-1.24*reach,5.50,-1.22 if side=='right' else -2.12))
   weight=min(1,(f-34)/6,(65-f)/8)
  if 88<=f<=110:
   target=xyz((1.50,min(4.07,root.location.z+.55),.48 if side=='right' else -.48))
   weight=min(1,(f-88)/4,(110-f)/4)
  if weight>0:
   wanted=original[f][side].lerp(target,weight)
   error=solve_hand(side,wanted,f)
   report.append({'frame':f,'side':side,'weight':weight,'error_blocks':error})
(O/'hand-contacts.json').write_text(json.dumps(report,indent=2))
print('HAND_CONTACT_MAX',max(x['error_blocks'] for x in report))
