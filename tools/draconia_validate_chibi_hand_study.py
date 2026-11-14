"""Check the chibi hand topology, skinning and sampled control poses.

blender -b --python-exit-code 1 --python tools/draconia_validate_chibi_hand_study.py -- BEFORE.blend CHIBI.blend
Self-intersection checks cover only the stated poses and do not certify every
possible motion or wand contact.
"""
import math
import sys

import bmesh
import bpy
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from draconia_hand_intersections import intersection_segments

paths = sys.argv[sys.argv.index('--') + 1:]
if len(paths) != 2:
    raise ValueError('Expected BEFORE.blend JOINED.blend')
prefixes = ('Articulated hand study ', 'Chibi hand source ')


def coordinates(obj):
    evaluated = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = evaluated.to_mesh()
    result = [evaluated.matrix_world @ vertex.co for vertex in mesh.vertices]
    evaluated.to_mesh_clear()
    return result


def snapshot():
    bpy.context.view_layer.update()
    return {obj.name: coordinates(obj) for obj in bpy.data.objects
            if obj.type == 'MESH' and not obj.name.startswith(prefixes)}
bpy.ops.wm.open_mainfile(filepath=paths[0])
before = snapshot()
bpy.ops.wm.open_mainfile(filepath=paths[1])
after = snapshot()
assert before.keys() == after.keys()
delta = max((a - b).length for name in before for a, b in zip(before[name], after[name]))
assert delta < 1e-5, delta
rig = bpy.data.objects['Draconia Body Rig - experimental']
assert bpy.data.objects['Arms'].hide_render and bpy.data.objects['Wand'].hide_render
assert rig['JacketClearance'] == 1.0
for side in ('L', 'R'):
    obj = bpy.data.objects['Articulated hand study ' + side]
    assert not obj.hide_render and any(m.type == 'ARMATURE' and m.object == rig for m in obj.modifiers)
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    assert not any(not edge.is_manifold for edge in bm.edges)
    remaining = set(bm.verts)
    components = 0
    while remaining:
        components += 1
        stack = [remaining.pop()]
        while stack:
            vertex = stack.pop()
            for edge in vertex.link_edges:
                other = edge.other_vert(vertex)
                if other in remaining:
                    remaining.remove(other)
                    stack.append(other)
    bm.free()
    assert components == 1, (side, components)
    deform = {bone.name for bone in rig.data.bones if bone.use_deform}
    ids = {group.index for group in obj.vertex_groups if group.name in deform}
    for vertex in obj.data.vertices:
        assert abs(sum(weight.weight for weight in vertex.groups if weight.group in ids) - 1) < 1e-4
    forearm = obj.vertex_groups['Forearm.' + side].index
    assert any(any(weight.group == forearm and weight.weight > .99 for weight in v.groups)
               for v in obj.data.vertices)
    for label in ('Fingers', 'Thumb'):
        first = rig.pose.bones[f'{label}.01.{side}']
        second = rig.pose.bones[f'{label}.02.{side}']
        assert first.parent.name == 'Hand.' + side and second.parent == first
        target_id = obj.vertex_groups[second.name].index
        other_label = 'Thumb' if label == 'Fingers' else 'Fingers'
        other_id = obj.vertex_groups[f'{other_label}.02.{side}'].index
        target = [v.index for v in obj.data.vertices if any(w.group == target_id and w.weight > .99 for w in v.groups)]
        other = [v.index for v in obj.data.vertices if any(w.group == other_id and w.weight > .99 for w in v.groups)]
        assert target and other
        base = coordinates(obj)
        first.rotation_mode = 'XYZ'
        first.rotation_euler.x = math.radians(-35 if label == 'Thumb' else 35)
        bpy.context.view_layer.update()
        posed = coordinates(obj)
        movement = max((posed[i] - base[i]).length for i in target)
        other_drift = max((posed[i] - base[i]).length for i in other)
        assert movement > .004 and other_drift < 1e-6, (side, label, movement, other_drift)
        first.rotation_euler.x = 0
        bpy.context.view_layer.update()
        print('CHIBI_CONTROL_INDEPENDENT', side, label, movement, flush=True)

    for mode in ('open', 'Fingers', 'Thumb', 'all'):
        for label in ('Fingers', 'Thumb'):
            for segment in (1, 2):
                bone = rig.pose.bones[f'{label}.{segment:02}.{side}']
                bone.rotation_mode = 'XYZ'
                active = mode == label or (mode == 'all')
                degrees = (-25 if label == 'Thumb' else (30 if segment == 1 else 35)) if active else 0
                bone.rotation_euler.x = math.radians(degrees)
        bpy.context.view_layer.update()
        lengths = intersection_segments(obj)
        maximum = max(lengths, default=0)
        assert maximum < .00075, (side, mode, maximum)
        print('CHIBI_HAND_SELF_INTERSECTION', side, mode, 'max segment', maximum, flush=True)
    for label in ('Fingers', 'Thumb'):
        for segment in (1, 2):
            rig.pose.bones[f'{label}.{segment:02}.{side}'].rotation_euler.x = 0
    bpy.context.view_layer.update()
print('CHIBI_HAND_UNCHANGED_MESH_MAX_DELTA', delta, 'CHIBI_HAND_CHECKS_OK', flush=True)
