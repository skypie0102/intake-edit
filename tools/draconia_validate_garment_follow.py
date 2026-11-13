"""Compare the garment follow rig with its input in representative poses.

blender -b --python-exit-code 1 --python tools/draconia_validate_garment_follow.py -- BEFORE.blend AFTER.blend

Triangle overlap is a narrow proxy: pairs can include hidden intersections and a
zero count for these samples does not establish collision-free animation.
"""
import bpy
import math
import sys
from mathutils import Vector
from mathutils.bvhtree import BVHTree

RIG = 'Draconia Body Rig - experimental'


def set_pose(rig, values):
    for bone in rig.pose.bones:
        bone.rotation_mode = 'XYZ'
        bone.rotation_euler = (0, 0, 0)
        bone.location = (0, 0, 0)
    for name, angles in values.items():
        rig.pose.bones[name].rotation_euler = [math.radians(a) for a in angles]
    bpy.context.view_layer.update()


def geometry():
    graph = bpy.context.evaluated_depsgraph_get()
    result = {}
    for obj in bpy.data.objects:
        if obj.type != 'MESH':
            continue
        evaluated = obj.evaluated_get(graph)
        mesh = evaluated.to_mesh()
        vertices = [evaluated.matrix_world @ v.co for v in mesh.vertices]
        polygons = [p.vertices[:] for p in mesh.polygons]
        result[obj.name] = (vertices, polygons)
        evaluated.to_mesh_clear()
    return result


def compare_geometry(expected, actual):
    assert expected.keys() == actual.keys()
    largest = 0.0
    for name in expected:
        a, af = expected[name]
        b, bf = actual[name]
        assert len(a) == len(b) and af == bf, name
        largest = max(largest, max(((x-y).length for x, y in zip(a, b)), default=0))
    assert largest < 1e-5, largest
    return largest


def overlaps(data, garment):
    def tree(name):
        vertices, polys = data[name]
        return BVHTree.FromPolygons(vertices, polys, all_triangles=False, epsilon=1e-6)
    return len(tree(garment).overlap(tree('Legs')))


def run(path, after):
    bpy.ops.wm.open_mainfile(filepath=path)
    rig = bpy.data.objects[RIG]
    if after:
        for garment in ('Cloak', 'Skirt'):
            for side in ('L', 'R'):
                follow = rig.pose.bones[f'{garment}Follow.{side}']
                assert not follow.bone.use_deform
                assert follow.parent.name == 'Hips'
                assert rig.pose.bones[f'{garment}Hem.{side}'].parent.name == follow.name
                con = follow.constraints['Half thigh rotation']
                assert con.target == rig and con.subtarget == f'Thigh.{side}'
        center = rig.pose.bones['CloakFollow.C']
        assert not center.bone.use_deform and center.parent.name == 'Hips'
        assert rig.pose.bones['CloakHem.C'].parent.name == center.name
        for side in ('L', 'R'):
            con = center.constraints[f'Half thigh rotation {side}']
            assert con.target == rig and con.subtarget == f'Thigh.{side}'
    samples = ({}, {'Torso': (0, 0, 20), 'UpperArm.R': (0, 0, -30), 'Forearm.R': (45, 0, 0)},
               {'CloakHem.L': (10, 0, -8), 'SkirtHem.R': (10, 0, 8)})
    static = []
    for p in samples:
        set_pose(rig, p)
        static.append(geometry())
    loop = []
    for frame in range(24):
        s = math.sin(2 * math.pi * frame / 24)
        set_pose(rig, {'Thigh.R': (50*s, 0, 0), 'Shin.R': (-15*max(0, s), 0, 0)})
        data = geometry()
        loop.append((overlaps(data, 'Cloak'), overlaps(data, 'Skirt')))
    return static, loop


def main():
    args = sys.argv[sys.argv.index('--') + 1:]
    if len(args) != 2:
        raise ValueError('Expected BEFORE.blend AFTER.blend')
    before_static, before_loop = run(args[0], False)
    after_static, after_loop = run(args[1], True)
    for a, b in zip(before_static, after_static):
        print('MAX_UNCHANGED_POSE_DIFFERENCE', compare_geometry(a, b))
    print('LOOP_CLOAK_PAIRS_BEFORE_AFTER', sum(x[0] for x in before_loop), sum(x[0] for x in after_loop))
    print('LOOP_SKIRT_PAIRS_BEFORE_AFTER', sum(x[1] for x in before_loop), sum(x[1] for x in after_loop))
    assert sum(x[0] for x in after_loop) == 0 and sum(x[1] for x in after_loop) == 0
    print('GARMENT_FOLLOW_CHECKS_OK')


if __name__ == '__main__':
    main()
