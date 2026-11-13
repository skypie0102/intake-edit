"""Compare the original and edited Blender scenes in several sampled poses.

Run: blender -b --python tools/draconia_validate_cloak_controls.py -- INPUT.blend OUTPUT.blend
"""
import bpy
import math
import sys
from mathutils import Vector

RIG = 'Draconia Body Rig - experimental'
NAMES = ('CloakHem.L', 'CloakHem.C', 'CloakHem.R')
POSES = (
    {},
    {'Torso': (0, 0, 20), 'Thigh.L': (25, 0, 0), 'Shin.L': (-25, 0, 0)},
    {'UpperArm.R': (0, 0, -30), 'Forearm.R': (45, 0, 0)},
    {'UpperArm.L': (15, 0, 20), 'Forearm.L': (35, 0, 0), 'Hand.L': (0, 10, 0)},
)


def pose(rig, values):
    for bone in rig.pose.bones:
        bone.rotation_mode = 'XYZ'
        bone.rotation_euler = (0, 0, 0)
    for name, degrees in values.items():
        rig.pose.bones[name].rotation_euler = [math.radians(v) for v in degrees]
    bpy.context.view_layer.update()


def positions():
    graph = bpy.context.evaluated_depsgraph_get()
    result = {}
    for obj in bpy.data.objects:
        if obj.type != 'MESH':
            continue
        evaluated = obj.evaluated_get(graph)
        mesh = evaluated.to_mesh()
        result[obj.name] = [tuple(evaluated.matrix_world @ v.co) for v in mesh.vertices]
        evaluated.to_mesh_clear()
    return result


def compare(a, b, tolerance=1e-5):
    assert a.keys() == b.keys()
    maximum = 0.0
    for name in a:
        assert len(a[name]) == len(b[name]), name
        for p, q in zip(a[name], b[name]):
            maximum = max(maximum, (Vector(p) - Vector(q)).length)
    assert maximum < tolerance, maximum
    return maximum


def main():
    args = sys.argv[sys.argv.index('--') + 1:]
    if len(args) != 2:
        raise ValueError('Expected INPUT.blend OUTPUT.blend')
    bpy.ops.wm.open_mainfile(filepath=args[0])
    rig = bpy.data.objects[RIG]
    baseline = []
    for p in POSES:
        pose(rig, p)
        baseline.append(positions())
    bpy.ops.wm.open_mainfile(filepath=args[1])
    rig = bpy.data.objects[RIG]
    assert all(rig.data.bones[n].parent.name == 'Hips' for n in NAMES)
    deform = {b.name for b in rig.data.bones if b.use_deform}
    for obj in bpy.data.objects:
        if obj.type != 'MESH':
            continue
        indices = {g.index for g in obj.vertex_groups if g.name in deform}
        for v in obj.data.vertices:
            total = sum(g.weight for g in v.groups if g.group in indices)
            assert abs(total - 1.0) < 1e-4, (obj.name, v.index, total)
    for p, expected in zip(POSES, baseline):
        pose(rig, p)
        print('MAX_POSE_DIFFERENCE', compare(expected, positions()))
    pose(rig, {})
    neutral = positions()
    pose(rig, {n: (15, 0, 0) for n in NAMES})
    moved = positions()
    for name in neutral:
        distances = [(Vector(a) - Vector(b)).length for a, b in zip(neutral[name], moved[name])]
        if name == 'Cloak':
            assert max(distances) > 0.01
            print('MAX_CLOAK_MOVEMENT', max(distances))
        else:
            assert max(distances, default=0) < 1e-5, name
    skirt_names = ('SkirtHem.L', 'SkirtHem.C', 'SkirtHem.R')
    if all(n in rig.data.bones for n in skirt_names):
        assert all(rig.data.bones[n].parent.name == 'Hips' for n in skirt_names)
        pose(rig, {n: (10, 0, 0) for n in skirt_names})
        moved_skirt = positions()
        for name in neutral:
            distances = [(Vector(a) - Vector(b)).length for a, b in zip(neutral[name], moved_skirt[name])]
            if name == 'Skirt':
                assert max(distances) > 0.005
                print('MAX_SKIRT_MOVEMENT', max(distances))
            else:
                assert max(distances, default=0) < 1e-5, name
    for key in ('Gaze_X', 'Gaze_Z', 'Brow_Raise', 'Smile'):
        assert key in rig
    rig['Gaze_X'] = 1.0
    rig.update_tag()
    bpy.context.view_layer.update()
    assert abs(bpy.data.objects['Circle'].data.shape_keys.key_blocks['Gaze_X_1'].value - 1) < 0.001
    print('CLOAK_CONTROL_CHECKS_OK')


if __name__ == '__main__':
    main()
