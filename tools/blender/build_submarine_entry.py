"""Build an editable 4.8-second boarding scene on the upstream Emotecraft rig.

Run with Blender 4.5 LTS, background mode, --disable-autoexec. The pose sheet is
explicit choreography, not a resampling of the old Java animation. Blender owns
the curves, editable keyframes, prop transforms and camera cuts.
"""
import bpy
import json
import math
import bmesh
import sys
from pathlib import Path
from mathutils import Vector, Matrix

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/animation/submarine-blender'
OUT.mkdir(parents=True, exist_ok=True)
SOURCE = ROOT / 'src/main/blender/deepseeker_entry.blend'
SOURCE.parent.mkdir(parents=True, exist_ok=True)
args = sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else []
if SOURCE.exists() and '--force' not in args:
    raise SystemExit('Existing Blender source preserved. Use -- --force only to regenerate the initial scene.')
bpy.ops.wm.open_mainfile(filepath=str(ROOT / 'tools/blender/vendor/emote_creator_bend.blend'),
                         load_ui=False, use_scripts=False)
scene = bpy.context.scene
scene.render.engine = 'CYCLES'
scene.cycles.samples = 16
scene.cycles.use_denoising = True
scene.render.resolution_x = 960
scene.render.resolution_y = 640
scene.render.resolution_percentage = 100
scene.render.fps = 30
scene.frame_start, scene.frame_end = 0, 144
scene.world.color = (.18, .18, .18)
scene.view_settings.view_transform = 'Standard'
scene.render.image_settings.file_format = 'PNG'

# Keep the upstream editable meshes, skin and hierarchy; discard its sample keys.
player_names = ['torso', 'torso_bend', 'head', 'rightArm', 'rightArm_bend',
                'leftArm', 'leftArm_bend', 'rightLeg', 'rightLeg_bend',
                'leftLeg', 'leftLeg_bend']
player = {name: bpy.data.objects[name] for name in player_names}
for obj in list(bpy.data.objects):
    obj.animation_data_clear()
    if obj.name not in player_names:
        bpy.data.objects.remove(obj, do_unlink=True)
for obj in player.values():
    obj.rotation_euler = (0, 0, 0)
    obj.rotation_mode = 'XYZ'
for material in bpy.data.materials:
    if material.use_nodes:
        for node in material.node_tree.nodes:
            if node.type == 'TEX_IMAGE': node.interpolation = 'Closest'

bpy.context.view_layer.update()
rest_matrices = {name: obj.matrix_world.copy() for name, obj in player.items()}

def empty(name, position=(0, 0, 0)):
    obj = bpy.data.objects.new(name, None)
    scene.collection.objects.link(obj)
    obj.location = position
    obj.empty_display_size = .2
    return obj

def xyz(point):
    return Vector((point[0], -point[2], point[1]))

master = empty('PLAYER — hull-space root')
master.scale = (.234375,) * 3  # Same .9375 player rendering scale as Minecraft.
player['torso'].parent = master
player['torso'].matrix_parent_inverse = Matrix.Identity(4)

data = json.loads((ROOT / 'output/animation/boarding/scene.json').read_text())
hatch = empty('HATCH — hinge', xyz(data['hatchPivot']))
chair = empty('CHAIR — swivel', (0, -data['chairZ'], 0))
bpy.context.view_layer.update()
tex = bpy.data.images.load(str(ROOT / 'src/main/resources/assets/practicemod/textures/entity/deepseeker.png'))
tex.pack()
mat = bpy.data.materials.new('Deepseeker — original pixel texture')
mat.use_nodes = True
bsdf = mat.node_tree.nodes.get('Principled BSDF')
bsdf.inputs['Roughness'].default_value = .82
node = mat.node_tree.nodes.new('ShaderNodeTexImage')
node.image = tex
node.interpolation = 'Closest'
mat.node_tree.links.new(node.outputs['Color'], bsdf.inputs['Base Color'])
shell = bpy.data.collections.new('Hull — hide for interior review')
scene.collection.children.link(shell)
interior = bpy.data.collections.new('Interior — original runtime mesh')
scene.collection.children.link(interior)
for index, (name, flat) in enumerate(zip(data['names'], data['hull'])):
    vertices = [xyz(flat[i:i+3]) for i in range(0, len(flat), 8)]
    faces = [tuple(range(i, i+4)) for i in range(0, len(vertices), 4)]
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(vertices, [], faces)
    mesh.materials.append(mat)
    uv = mesh.uv_layers.new()
    for polygon in mesh.polygons:
        for loop_index in polygon.loop_indices:
            vi = mesh.loops[loop_index].vertex_index * 8
            uv.data[loop_index].uv = (flat[vi+3], 1-flat[vi+4])
    obj = bpy.data.objects.new(name, mesh)
    # Full hull remains in the file. Review cuts hide outer panels, not furniture.
    outer = (index < 245 and not name.startswith('floor')) or data['glass'][index]
    (shell if outer else interior).objects.link(obj)
    part = data['parts'][index]
    if part in (1, 3):
        prop = hatch if part == 1 else chair
        obj.parent = prop
        obj.matrix_parent_inverse = prop.matrix_world.inverted()

