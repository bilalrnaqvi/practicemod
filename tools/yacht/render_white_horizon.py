"""Render actual exported cuboids in Blender, never a concept-image generator.
blender --background --python tools/yacht/render_white_horizon.py -- [structural|all|view-name]
"""
import bpy,json,sys,math
from pathlib import Path
from mathutils import Vector
from collections import defaultdict
R=Path(__file__).resolve().parents[2];D=R/'src/main/yacht/white_horizon/exports';O=R/'output/yacht/white-horizon';O.mkdir(parents=True,exist_ok=True)
data=json.loads((D/'scene.json').read_text());layout=json.loads((D.parent/'white_horizon.layout.json').read_text())
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
s=bpy.context.scene;s.render.engine='CYCLES';s.cycles.samples=24;s.cycles.use_denoising=True
s.render.resolution_x=1600;s.render.resolution_y=1000;s.render.resolution_percentage=100
s.world.color=(.6,.6,.6);s.world.use_nodes=True;s.world.node_tree.nodes['Background'].inputs[0].default_value=(.54,.66,.77,1);s.world.node_tree.nodes['Background'].inputs[1].default_value=.7
s.view_settings.view_transform='AgX';s.render.image_settings.file_format='PNG'
# Map model coordinates to Blender, maintaining right-handed axes.
def V(p):return (p[0],-p[2],p[1])
mats={}
for mat in data['materials']:
 name=mat['name'];m=bpy.data.materials.new(name);m.use_nodes=True;n=m.node_tree.nodes;bs=n.get('Principled BSDF');bs.inputs['Roughness'].default_value=.8
 t=n.new('ShaderNodeTexImage');t.image=bpy.data.images.load(str(D/mat['path']));t.interpolation='Closest';m.node_tree.links.new(t.outputs['Color'],bs.inputs['Base Color'])
 if name in ('glass','water'):
  # Alpha shader, no refractive geometry distortion; reflect limitation in handoff.
  mix=n.new('ShaderNodeMixShader');trans=n.new('ShaderNodeBsdfTransparent');mix.inputs[0].default_value=.22 if name=='glass' else .65
  m.node_tree.links.new(trans.outputs[0],mix.inputs[1]);m.node_tree.links.new(bs.outputs[0],mix.inputs[2]);m.node_tree.links.new(mix.outputs[0],n.get('Material Output').inputs[0]);bs.inputs['Roughness'].default_value=.25
 if name=='lamp':
  bs.inputs['Emission Color'].default_value=(1,.55,.16,1);bs.inputs['Emission Strength'].default_value=1.5
 mats[name]=m
faces=[('north',(0,3,2,1)),('south',(4,5,6,7)),('west',(0,4,7,3)),('east',(1,2,6,5)),('down',(0,1,5,4)),('up',(3,7,6,2))]
collection=bpy.data.collections.new('Actual canonical yacht geometry');s.collection.children.link(collection)
def mesh_scene(es):
 for obj in list(collection.objects):bpy.data.objects.remove(obj,do_unlink=True)
 buckets=defaultdict(list)
 for e in es:buckets[e['material']].append(e)
 for mat,elems in buckets.items():
  verts=[];polys=[];uvs=[]
  for e in elems:
   x,y,z=e['min'];X,Y,Z=e['max'];i=len(verts);verts.extend(V(v) for v in [(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)])
   for face,inds in faces:
    polys.append(tuple(i+k for k in inds));u,v,U,W=e['faces'][face]['uv'];uvs.extend([(u/16,1-W/16),(U/16,1-W/16),(U/16,1-v/16),(u/16,1-v/16)])
  mesh=bpy.data.meshes.new(mat);mesh.from_pydata(verts,[],polys);mesh.materials.append(mats[mat]);uv=mesh.uv_layers.new()
  for i,val in enumerate(uvs):uv.data[i].uv=val
  ob=bpy.data.objects.new(mat,mesh);collection.objects.link(ob)
# Camera and studio lamps.
bpy.ops.object.camera_add();cam=bpy.context.object;s.camera=cam;cam.data.lens=25;cam.data.clip_end=400
for loc,energy,size in [((30,30,60),65000,45),((-30,-30,35),35000,35)]:
 bpy.ops.object.light_add(type='AREA',location=loc);l=bpy.context.object;l.data.energy=energy;l.data.shape='DISK';l.data.size=size;l.rotation_euler=(Vector((0,0,0))-l.location).to_track_quat('-Z','Y').to_euler()
bpy.ops.object.light_add(type='SUN',location=(10,10,40));sun=bpy.context.object;sun.rotation_euler=(.45,-.4,-.5);sun.data.energy=1.2;sun.data.angle=.15
# Context sea is a review plane, explicitly excluded from exports/collision.
bpy.ops.mesh.primitive_plane_add(size=400,location=(0,0,-5.1));sea=bpy.context.object;sea.name='REVIEW ONLY — ocean context';sea.data.materials.append(mats['water'])
refs=bpy.data.collections.new('REVIEW ONLY — 0.6 x 1.8 player references');s.collection.children.link(refs)
def ref_box(lo,hi,mat):
 bpy.ops.mesh.primitive_cube_add(size=1,location=V([(a+b)/2 for a,b in zip(lo,hi)]));o=bpy.context.object;o.dimensions=(hi[0]-lo[0],hi[2]-lo[2],hi[1]-lo[1]);o.data.materials.append(mats[mat]);
 for c in list(o.users_collection):c.objects.unlink(o)
 refs.objects.link(o)
