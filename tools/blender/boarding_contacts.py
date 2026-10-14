"""Bake Blender's native two-bone IK into the editable player arm channels."""
import bpy, math
from mathutils import Vector, Matrix


def bake_contacts(scene, player, master, hatch, chair, empty):
    controls = bpy.data.collections.new('Contact controls — Blender IK')
    scene.collection.children.link(controls)
    armdata = bpy.data.armatures.new('Contact solver')
    rig = bpy.data.objects.new('Contact solver', armdata)
    controls.objects.link(rig)
    rig.show_in_front = True
    bpy.context.view_layer.objects.active = rig
    rig.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    for side in ('right','left'):
        elbow_x = .05859375 if side == 'right' else -.05859375
        upper = armdata.edit_bones.new(side)
        upper.head, upper.tail = (0,0,0),(elbow_x,0,-.234375)
        lower = armdata.edit_bones.new(side+'_lower')
        lower.head, lower.tail = upper.tail,(elbow_x,0,-.5859375)
        lower.parent, lower.use_connect = upper, True
    bpy.ops.object.mode_set(mode='OBJECT')
    ctrl = {}
    for side in ('right','left'):
        ctrl[side] = {}
        for kind in ('shoulder','hand','pole'):
            obj = empty(side+' '+kind+' — contact')
            for collection in list(obj.users_collection): collection.objects.unlink(obj)
            controls.objects.link(obj)
            ctrl[side][kind] = obj
        rig.pose.bones[side].constraints.new('COPY_LOCATION').target = ctrl[side]['shoulder']
        ik = rig.pose.bones[side+'_lower'].constraints.new('IK')
        ik.target, ik.pole_target = ctrl[side]['hand'],ctrl[side]['pole']
        ik.chain_count, ik.use_stretch = 2,False
        ik.pole_angle = 0
        rig.pose.bones[side].ik_stretch = 0
        lower_pose = rig.pose.bones[side+'_lower']
        lower_pose.ik_stretch = 0

    # Read the authored FK curves before replacing their contact spans.
    original = {}
    targets = {}
    for frame in range(145):
        scene.frame_set(frame)
        original[frame] = {}
        targets[frame] = {}
        for side in ('right','left'):
            upper,lower = player[side+'Arm'],player[side+'Arm_bend']
            shoulder,elbow = upper.matrix_world.translation.copy(),lower.matrix_world.translation.copy()
            hand = lower.matrix_world @ Vector((0,0,-1.5))
            original[frame][side] = (upper.rotation_euler.copy(),lower.rotation_euler.copy())
            weight = 0
            target = hand.copy()
            if side == 'right' and frame <= 18:
                # Push the lid, then let it complete its spring-assisted opening.
                weight = 1 if frame <= 11 else (18-frame)/7
                target = hatch.matrix_world @ Vector((.65625,1.3125,.55))
            if 41 <= frame <= 65:
                weight = min(1,(frame-41)/4,(65-frame)/5)
                # A short rail slide, interrupted by alternating changes of grip.
                rail_y = .40 if side == 'left' else -.38
                rise = .75
                target = Vector((1.46,rail_y,min(5.52,master.location.z+rise)))
            if frame >= 101:
                weight = min(1,(frame-101)/6)
                target = chair.matrix_world @ Vector((.60 if side=='right' else -.60,.045,3.10))
            wanted = hand.lerp(target,max(0,weight))
            elbow_direction = elbow - (shoulder+hand)*.5
            if elbow_direction.length < .01:
                elbow_direction = master.matrix_world.to_quaternion() @ Vector((1 if side=='right' else -1,0,0))
            pole = elbow + elbow_direction.normalized()*.5
            targets[frame][side] = {'weight':weight,'point':list(wanted),
                                  'reach':(wanted-shoulder).length}
            for kind,point in [('shoulder',shoulder),('hand',wanted),('pole',pole)]:
                obj = ctrl[side][kind]
                obj.location = point
                obj.keyframe_insert(data_path='location',frame=frame)

    # Retain the native solver and controls in the scene; bake only contact spans
    # to the original rig so standard player animation exporters can read them.
    errors = []
    previous = {}
    for frame in range(145):
        scene.frame_set(frame)
        deps = bpy.context.evaluated_depsgraph_get()
        evaluated = rig.evaluated_get(deps)
        for side in ('right','left'):
            contact = targets[frame][side]
            if contact['weight'] <= 0: continue
            solved_upper = evaluated.pose.bones[side]
            solved_lower = evaluated.pose.bones[side+'_lower']
            u = (solved_upper.tail-solved_upper.head).normalized()
            v = (solved_lower.tail-solved_lower.head).normalized()
            # Retarget Blender's freely rotating IK joints to the upstream rig's
            # one-axis elbow. Preserve the solved elbow/hand plane by moving the
            # twist into the shoulder. The legacy elbow has a 1-pixel side offset.
            rest_u = Vector((.25 if side=='right' else -.25,0,-1)).normalized()
            delta = Vector(contact['point'])-solved_upper.head
            a,b,length = .05859375,.234375,.3515625
            cosine = max(-1,min(1,(delta.length_squared-a*a-b*b-length*length)/(2*b*length)))
            bend = math.acos(cosine)
            rest_hand = Vector((a if side=='right' else -a,length*math.sin(bend),-b-length*math.cos(bend)))
            def basis(a,b):
                second = b-a*a.dot(b)
                if second.length < 1e-6: second = a.cross(Vector((0,1,0)))
                second.normalize()
                return Matrix((a,second,a.cross(second))).transposed()
            desired = (basis(delta.normalized(),u) @ basis(rest_hand.normalized(),rest_u).transposed()).to_quaternion()
            upper,lower = player[side+'Arm'],player[side+'Arm_bend']
            parent = upper.parent.matrix_world @ upper.matrix_parent_inverse
            local = parent.to_quaternion().inverted() @ desired
            upper.rotation_euler = local.to_euler('XYZ',previous.get(upper.name,upper.rotation_euler))
            previous[upper.name] = upper.rotation_euler.copy()
            lower.rotation_euler = (bend,0,0)
            for obj in (upper,lower): obj.keyframe_insert(data_path='rotation_euler',frame=frame)
            bpy.context.view_layer.update()
            actual = player[side+'Arm_bend'].matrix_world @ Vector((0,0,-1.5))
            errors.append({'frame':frame,'hand':side,'weight':contact['weight'],
                           'distance':(actual-Vector(contact['point'])).length,
                           'reach':contact['reach']})
    controls.hide_render = True
    return errors


