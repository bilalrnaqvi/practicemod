"""Check sampled animated head/limb convex envelopes against actual hull geometry."""
import bpy,json,itertools,sys
from pathlib import Path
from mathutils import Vector
R=Path(__file__).resolve().parents[2];O=R/'output/animation/boarding-runtime'
bpy.ops.wm.open_mainfile(filepath=str(R/'src/main/blender/deepseeker_boarding_runtime.blend'),load_ui=False,use_scripts=False)
s=bpy.context.scene
# Separating axes for convex polyhedra, with broad phase before edge products.
def shape(obj,box=False):
 m=obj.matrix_world
 if box:
  v=[m@Vector(p) for p in obj.bound_box]; edges=[v[i]-v[0] for i in (1,3,4)];norm=[a.cross(b) for a,b in itertools.combinations(edges,2)]
 else:
  v=[m@p.co for p in obj.data.vertices];edges=[v[e.vertices[1]]-v[e.vertices[0]] for e in obj.data.edges];norm=[m.to_3x3()@p.normal for p in obj.data.polygons]
 def unique(vs):
  out=[]
  for a in vs:
   if a.length<1e-7:continue
   a=a.normalized()
   if not any(abs(a.dot(b))>.99999 for b in out):out.append(a)
  return out
 return v,unique(edges),unique(norm),[min(p[i] for p in v) for i in range(3)],[max(p[i] for p in v) for i in range(3)]
def hit(a,b):
 av,ae,an,alo,ahi=a;bv,be,bn,blo,bhi=b
 if any(ahi[i]<blo[i]+.015 or bhi[i]<alo[i]+.015 for i in range(3)):return 0
 depth=100
 for axis in an+bn+[x.cross(y) for x in ae for y in be]:
  if axis.length<1e-6:continue
  axis.normalize();aa=[p.dot(axis) for p in av];bb=[p.dot(axis) for p in bv];d=min(max(aa),max(bb))-max(min(aa),min(bb))
  if d<.015:return 0
  depth=min(depth,d)
 return depth
parts=['head','torso','torso_bend','rightArm','rightArm_bend','leftArm','leftArm_bend','rightLeg','rightLeg_bend','leftLeg','leftLeg_bend']
hull=list(bpy.data.collections['Rear entry — actual runtime geometry'].objects)
fixed={o.name:shape(o) for o in hull if not o.parent};report=[]
for f in range(s.frame_end+1):
 s.frame_set(f);body={n:shape(bpy.data.objects[n],True) for n in parts}
 for o in hull:
  if o.get('glass'):continue
  h=fixed.get(o.name) or shape(o)
  for n,b in body.items():
   d=hit(b,h)
   if d:report.append(dict(frame=f,part=n,hull=o.name,depth=round(d,4)))
(O/'rear-body-clearance.json').write_text(json.dumps(report,indent=2))
print('HEAD', [p for p in report if p['part']=='head'])
print('WORST',sorted(report,key=lambda p:p['depth'])[-25:])