def people(points):
 for o in list(refs.objects):bpy.data.objects.remove(o,do_unlink=True)
 for x,y,z in points:
  ref_box([x-.25,y+1.3,z-.25],[x+.25,y+1.8,z+.25],'wood')
  ref_box([x-.3,y+.6,z-.15],[x+.3,y+1.3,z+.15],'navy')
  for xx in (x-.25,x+.05):ref_box([xx,y,z-.15],[xx+.2,y+.6,z+.15],'frame')
# All targets use actual canonical geometry. Filters remove decks/walls solely for review.
views={
 'structural':((62,75,48),(0,0,0),74,'ORTHO',lambda e:True,[(0,0,-15),(0,4,-5),(0,-4,2)]),
 'exterior_aft':((62,75,43),(0,0,1),74,'ORTHO',lambda e:True,[(0,0,-15),(0,4,-6),(0,8,7)]),
 'exterior_bow':((-58,-75,40),(0,0,1),74,'ORTHO',lambda e:True,[(0,0,25),(0,4,14),(0,8,7)]),
 'port_elevation':((-90,0,12),(0,0,1),70,'ORTHO',lambda e:True,[(0,0,-15),(0,4,-6)]),
 'cutaway_longitudinal':((65,58,32),(0,0,0),73,'ORTHO',lambda e:e['max'][0]<=.01 and e['category'] not in ('glazing',),[(-.5,-4,1),(-.5,0,1),(-4,4,1)]),
 'deck_machinery':((0,0,80),(0,0,-8),65,'ORTHO',lambda e:e['deck']=='machinery' and e['category']!='hull',[(0,-8,0)]),
 'deck_lower':((0,0,80),(0,0,-4),65,'ORTHO',lambda e:e['deck'] in ('lower',) and e['category']!='hull',[(0,-4,-15),(0,-4,0),(0,-4,15)]),
 'deck_main':((0,0,80),(0,0,0),65,'ORTHO',lambda e:e['deck'] in ('main','swim'),[(0,0,-15),(0,0,0),(0,0,24)]),
 'deck_upper':((0,0,80),(0,0,4),65,'ORTHO',lambda e:e['deck']=='upper',[(0,4,-15),(-4,4,0),(0,4,13)]),
 'deck_roof':((0,-8,80),(0,-8,8),45,'ORTHO',lambda e:e['deck']=='roof' and e['max'][1]<11,[(0,8,2),(0,8,12)]),
 'salon_interior':((0,1.62,28-30),(0,3.4,40-30),0,'MODEL',lambda e:True,[(2,0,5),(-3,4,7)]),
 'guest_interior':((-2.3,-2.38,28-30),(-6,-3,32-30),0,'MODEL',lambda e:True,[(-2.25,-4,1.5)]),
 'bridge_interior':((-.5,5.62,44-30),(-3,5.2,49-30),0,'MODEL',lambda e:True,[(-2.5,4,17)]),
 'cabins_cutaway':((34,38,22),(-3.5,0,-3),54,'ORTHO',lambda e:e['deck']=='lower' and e['max'][0]<=.01 and e['category']!='hull' and '_wall' not in e['name'] and '_lintel' not in e['name'],[(-3,-4,-15),(-3,-4,0),(-3,-4,8)]),
 'stair_cutaway':((24,26,16),(-3.5,8,-2),25,'ORTHO',lambda e:(e['min'][2]>=-12 and e['max'][2]<=-4 and e['min'][0]>=-6.1 and e['max'][0]<=-.9),[(-5,-4,-9),(-2,0,-11),(-5,2,-6)]),
}
arg=sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [];selection=arg[0] if arg else 'all'
keys=[k for k in views if k!='structural'] if selection=='all' else [selection]
for key in keys:
 pos,target,scale,kind,pred,pp=views[key];mesh_scene([e for e in data['elements'] if pred(e)]);people(pp)
 sea.hide_render=key.startswith('deck_') or 'interior' in key or 'cutaway' in key
 if kind=='MODEL':pos,target=V(pos),V(target);cam.data.type='PERSP';cam.data.lens=20
 else:cam.data.type='ORTHO';cam.data.ortho_scale=scale
 cam.location=pos;cam.rotation_euler=(Vector(target)-cam.location).to_track_quat('-Z','Y').to_euler()
 if key.startswith('deck_'):
  cam.rotation_euler=(0,0,0);cam.rotation_euler=(Vector(target)-cam.location).to_track_quat('-Z','Y').to_euler();s.render.resolution_x=1000;s.render.resolution_y=1700;cam.data.ortho_scale=65 if key!='deck_roof' else 32
 else:s.render.resolution_x=1600;s.render.resolution_y=1000
 s.render.filepath=str(O/(key+'.png'));bpy.ops.render.render(write_still=True)
 print('RENDERED '+key,flush=True)