def rotation(obj, frame, angles):
    obj.rotation_euler = tuple(math.radians(v) for v in angles)
    obj.keyframe_insert(data_path='rotation_euler', frame=frame)

def keyprop(obj, axis, keys):
    for frame, value in keys:
        angles = [0, 0, 0]
        angles[axis] = value
        rotation(obj, frame, angles)

# Pose fields: frame, hip in hull coordinates, heading, torso lean, head pitch,
# right shoulder/elbow, left shoulder/elbow, right thigh/knee, left thigh/knee.
# Arm and leg triples retain deliberate asymmetry; rotations are Blender degrees.
POSES = [
 (0, (1.95,5.97,-.05),90,-40,18, (38,0,-8),38,(22,0,10),30, (52,0,0),88,(46,0,0),82),
 (5, (1.95,5.97,-.05),90,-40,18, (52,0,-9),42,(30,0,12),48, (52,0,0),88,(46,0,0),82),
 (12,(1.98,6.09,-.05),90,-18,5, (94,0,-12),58,(24,0,15),32, (30,0,0),48,(24,0,0),42),
 (21,(1.93,6.24,-.04),82,-8,-6, (145,0,-10),22,(52,0,20),60, (24,0,-8),48,(-5,0,5),15),
 (27,(1.67,6.26,-.04),40,-16,12, (112,0,-22),45,(88,0,20),58, (60,0,-8),85,(22,0,5),52),
 (33,(1.12,6.08,-.04),-45,-13,22, (128,0,-18),28,(122,0,16),32, (45,0,-5),70,(62,0,4),88),
 (39,(.95,5.55,-.04),-90,-5,25, (148,0,-6),20,(152,0,8),25, (20,0,0),38,(46,0,0),62),
 (45,(.95,4.90,-.04),-90,-4,18, (150,0,-6),22,(135,0,8),40, (42,0,0),60,(14,0,0),25),
 (51,(.95,4.20,-.04),-90,-5,14, (130,0,-8),40,(150,0,6),24, (12,0,0),25,(42,0,0),60),
 (57,(.95,3.46,-.05),-90,-8,16, (145,0,-7),26,(125,0,9),48, (35,0,0),55,(8,0,0),18),
 (63,(.75,2.62,-.18),-70,-15,18, (105,0,-15),60,(70,0,15),65, (35,0,0),50,(-12,0,0),32),
 (69,(.50,1.89,-.65),-25,-18,12, (40,0,-8),70,(-25,0,9),35, (-28,0,0),35,(52,0,0),60),
 (75,(.18,1.87,-1.40),0,-10,5, (-32,0,-7),42,(46,0,8),65, (44,0,0),30,(-35,0,0),75),
 (81,(0,1.88,-2.26),0,-12,6, (44,0,-8),65,(-30,0,8),38, (-30,0,0),65,(48,0,0),35),
 (87,(0,1.91,-3.08),0,-8,5, (-20,0,-8),45,(35,0,8),65, (38,0,0),35,(-26,0,0),60),
 (93,(0,2.40,-3.74),52,-14,8, (45,0,-20),70,(40,0,20),65, (65,0,0),85,(25,0,0),55),
 (99,(0,2.88,-4.34),150,-20,10, (34,0,-22),80,(38,0,22),78, (80,0,-8),-68,(68,0,8),-55),
 (105,(0,2.77,-4.6875),180,-10,8, (18,0,-15),80,(18,0,15),80, (82,0,-6),-82,(82,0,6),-82),
 (111,(0,2.79,-4.6875),190,0,0, (12,0,-12),80,(12,0,12),80, (82,0,-6),-82,(82,0,6),-82),
 (123,(0,2.79,-4.6875),280,3,-3, (12,0,-12),80,(12,0,12),80, (82,0,-6),-82,(82,0,6),-82),
 (135,(0,2.79,-4.6875),357,0,0, (28,0,-8),65,(28,0,8),65, (82,0,-6),-82,(82,0,6),-82),
 (144,(0,2.79,-4.6875),360,0,0, (42,0,-4),40,(42,0,4),40, (82,0,-6),-82,(82,0,6),-82),
]
for frame, hip, heading, lean, head, ra, re, la, le, rl, rk, ll, lk in POSES:
    master.location = xyz(hip)
    master.keyframe_insert(data_path='location', frame=frame)
    rotation(master, frame, (0,0,heading))
    rotation(player['torso_bend'], frame, (lean,0,0))
    rotation(player['head'], frame, (head,0,0))
    for name, angles in [('rightArm',ra),('leftArm',la),('rightLeg',rl),('leftLeg',ll)]:
        rotation(player[name],frame,angles)
    for name, angle in [('rightArm_bend',re),('leftArm_bend',le),
                         ('rightLeg_bend',-abs(rk)),('leftLeg_bend',-abs(lk))]:
        rotation(player[name],frame,(angle,0,0))

