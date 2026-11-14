"""Retarget the CC0 Quaternius/KinemIQ dance to the Draconia study rig.

blender -b --python-exit-code 1 --python tools/draconia_retarget_cc0_dance.py -- MODEL.blend ACTION.fbx OUTPUT.blend

The result is a diagnostic: source rotations are transferred relative to the
source's first pose and Draconia's asymmetric rest pose. Root translation is
scaled, feet are prevented from dropping below their initial height, and the
last six frames blend back to the first pose for preview looping.
"""
import bpy
import hashlib
import sys
from pathlib import Path
from mathutils import Matrix, Vector

SOURCE_SHA256 = 'e39bfbe4f64583c34d0a40672812ff2ce75739ea39a23e96057451314b423e27'
MAPPING = (
    ('pelvis', 'Hips'), ('spine_03', 'Torso'), ('neck_01', 'Neck'), ('Head', 'Head'),
    # The source's left side is +X; Draconia's .R bones are on +X.
    ('upperarm_l', 'UpperArm.R'), ('lowerarm_l', 'Forearm.R'), ('hand_l', 'Hand.R'),
    ('upperarm_r', 'UpperArm.L'), ('lowerarm_r', 'Forearm.L'), ('hand_r', 'Hand.L'),
    ('thigh_l', 'Thigh.R'), ('calf_l', 'Shin.R'), ('foot_l', 'Foot.R'),
    ('thigh_r', 'Thigh.L'), ('calf_r', 'Shin.L'), ('foot_r', 'Foot.L'),
)


def lowest_leg_z():
    obj = bpy.data.objects['Legs'].evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    result = min((obj.matrix_world @ vertex.co).z for vertex in mesh.vertices)
    obj.to_mesh_clear()
    return result


def main():
    args = sys.argv[sys.argv.index('--') + 1:]
    if len(args) != 3:
        raise ValueError('Expected MODEL.blend ACTION.fbx OUTPUT.blend')
    model, animation, output = (Path(p).resolve() for p in args)
    if output in (model, animation) or output.exists():
        raise ValueError('Output must be a new file, separate from the inputs')
    if hashlib.sha256(animation.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise ValueError('Expected the published KinemIQ Action_dance.fbx file')
    bpy.ops.wm.open_mainfile(filepath=str(model))
    scene = bpy.context.scene
    target = bpy.data.objects['Draconia Body Rig - experimental']
    if target.animation_data and target.animation_data.action:
        raise ValueError('The input rig already has an active animation')
    for _, name in MAPPING:
        if name not in target.pose.bones:
            raise ValueError(f'Missing target bone: {name}')
    before = {obj.as_pointer() for obj in bpy.data.objects}
    bpy.ops.import_scene.fbx(filepath=str(animation))
    imported = [obj for obj in bpy.data.objects if obj.as_pointer() not in before]
    sources = [obj for obj in imported if obj.type == 'ARMATURE']
    if len(sources) != 1:
        raise ValueError('Expected exactly one source armature')
    source = sources[0]
    source.name = 'CC0 Dance Source'
    if not source.animation_data or not source.animation_data.action:
        raise ValueError('The source armature has no action')
    for name, _ in MAPPING:
        if name not in source.pose.bones:
            raise ValueError(f'Missing source bone: {name}')
    scene.render.fps = 30
    scene.frame_set(1)
    bpy.context.view_layer.update()
    reference = {name: (source.matrix_world @ source.pose.bones[name].matrix).to_quaternion()
                 for name, _ in MAPPING}
    source_hip = (source.matrix_world @ source.pose.bones['pelvis'].head).copy()
    target_rest = {name: (target.matrix_world @ target.data.bones[name].matrix_local).to_quaternion()
                   for _, name in MAPPING}
    root = target.pose.bones['Root']
    root_rest_inverse = target.data.bones['Root'].matrix_local.to_3x3().inverted()
    target_inverse = target.matrix_world.to_3x3().inverted()
    floor = lowest_leg_z()
    target.animation_data_create()
    target.animation_data.action = bpy.data.actions.new('CC0 Dance - retarget diagnostic')
    for bone in target.pose.bones:
        bone.rotation_mode = 'QUATERNION'
    previous = {}
    for frame in range(1, 32):
        scene.frame_set(frame)
        bpy.context.view_layer.update()
        hip = source.matrix_world @ source.pose.bones['pelvis'].head
        root.location = root_rest_inverse @ (target_inverse @ ((hip - source_hip) * .8))
        root.keyframe_insert(data_path='location', frame=frame)
        for source_name, target_name in MAPPING:
            source_rotation = (source.matrix_world @ source.pose.bones[source_name].matrix).to_quaternion()
            delta = source_rotation @ reference[source_name].inverted()
            world_rotation = delta @ target_rest[target_name]
            local_rotation = (target.matrix_world.to_quaternion().inverted() @ world_rotation).normalized()
            bone = target.pose.bones[target_name]
            bone.matrix = Matrix.LocRotScale(bone.head.copy(), local_rotation, Vector((1, 1, 1)))
            bpy.context.view_layer.update()
            q = bone.rotation_quaternion.copy()
            if target_name in previous and q.dot(previous[target_name]) < 0:
                q.negate()
                bone.rotation_quaternion = q
            previous[target_name] = q.copy()
            bone.keyframe_insert(data_path='rotation_quaternion', frame=frame)
    # Source end differs slightly from its first pose. Ease the final six frames
    # into the first pose to avoid a visible jump when the preview loops.
    scene.frame_set(1)
    bpy.context.view_layer.update()
    first_rotations = {name: target.pose.bones[name].rotation_quaternion.copy() for _, name in MAPPING}
    first_root = root.location.copy()
    for frame in range(1, 32):
        scene.frame_set(frame)
        bpy.context.view_layer.update()
        if frame > 25:
            t = (frame - 25) / 6
            t = t*t*(3-2*t)
            root.location = root.location.lerp(first_root, t)
            for _, name in MAPPING:
                bone = target.pose.bones[name]
                bone.rotation_quaternion = bone.rotation_quaternion.slerp(first_rotations[name], t)
                bone.keyframe_insert(data_path='rotation_quaternion', frame=frame)
            bpy.context.view_layer.update()
        # Keep the lowest boot at or above its neutral height. This is a simple
        # floor correction, not foot locking or a physical contact solver.
        lift = max(0.0, floor - lowest_leg_z())
        root.location += root_rest_inverse @ (target_inverse @ Vector((0, 0, lift)))
        root.keyframe_insert(data_path='location', frame=frame)
    for curve in target.animation_data.action.fcurves:
        for key in curve.keyframe_points:
            key.interpolation = 'LINEAR'
    for obj in imported:
        obj.hide_set(True)
        obj.hide_render = True
    scene.frame_start, scene.frame_end = 1, 30
    scene.frame_set(1)
    bpy.ops.wm.save_as_mainfile(filepath=str(output))
    print('RETARGETED', target.animation_data.action.name, 'FRAMES', 31, 'FPS', scene.render.fps)


if __name__ == '__main__':
    main()
