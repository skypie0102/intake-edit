"""Check the separate hand study's weights, joints and unaffected meshes.

blender -b --python-exit-code 1 --python tools/draconia_validate_hand_study.py -- BEFORE.blend STUDY.blend
These tests do not certify wrist transitions or collision-free grasping.
"""
import math
import sys

import bpy

paths = sys.argv[sys.argv.index('--') + 1:]
if len(paths) != 2:
    raise ValueError('Expected BEFORE.blend STUDY.blend')


def coordinates(obj):
    evaluated = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = evaluated.to_mesh()
    result = [evaluated.matrix_world @ vertex.co for vertex in mesh.vertices]
    evaluated.to_mesh_clear()
    return result


def snapshot():
    bpy.context.view_layer.update()
    return {obj.name: coordinates(obj) for obj in bpy.data.objects
            if obj.type == 'MESH' and not obj.name.startswith('Articulated hand study ')}


bpy.ops.wm.open_mainfile(filepath=paths[0])
before = snapshot()
bpy.ops.wm.open_mainfile(filepath=paths[1])
after = snapshot()
assert before.keys() == after.keys()
delta = max((a - b).length for name in before for a, b in zip(before[name], after[name]))
assert delta < 1e-5, delta
rig = bpy.data.objects['Draconia Body Rig - experimental']
original = bpy.data.objects['Arms']
assert original.hide_render and original.hide_get() and bpy.data.objects['Wand'].hide_render
for side in ('L', 'R'):
    obj = bpy.data.objects['Articulated hand study ' + side]
    assert not obj.hide_render and any(m.type == 'ARMATURE' and m.object == rig for m in obj.modifiers)
    deform = {bone.name for bone in rig.data.bones if bone.use_deform}
    deform_ids = {group.index for group in obj.vertex_groups if group.name in deform}
    for vertex in obj.data.vertices:
        assert abs(sum(weight.weight for weight in vertex.groups if weight.group in deform_ids) - 1) < 1e-4
    for label in ('Index', 'Middle', 'Ring', 'Pinky', 'Thumb'):
        first = rig.pose.bones[f'{label}.01.{side}']
        second = rig.pose.bones[f'{label}.02.{side}']
        assert first.parent.name == 'Hand.' + side and second.parent == first
        target_id = obj.vertex_groups[second.name].index
        other_name = 'Middle' if label == 'Index' else 'Index'
        other_id = obj.vertex_groups[f'{other_name}.02.{side}'].index
        target = [v.index for v in obj.data.vertices if any(w.group == target_id and w.weight > .99 for w in v.groups)]
        other = [v.index for v in obj.data.vertices if any(w.group == other_id and w.weight > .99 for w in v.groups)]
        assert target and other
        base = coordinates(obj)
        first.rotation_mode = 'XYZ'
        first.rotation_euler.x = math.radians(35)
        bpy.context.view_layer.update()
        posed = coordinates(obj)
        movement = max((posed[i] - base[i]).length for i in target)
        other_drift = max((posed[i] - base[i]).length for i in other)
        assert movement > .004 and other_drift < 1e-6, (side, label, movement, other_drift)
        first.rotation_euler.x = 0
        bpy.context.view_layer.update()
        print('FINGER_INDEPENDENT', side, label, 'tip movement', movement, flush=True)
print('HAND_STUDY_UNCHANGED_MESH_MAX_DELTA', delta, 'HAND_STUDY_CHECKS_OK', flush=True)