# Weld the legacy display pieces into a skinned surface. The upstream preview
# meshes leave open seams on bending; an armature with shared joint vertices
# keeps the elbows, knees and waist continuous while retaining its skin UVs.
armdata = bpy.data.armatures.new('Player deformation')
arm = bpy.data.objects.new('Player deformation',armdata)
scene.collection.objects.link(arm)
arm.parent = master
bpy.context.view_layer.objects.active = arm
arm.select_set(True)
bpy.ops.object.mode_set(mode='EDIT')
for name in player_names:
    bone = armdata.edit_bones.new(name)
    bone.head = rest_matrices[name].translation
    bone.tail = bone.head + Vector((0,1,0))
bpy.ops.object.mode_set(mode='OBJECT')
for name,obj in player.items():
    constraint = arm.pose.bones[name].constraints.new('COPY_TRANSFORMS')
    constraint.target = obj
    constraint.owner_space = constraint.target_space = 'WORLD'

verts, faces, uvs, owners = [], [], [], []
for name,obj in player.items():
    offset = len(verts)
    verts.extend(rest_matrices[name] @ v.co for v in obj.data.vertices)
    owners.extend([name]*len(obj.data.vertices))
    for poly in obj.data.polygons:
        faces.append(tuple(offset+i for i in poly.vertices))
        uvs.extend(tuple(obj.data.uv_layers.active.data[i].uv) for i in poly.loop_indices)
mesh = bpy.data.meshes.new('Continuous player skin')
mesh.from_pydata(verts,[],faces)
mesh.materials.append(player['head'].data.materials[0])
uv = mesh.uv_layers.new()
for loop,coord in zip(uv.data,uvs): loop.uv = coord
skin = bpy.data.objects.new('Player — continuous skin',mesh)
scene.collection.objects.link(skin)
skin.parent = master
groups = {name:skin.vertex_groups.new(name=name) for name in player_names}
for index,name in enumerate(owners): groups[name].add([index],1,'REPLACE')
# Average bone weights on coincident vertices before welding the surface.
coincident = {}
for index,vertex in enumerate(mesh.vertices):
    key = (owners[index].removesuffix('_bend'), *[round(v,5) for v in vertex.co])
    coincident.setdefault(key,[]).append(index)
for indices in coincident.values():
    names = set(owners[i] for i in indices)
    for i in indices:
        for name in names: groups[name].add([i],1/len(names),'REPLACE')
bm = bmesh.new()
bm.from_mesh(mesh)
bm.verts.ensure_lookup_table()
chains = {}
for index,name in enumerate(owners):
    chains.setdefault(name.removesuffix('_bend'),[]).append(bm.verts[index])
for vertices in chains.values():
    bmesh.ops.remove_doubles(bm,verts=vertices,dist=.00001)
bm.to_mesh(mesh)
bm.free()
modifier = skin.modifiers.new('Joint deformation','ARMATURE')
modifier.object = arm
for obj in player.values(): obj.hide_render = True; obj.display_type = 'WIRE'

keyprop(hatch, 0, [(0,0),(5,0),(12,38),(21,104),(30,105),(49,105),(61,78),(72,0),(144,0)])
keyprop(chair, 2, [(0,0),(42,0),(66,180),(105,180),(111,190),(123,280),(135,357),(144,360)])
sys.path.insert(0,str(Path(__file__).parent))
from boarding_contacts import bake_contacts, bake_feet
errors = bake_contacts(scene,player,master,hatch,chair,empty)
(OUT / 'contact-review.json').write_text(json.dumps(errors,indent=2))
foot_errors = bake_feet(scene,player,master,empty)
(OUT / 'foot-contact-review.json').write_text(json.dumps(foot_errors,indent=2))
for action in bpy.data.actions:
    for curve in action.fcurves:
        for key in curve.keyframe_points:
            key.interpolation = 'BEZIER'
            key.handle_left_type = key.handle_right_type = 'AUTO_CLAMPED'

