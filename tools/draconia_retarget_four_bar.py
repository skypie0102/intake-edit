"""Retarget the complete CC0 Innerscene four-bar dance to the study rig.

blender -b --python-exit-code 1 --python tools/draconia_retarget_four_bar.py -- MODEL.blend SOURCE.glb OUTPUT.blend
The source is 8 seconds at 24 fps (frames 0 through 192, endpoint repeated).
Each of its 192 unique frames is kept, with no shortened loop or end crossfade.
"""
import bpy
import hashlib
import sys
from pathlib import Path
from mathutils import Matrix, Vector

SOURCE_SHA256 = 'fcc9438c385bb8e5d5b23827501b0787713fed704734a745a307f6a61155e5fd'
MAPPING = (
    ('pelvis', 'Hips'), ('spine_03', 'Torso'), ('neck_01', 'Neck'), ('head', 'Head'),
    ('upperarm_l', 'UpperArm.R'), ('lowerarm_l', 'Forearm.R'), ('hand_l', 'Hand.R'),
    ('upperarm_r', 'UpperArm.L'), ('lowerarm_r', 'Forearm.L'), ('hand_r', 'Hand.L'),
    ('thigh_l', 'Thigh.R'), ('calf_l', 'Shin.R'), ('foot_l', 'Foot.R'),
    ('thigh_r', 'Thigh.L'), ('calf_r', 'Shin.L'), ('foot_r', 'Foot.L'),
)
# Segment direction takes the difference in the characters' rest poses into
# account, particularly Draconia's bent wand arm. The wand wrist is adjusted
# separately for visibility while preserving its original grip.
DIRECTION_BONES = {'UpperArm.L', 'Forearm.L', 'UpperArm.R', 'Forearm.R',
                   'Thigh.L', 'Shin.L', 'Thigh.R', 'Shin.R'}


def lowest_leg_z():
    obj = bpy.data.objects['Legs'].evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    value = min((obj.matrix_world @ v.co).z for v in mesh.vertices)
    obj.to_mesh_clear()
    return value


def main():
    args = sys.argv[sys.argv.index('--') + 1:]
    if len(args) != 3:
        raise ValueError('Expected MODEL.blend SOURCE.glb OUTPUT.blend')
    model, animation, output = (Path(p).resolve() for p in args)
    if output in (model, animation) or output.exists():
        raise ValueError('Output must be a new file')
    if hashlib.sha256(animation.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise ValueError('Unexpected source file')
    bpy.ops.wm.open_mainfile(filepath=str(model))
    scene = bpy.context.scene
    target = bpy.data.objects['Draconia Body Rig - experimental']
    if target.animation_data and target.animation_data.action:
        raise ValueError('Input rig already has an action')
    scene.render.fps = 24
    before = {o.as_pointer() for o in bpy.data.objects}
    bpy.ops.import_scene.gltf(filepath=str(animation))
    imported = [o for o in bpy.data.objects if o.as_pointer() not in before]
    source, = [o for o in imported if o.type == 'ARMATURE']
    source.name = 'CC0 Four Bar Source'
    assert tuple(source.animation_data.action.frame_range) == (0.0, 192.0)
    scene.render.fps = 24
    scene.frame_set(0)
    bpy.context.view_layer.update()
    source_rest = {s: (source.matrix_world @ source.data.bones[s].matrix_local).to_quaternion() for s, _ in MAPPING}
    target_rest = {t: (target.matrix_world @ target.data.bones[t].matrix_local).to_quaternion() for _, t in MAPPING}
    hip_at_start = (source.matrix_world @ source.pose.bones['pelvis'].head).copy()
    floor = lowest_leg_z()
    target.animation_data_create()
    target.animation_data.action = bpy.data.actions.new('Four-bar CC0 dance - full 8 seconds')
    root = target.pose.bones['Root']
    root_rest_inverse = target.data.bones['Root'].matrix_local.to_3x3().inverted()
    target_inverse = target.matrix_world.to_3x3().inverted()
    for bone in target.pose.bones:
        bone.rotation_mode = 'QUATERNION'
    previous = {}
    for source_frame in range(192):
        frame = source_frame + 1
        scene.frame_set(source_frame)
        bpy.context.view_layer.update()
        hip = source.matrix_world @ source.pose.bones['pelvis'].head
        root.location = root_rest_inverse @ (target_inverse @ ((hip - hip_at_start) * .8))
        for source_name, target_name in MAPPING:
            sb = source.pose.bones[source_name]
            bone = target.pose.bones[target_name]
            if target_name == 'Hand.L':
                # The source has no wand. Keep the authored grip and turn the
                # wrist so its tip is visible to the front preview camera.
                bone.rotation_quaternion = (1, 0, 0, 0)
                bpy.context.view_layer.update()
                wand = bpy.data.objects['Wand'].evaluated_get(bpy.context.evaluated_depsgraph_get())
                mesh = wand.to_mesh()
                hand_world = target.matrix_world @ bone.head
                tip = max((wand.matrix_world @ v.co for v in mesh.vertices), key=lambda p: (p - hand_world).length)
                current = (tip - hand_world).normalized()
                wand.to_mesh_clear()
                desired = Vector((-0.65, -0.45, 0.85)).normalized()
                world_rotation = current.rotation_difference(desired) @ (target.matrix_world @ bone.matrix).to_quaternion()
                local_rotation = (target.matrix_world.to_quaternion().inverted() @ world_rotation).normalized()
                bone.matrix = Matrix.LocRotScale(bone.head.copy(), local_rotation, Vector((1, 1, 1)))
                bpy.context.view_layer.update()
                bone.keyframe_insert(data_path='rotation_quaternion', frame=frame)
                continue
            src_rotation = (source.matrix_world @ sb.matrix).to_quaternion()
            world_rotation = src_rotation @ source_rest[source_name].inverted() @ target_rest[target_name]
            if target_name in DIRECTION_BONES:
                # Both skeletons use the same world axes. Align the actual bone
                # segment, retaining the transferred roll around that segment.
                src_direction = (source.matrix_world.to_3x3() @ (sb.tail - sb.head)).normalized()
                current_direction = world_rotation @ Vector((0, 1, 0))
                world_rotation = current_direction.rotation_difference(src_direction) @ world_rotation
            local_rotation = (target.matrix_world.to_quaternion().inverted() @ world_rotation).normalized()
            bone.matrix = Matrix.LocRotScale(bone.head.copy(), local_rotation, Vector((1, 1, 1)))
            bpy.context.view_layer.update()
            q = bone.rotation_quaternion.copy()
            if target_name in previous and q.dot(previous[target_name]) < 0:
                q.negate()
                bone.rotation_quaternion = q
            previous[target_name] = q.copy()
            bone.keyframe_insert(data_path='rotation_quaternion', frame=frame)
        # Simple floor penetration correction; no physical contact simulation.
        lift = max(0.0, floor - lowest_leg_z())
        root.location += root_rest_inverse @ (target_inverse @ Vector((0, 0, lift)))
        root.keyframe_insert(data_path='location', frame=frame)
        if frame % 24 == 0:
            print('BAKED',frame,flush=True)
    for curve in target.animation_data.action.fcurves:
        for key in curve.keyframe_points:
            key.interpolation = 'LINEAR'
    for obj in imported:
        obj.hide_set(True)
        obj.hide_render = True
    scene.frame_start, scene.frame_end = 1, 192
    scene.frame_set(1)
    bpy.ops.wm.save_as_mainfile(filepath=str(output))
    print('RETARGETED 192 FRAMES AT 24 FPS')

if __name__ == '__main__':
    main()