def bake_feet(scene, player, master, empty):
    """Explicit planted footsteps and a brief rail slide, solved by Blender IK."""
    floor = 1.33125
    steps = {
        'right':[(67,(.72,.32,1.70)),(69,(.62,.32,floor)),(70,(.62,.32,floor)),
                 (72,(.44,.90,floor+.28)),(75,(.25,1.60,floor)),(77,(.25,1.60,floor)),
                 (80,(.20,2.20,floor+.30)),(83,(.16,2.80,floor)),(85,(.16,2.80,floor)),
                 (88,(.15,3.34,1.91)),(91,(.15,3.90,1.70)),(93,(.15,3.90,1.70))],
        'left':[(67,(.43,.50,1.66)),(69,(.35,.95,floor)),(73,(.35,.95,floor)),
                (76,(.10,1.50,floor+.28)),(79,(-.15,2.20,floor)),(81,(-.15,2.20,floor)),
                (84,(-.15,2.70,floor+.27)),(87,(-.15,3.25,floor)),(89,(-.15,3.25,floor)),
                (92,(-.15,3.64,1.98)),(95,(-.15,3.90,1.70))]
    }
    armdata = bpy.data.armatures.new('Foot contact solver')
    rig = bpy.data.objects.new('Foot contact solver',armdata)
    scene.collection.objects.link(rig)
    bpy.context.view_layer.objects.active = rig
    rig.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    length = .3515625
    for side in steps:
        upper = armdata.edit_bones.new(side)
        upper.head,upper.tail = (0,0,0),(0,0,-length)
        lower = armdata.edit_bones.new(side+'_lower')
        lower.head,lower.tail = upper.tail,(0,0,-2*length)
        lower.parent,lower.use_connect = upper,True
    bpy.ops.object.mode_set(mode='OBJECT')
    controls = {}
    for side,keys in steps.items():
        hip,foot,pole = [empty(side+' '+name+' — foot contact') for name in ('hip','foot','knee')]
        controls[side] = hip,foot,pole
        for frame,point in keys:
            foot.location = point
            foot.keyframe_insert(data_path='location',frame=frame)
        rig.pose.bones[side].constraints.new('COPY_LOCATION').target = hip
        ik = rig.pose.bones[side+'_lower'].constraints.new('IK')
        ik.target,ik.pole_target = foot,pole
        ik.chain_count,ik.use_stretch = 2,False
        rig.pose.bones[side].ik_stretch = rig.pose.bones[side+'_lower'].ik_stretch = 0
    targets = {}
    for frame in range(44,94):
        scene.frame_set(frame)
        targets[frame] = {}
        for side in steps:
            upper,lower = player[side+'Leg'],player[side+'Leg_bend']
            hip,foot,pole = controls[side]
            start = upper.matrix_world.translation.copy()
            free = lower.matrix_world @ Vector((0,0,-1.5))
            weight = 0
            target = free.copy()
            if frame <= 64:
                weight = min(1,(frame-44)/3,(64-frame)/4)
                target = Vector((1.44,.10 if side=='left' else -.16,master.location.z-.32))
            elif frame >= 67:
                weight = min(1,(frame-67)/2,(94-frame)/3)
                target = foot.location.copy()
            wanted = free.lerp(target,max(0,weight))
            forward = master.rotation_euler.to_quaternion() @ Vector((0,1,0))
            targets[frame][side] = (start,wanted,weight)
            for obj,point in [(hip,start),(foot,wanted),(pole,start+forward*.8+Vector((0,0,-.2)))]:
                obj.location = point
                obj.keyframe_insert(data_path='location',frame=frame)
    errors = []
    previous = {}
    for frame,items in targets.items():
        scene.frame_set(frame)
        solved = rig.evaluated_get(bpy.context.evaluated_depsgraph_get())
        for side,(start,target,weight) in items.items():
            if weight <= 0: continue
            delta = target-start
            bend = -2*math.acos(max(0,min(1,delta.length/(2*length))))
            rest_hand = Vector((0,length*math.sin(bend),-length-length*math.cos(bend)))
            u = (solved.pose.bones[side].tail-solved.pose.bones[side].head).normalized()
            def basis(a,b):
                second = b-a*a.dot(b)
                if second.length < 1e-6: second = a.cross(Vector((1,0,0)))
                second.normalize()
                return Matrix((a,second,a.cross(second))).transposed()
            desired = (basis(delta.normalized(),u) @ basis(rest_hand.normalized(),Vector((0,0,-1))).transposed()).to_quaternion()
            upper,lower = player[side+'Leg'],player[side+'Leg_bend']
            parent = upper.parent.matrix_world @ upper.matrix_parent_inverse
            local = parent.to_quaternion().inverted() @ desired
            upper.rotation_euler = local.to_euler('XYZ',previous.get(side,upper.rotation_euler))
            previous[side] = upper.rotation_euler.copy()
            lower.rotation_euler = (bend,0,0)
            for obj in (upper,lower): obj.keyframe_insert(data_path='rotation_euler',frame=frame)
            bpy.context.view_layer.update()
            actual = lower.matrix_world @ Vector((0,0,-1.5))
            errors.append({'frame':frame,'foot':side,'weight':weight,'error':(actual-target).length})
    return errors
