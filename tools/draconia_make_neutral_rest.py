"""Create a neutral arms-down rest rig and keep the wand as a hidden prop.

blender -b --python-exit-code 1 --python tools/draconia_make_neutral_rest.py -- INPUT.blend OUTPUT.blend

The source model has a short, bent wand forearm in its rest pose. Pose it down,
rebake the affected skinned meshes, and apply that pose as the new armature
rest. Gaze shape keys, garment controls and unrelated meshes are left intact.
The wand becomes a hidden rigid prop with a switchable hand constraint; no
pickup animation is created by this script.
"""
import bpy
import sys
from pathlib import Path
from mathutils import Matrix, Vector

RIG = 'Draconia Body Rig - experimental'
AFFECTED = ('Arms', 'Cloak', 'Jacket Hands', 'Wand')


def align_bone(rig, name, direction):
    bone = rig.pose.bones[name]
    bone.rotation_mode = 'QUATERNION'
    current = (rig.matrix_world.to_3x3() @ (bone.tail-bone.head)).normalized()
    world = (rig.matrix_world @ bone.matrix).to_quaternion()
    world = current.rotation_difference(direction.normalized()) @ world
    rotation = (rig.matrix_world.to_quaternion().inverted() @ world).normalized()
    bone.matrix = Matrix.LocRotScale(bone.head.copy(), rotation, Vector((1, 1, 1)))
    bpy.context.view_layer.update()


def geometry(names):
    graph = bpy.context.evaluated_depsgraph_get()
    result = {}
    for name in names:
        obj = bpy.data.objects[name].evaluated_get(graph)
        mesh = obj.to_mesh()
        result[name] = [obj.matrix_world @ vertex.co for vertex in mesh.vertices]
        obj.to_mesh_clear()
    return result


def centre(name):
    points = geometry([name])[name]
    return sum(points, Vector()) / len(points)


def main():
    args = sys.argv[sys.argv.index('--')+1:]
    if len(args) != 2:
        raise ValueError('Expected INPUT.blend OUTPUT.blend')
    source, output = (Path(p).resolve() for p in args)
    if source == output or output.exists():
        raise ValueError('Output must be a new file')
    bpy.ops.wm.open_mainfile(filepath=str(source))
    rig = bpy.data.objects[RIG]
    wand = bpy.data.objects['Wand']
    if rig.animation_data and rig.animation_data.action:
        raise ValueError('Expected the unanimated clothing master')
    if any(bpy.data.objects[n].data.shape_keys for n in AFFECTED):
        raise ValueError('Affected meshes must not have shape keys')
    if any(m.type not in ('ARMATURE', 'CORRECTIVE_SMOOTH') for n in AFFECTED for m in bpy.data.objects[n].modifiers):
        raise ValueError('Unexpected modifier on an affected mesh')
    wand.hide_set(False)
    wand.hide_render = True
    bpy.context.view_layer.update()
    other = rig.pose.bones['Forearm.R']
    direction = rig.matrix_world.to_3x3() @ (other.tail-other.head)
    direction.x *= -1
    align_bone(rig, 'Forearm.L', direction)
    # Compensate for the original foreshortened, bent forearm. The scale is
    # baked into the new rest shape; the final pose bone scale is one.
    rig.pose.bones['Forearm.L'].scale.y = 2.1
    bpy.context.view_layer.update()
    other = rig.pose.bones['Hand.R']
    direction = rig.matrix_world.to_3x3() @ (other.tail-other.head)
    direction.x *= -1
    align_bone(rig, 'Hand.L', direction)
    bpy.context.view_layer.update()
    names = [o.name for o in bpy.data.objects if o.type == 'MESH']
    reference = geometry(names)
    graph = bpy.context.evaluated_depsgraph_get()
    for name in AFFECTED:
        obj = bpy.data.objects[name]
        arm = next(m for m in obj.modifiers if m.type == 'ARMATURE' and m.object == rig)
        arm_settings = {key: getattr(arm, key) for key in ('use_deform_preserve_volume', 'vertex_group', 'invert_vertex_group')}
        smooth = next((m for m in obj.modifiers if m.type == 'CORRECTIVE_SMOOTH'), None)
        keys = ('factor', 'iterations', 'scale', 'rest_source', 'smooth_type', 'invert_vertex_group', 'vertex_group', 'use_only_smooth', 'use_pin_boundary')
        smooth_settings = {key: getattr(smooth, key) for key in keys} if smooth else None
        evaluated = obj.evaluated_get(graph)
        mesh = bpy.data.meshes.new_from_object(evaluated, preserve_all_data_layers=True, depsgraph=graph)
        for modifier in list(obj.modifiers):
            obj.modifiers.remove(modifier)
        obj.data = mesh
        arm = obj.modifiers.new('Body rig skin', 'ARMATURE')
        arm.object = rig
        for key, value in arm_settings.items():
            setattr(arm, key, value)
        if smooth_settings:
            smooth = obj.modifiers.new('Sleeve corrective smoothing', 'CORRECTIVE_SMOOTH')
            for key, value in smooth_settings.items():
                setattr(smooth, key, value)
    bpy.ops.object.select_all(action='DESELECT')
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode='POSE')
    bpy.ops.pose.armature_apply(selected=False)
    bpy.ops.object.mode_set(mode='OBJECT')
    bpy.context.view_layer.update()
    result = geometry(names)
    assert all(len(reference[name]) == len(result[name]) for name in names)
    max_delta = max((x-y).length for name in names for x, y in zip(reference[name], result[name]))
    if max_delta > 1e-5:
        raise ValueError(f'Rest-pose bake changed evaluated geometry: {max_delta}')
    # A rigid prop can later be animated independently and attached to the
    # hand by keyframing this constraint. It is hidden in the default scene.
    before = centre('Wand')
    for modifier in list(wand.modifiers):
        if modifier.type == 'ARMATURE':
            wand.modifiers.remove(modifier)
    attachment = wand.constraints.new('CHILD_OF')
    attachment.name = 'Wand hand attachment'
    attachment.target = rig
    attachment.subtarget = 'Hand.L'
    attachment.inverse_matrix = (rig.matrix_world @ rig.pose.bones['Hand.L'].matrix).inverted()
    attachment.influence = 1.0
    bpy.context.view_layer.update()
    if (centre('Wand') - before).length > 1e-5:
        raise ValueError('Wand jumped when converted into a prop')
    # The prop starts detached as well as hidden. Animators can key the
    # constraint influence when she eventually takes it from the cloak.
    attachment.influence = 0.0
    wand.hide_set(True)
    wand.hide_render = True
    bpy.ops.wm.save_as_mainfile(filepath=str(output))
    print('NEUTRAL_REST_BAKE_MAX_DELTA', max_delta, 'WAND_HIDDEN', wand.hide_get(), flush=True)

if __name__ == '__main__':
    main()