for frame, label in [(0,'GRIP'),(5,'LIFT HATCH'),(27,'HOP THROUGH'),(39,'CATCH LADDER'),
                     (63,'STEP OFF'),(93,'TURN INTO SEAT'),(105,'SIT'),(111,'CHAIR SWIVEL'),(144,'CONTROL')]:
    scene.timeline_markers.new(label, frame=frame)

def camera(name, pos, target, lens=40):
    block = bpy.data.cameras.new(name)
    obj = bpy.data.objects.new(name, block)
    scene.collection.objects.link(obj)
    obj.location = xyz(pos)
    obj.rotation_euler = (xyz(target)-obj.location).to_track_quat('-Z','Y').to_euler()
    block.lens = lens
    block.clip_start = .05
    return obj

exterior = camera('01 — Hatch', (6.3,8.4,-5.0),(1.4,5.9,-.05),42)
descent = camera('02 — Ladder',(-.2,3.4,-2),(.95,3.5,-.05),18)
seating = camera('03 — Chair',(-3.2,4.3,-5.9),(0,3.05,-4.5),40)
review = camera('Review — cutaway',(10,8.5,-10),(0,3.6,-2),40)
focus = empty('Camera focus — player center')
focus.parent = master
focus.location = (0,0,1)
for cam in (descent,seating):
    track = cam.constraints.new('TRACK_TO')
    track.target, track.track_axis, track.up_axis = focus,'TRACK_NEGATIVE_Z','UP_Y'
for frame, cam in [(0,exterior),(39,descent),(69,seating)]:
    scene.timeline_markers.new(cam.name,frame=frame).camera = cam
for obj in shell.objects:
    for frame,hidden in [(0,False),(38,False),(39,True),(144,True)]:
        obj.hide_render = hidden
        obj.keyframe_insert(data_path='hide_render',frame=frame)
scene.camera = exterior
for name,pos,power,size in [('Key',(4,10,-4),1800,6),('Fill',(-4,6,-2),1100,5),
                            ('Cabin',(0,4,-2),130,2)]:
    ld = bpy.data.lights.new(name, 'AREA')
    lo = bpy.data.objects.new(name, ld)
    scene.collection.objects.link(lo)
    lo.location = xyz(pos)
    lo.rotation_euler = (xyz((0,2.5,-2))-lo.location).to_track_quat('-Z','Y').to_euler()
    ld.energy, ld.shape, ld.size = power, 'DISK', size

notes = bpy.data.texts.new('READ ME — boarding')
notes.write('Deepseeker entry — 4.8 seconds, 30 fps.\n'
            'Editable pose keys on the Emotecraft legacy articulated rig.\n'
            'Original vehicle geometry/texture; HATCH and CHAIR empties animate props.\n'
            'Camera markers cut from hatch to ladder to chair.\n'
            'Interior shots are a cutaway: outer shell visibility keys start at frame 39.\n'
            'This is Blender choreography, not a validated Minecraft integration.\n'
            'Source rig: KosmX/emotes, GPL-3.0; license beside vendored source.\n')
scene.frame_set(0)
bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE))

# Export evaluated samples for integration review; these are not a new runtime API.
samples = []
for frame in range(145):
    scene.frame_set(frame)
    samples.append({'frame':frame, 'seconds':frame/30,
                    'hip': [master.location.x, master.location.z, -master.location.y],
                    'heading':math.degrees(master.rotation_euler.z),
                    'hatch':math.degrees(hatch.rotation_euler.x),
                    'chair':math.degrees(chair.rotation_euler.z),
                    'pose':{name:list(obj.rotation_euler) for name,obj in player.items()}})
(OUT / 'evaluated-motion.json').write_text(json.dumps({'fps':30,'duration':4.8,'frames':samples},indent=2))

# Review selected actual Blender frames before accepting or exporting the motion.
if 'render' in args:
    for frame in [0,12,27,39,57,75,99,111,123,144]:
        scene.frame_set(frame)
        scene.render.filepath = str(OUT / f'pose-{frame:03}.png')
        bpy.ops.render.render(write_still=True)
print('BLENDER_ENTRY_SAVED',SOURCE)
