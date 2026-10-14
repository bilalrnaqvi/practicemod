"""Export the open Blender scene to the upstream Emotecraft/PAL legacy format.

Player pose only: hull-space root, hatch, chair and camera stay separate so the
server-owned vehicle sequence can coordinate them. This does not install a mod.
Axis signs match the vendored rig's embedded 'Export emote' script.
"""
import bpy, json, math
from mathutils import Matrix, Euler, Vector
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/animation/submarine-blender'
scene = bpy.context.scene
moves = []
reference = []
# The upstream exporter assumes its rig's original Euler order. This scene uses
# XYZ for native IK baking, so swapping Euler channels loses combined rotations.
# Convert the evaluated rotation matrix into Minecraft model coordinates first.
basis = Matrix(((-1,0,0),(0,0,-1),(0,-1,0)))
previous = {}
max_rotation_error = 0.0
for tick in range(97):
    frame = tick * scene.render.fps / 20
    scene.frame_set(math.floor(frame),subframe=frame % 1)
    root_inverse = bpy.data.objects['PLAYER — hull-space root'].matrix_world.inverted()
    points = {}
    for part in ('rightArm','leftArm','rightLeg','leftLeg'):
        point = bpy.data.objects[part+'_bend'].matrix_world @ Vector((0,0,-1.5))
        points[part] = list((basis @ (root_inverse @ point)) * 4 + Vector((0,12,0)))
    reference.append({'tick':tick,'tips':points})
    move = {'tick':tick,'easing':'LINEAR','turn':0}
    for name in ('head','torso','rightArm','leftArm','rightLeg','leftLeg'):
        rotation = basis @ bpy.data.objects[name].rotation_euler.to_matrix() @ basis.transposed()
        angles = rotation.to_euler('XYZ',previous.get(name,Euler((0,0,0),'XYZ')))
        previous[name] = angles.copy()
        max_rotation_error = max(max_rotation_error,
                max(abs(rotation[i][j]-angles.to_matrix()[i][j]) for i in range(3) for j in range(3)))
        values = {'pitch':angles.x,'yaw':angles.y,'roll':angles.z}
        if name == 'torso':
            # Legacy torso rotations load onto PAL's whole-body channel with X/Y negated.
            values['pitch'],values['yaw'] = -angles.x,-angles.y
        bend = bpy.data.objects.get(name+'_bend')
        if bend:
            if max(abs(bend.rotation_euler.y),abs(bend.rotation_euler.z)) > .0001:
                raise ValueError(f'{name}: elbow/knee has unsupported off-axis bending at tick {tick}')
            values['bend'] = -bend.rotation_euler.x
        move[name] = {key:round(value,8) for key,value in values.items()}
    moves.append(move)
document = {'name':'deepseeker_entry','author':'PracticeMod',
            'description':'4.8-second hatch entry and chair swivel; player pose track.',
            'emote':{'isLoop':'false','returnTick':0,'beginTick':0,'endTick':96,
                     'stopTick':97,'degrees':False,'moves':moves}}
(OUT/'deepseeker_entry.emote.json').write_text(json.dumps(document,indent=2))
(OUT/'rotation-export-review.json').write_text(json.dumps({
    'samples':len(moves),'max_matrix_component_error':max_rotation_error,
    'conversion':'Conjugate Blender XYZ rotation by model basis, then decompose to Minecraft Z-Y-X'},indent=2))
(OUT/'runtime-reference.json').write_text(json.dumps(reference,indent=2))
print('EXPORTED_STANDARD_PLAYER_CLIP',len(moves),'samples, 96 ticks')
