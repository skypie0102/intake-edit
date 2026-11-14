"""Validate selected clothing, control, and attachment invariants.

blender -b --python-exit-code 1 --python tools/draconia_validate_clothing_rig.py -- BEFORE.blend AFTER.blend
These checks do not certify every pose, collision, or appearance.
"""
import bpy
import math
import sys
from pathlib import Path
from mathutils import Vector
from mathutils.bvhtree import BVHTree

sys.path.insert(0, str(Path(__file__).resolve().parent))
from draconia_validate_sleeve_edges import RIG, pose, geometry, check_equal


def tree(mesh):
    return BVHTree.FromPolygons(*mesh, all_triangles=False, epsilon=1e-6)


def main():
    args = sys.argv[sys.argv.index('--') + 1:]
    if len(args) != 2:
        raise ValueError('Expected BEFORE.blend AFTER.blend')
    samples = ({}, {'Torso': (0, 0, 20)},
               {'UpperArm.L': (0, 0, -40), 'Forearm.L': (45, 0, 0)},
               {'Thigh.R': (45, 0, 0), 'Shin.R': (-20, 0, 0)})
    all_geometry = []
    for path in args:
        bpy.ops.wm.open_mainfile(filepath=path)
        rig = bpy.data.objects[RIG]
        frames = []
        for values in samples:
            pose(rig, values)
            frames.append(geometry())
        all_geometry.append(frames)
    for i, (before, after) in enumerate(zip(*all_geometry)):
        print('MAX_UNCHANGED_MESH_DIFFERENCE', i, check_equal(before, after, exclude=('Cloak', 'Skirt')))
        if i == 0:
            print('MAX_NEUTRAL_DIFFERENCE', check_equal(before, after))
    rig = bpy.data.objects[RIG]
    file_images = [image for image in bpy.data.images if image.source == 'FILE']
    assert file_images and all(image.packed_file for image in file_images)
    print('PACKED_TEXTURES', len(file_images))
    deform_names = {b.name for b in rig.data.bones if b.use_deform}
    for obj in bpy.data.objects:
        if obj.type != 'MESH':
            continue
        ids = {g.index for g in obj.vertex_groups if g.name in deform_names}
        for vertex in obj.data.vertices:
            total = sum(w.weight for w in vertex.groups if w.group in ids)
            assert abs(total - 1.0) < 1e-4, (obj.name, vertex.index, total)
    assert rig.pose.bones['CloakHem.C'].parent.name == 'CloakFollow.C'
    skirt_center = rig.pose.bones['SkirtFollow.C']
    assert not skirt_center.bone.use_deform and skirt_center.parent.name == 'Hips'
    assert rig.pose.bones['SkirtHem.C'].parent == skirt_center
    for side in ('L', 'R'):
        con = skirt_center.constraints[f'Thigh follow {side}']
        assert con.target == rig and con.subtarget == f'Thigh.{side}'
        assert abs(con.influence - .15) < 1e-5
    for name in ('SkirtFront.C', 'SkirtBack.C'):
        assert rig.pose.bones[name].parent.name == 'SkirtHem.C'
        assert rig.data.bones[name].use_deform
    for l, r in ((0, 0), (40, 0), (0, -40), (40, 40), (40, -40), (-30, -30)):
        pose(rig, {'Thigh.L': (l, 0, 0), 'Thigh.R': (r, 0, 0)})
        evaluated = rig.evaluated_get(bpy.context.evaluated_depsgraph_get())
        front = math.degrees(evaluated.pose.bones['SkirtFront.C'].rotation_euler.x)
        back = math.degrees(evaluated.pose.bones['SkirtBack.C'].rotation_euler.x)
        assert abs(front - (.3*min(l, r, 0) - .15*(l+r))) < 1e-3
        assert abs(back - (.3*max(l, r, 0) - .15*(l+r))) < 1e-3
    for axis in (0, 2):
        for l, r in ((0, 0), (40, 0), (0, -40), (40, 40), (40, -40), (-30, -30)):
            left, right = [0, 0, 0], [0, 0, 0]
            left[axis], right[axis] = l, r
            pose(rig, {'Thigh.L': left, 'Thigh.R': right})
            evaluated = rig.evaluated_get(bpy.context.evaluated_depsgraph_get())
            actual = math.degrees(evaluated.pose.bones['CloakFollow.C'].rotation_euler[axis])
            if axis == 0:
                expected = .7*max(l, r, 0) + .3*min(max(l, 0), max(r, 0)) + min(max(l, r), 0)
            else:
                expected = .7*(l+r) - .4*min(max(l, 0), max(r, 0)) + .4*min(max(-l, 0), max(-r, 0))
            assert abs(actual - expected) < 1e-3, (axis, l, r, actual, expected)
    for side, outward in (('L', 1), ('R', -1)):
        skirt = rig.pose.bones[f'SkirtFollow.{side}'].constraints['Thigh follow']
        assert abs(skirt.influence - .65) < 1e-5
        for angle, expected in ((-30*outward, .3), (0, .3), (5*outward, .45), (30*outward, .6)):
            pose(rig, {f'Thigh.{side}': (0, 0, angle)})
            evaluated = rig.evaluated_get(bpy.context.evaluated_depsgraph_get())
            follow = evaluated.pose.bones[f'CloakFollow.{side}']
            assert not follow.constraints['Thigh follow'].use_z
            assert abs(follow.constraints['Thigh follow'].influence - .7) < 1e-5
            actual = follow.constraints['Sideways thigh follow'].influence
            assert abs(actual - expected) < 1e-3, (side, angle, actual, expected)
    pose(rig, {})
    neutral = geometry()
    pose(rig, {'CloakHem.C': (10, 0, 0)})
    moved = geometry()
    distances = [(a-b).length for a, b in zip(neutral['Cloak'][0], moved['Cloak'][0])]
    assert max(distances) > .005
    print('MANUAL_CENTER_MAX_MOVEMENT', max(distances))
    pose(rig, {'SkirtHem.C': (10, 0, 0)})
    moved = geometry()
    distances = [(a-b).length for a, b in zip(neutral['Skirt'][0], moved['Skirt'][0])]
    assert max(distances) > .005
    print('MANUAL_SKIRT_CENTER_MAX_MOVEMENT', max(distances))
    pose(rig, {})
    rig['Gaze_X'] = 1.0
    rig.update_tag()
    bpy.context.view_layer.update()
    assert abs(bpy.data.objects['Circle'].data.shape_keys.key_blocks['Gaze_X_1'].value - 1) < .001
    rig['Gaze_X'] = 0.0
    rig.update_tag()
    points = []
    for values in samples:
        pose(rig, values)
        vertices = geometry()['Wand'][0]
        centre = sum(vertices, Vector()) / len(vertices)
        hand = rig.matrix_world @ rig.pose.bones['Hand.L'].matrix
        points.append(hand.inverted() @ centre)
    drift = max((p - points[0]).length for p in points)
    assert drift < 1e-5, drift
    print('WAND_LOCAL_DRIFT', drift)
    pairs = [0, 0]
    for frame in range(24):
        s = math.sin(2 * math.pi * frame / 24)
        pose(rig, {'Thigh.R': (50*s, 0, 0), 'Shin.R': (-15*max(0, s), 0, 0)})
        data = geometry()
        legs = tree(data['Legs'])
        pairs[0] += len(tree(data['Cloak']).overlap(legs))
        pairs[1] += len(tree(data['Skirt']).overlap(legs))
    assert pairs == [0, 0], pairs
    print('STEP_CLOAK_SKIRT_PAIRS', pairs)
    cross_pairs = [0, 0]
    for frame in range(24):
        s = math.sin(2 * math.pi * frame / 24)
        pose(rig, {'Thigh.L': (18*s, 0, 25*s), 'Thigh.R': (-18*s, 0, -25*s)})
        data = geometry()
        legs = tree(data['Legs'])
        cross_pairs[0] += len(tree(data['Cloak']).overlap(legs))
        cross_pairs[1] += len(tree(data['Skirt']).overlap(legs))
    assert cross_pairs == [0, 0], cross_pairs
    print('CROSS_STEP_CLOAK_SKIRT_PAIRS', cross_pairs)
    print('CLOTHING_RIG_CHECKS_OK')


if __name__ == '__main__':
    main()
