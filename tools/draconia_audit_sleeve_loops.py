"""Measure area ratios and surface intersections over the preview sleeve loops.

blender -b --python-exit-code 1 --python tools/draconia_audit_sleeve_loops.py -- BEFORE.blend AFTER.blend
Overlaps and area ratios are limited diagnostics, not proof of visual quality.
"""
import bpy
import math
import sys
from mathutils.bvhtree import BVHTree


def evaluated(name):
    obj = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    vertices = [obj.matrix_world @ v.co for v in mesh.vertices]
    polygons = [tuple(p.vertices) for p in mesh.polygons]
    obj.to_mesh_clear()
    return vertices, polygons


def area(poly, vertices):
    p = [vertices[i] for i in poly]
    return sum((p[i] - p[0]).cross(p[i+1] - p[0]).length * 0.5 for i in range(1, len(p)-1))


def tree(mesh):
    return BVHTree.FromPolygons(*mesh, all_triangles=False, epsilon=1e-6)


def pose(rig, side, frame):
    t = 2 * math.pi * frame / 24
    for bone in rig.pose.bones:
        bone.rotation_mode = 'XYZ'
        bone.rotation_euler = (0, 0, 0)
    if side == 'wand':
        rig.pose.bones['Forearm.L'].rotation_euler.x = math.radians(45 * math.sin(t))
        rig.pose.bones['UpperArm.L'].rotation_euler.z = math.radians(8 * math.sin(t))
    else:
        rig.pose.bones['Forearm.R'].rotation_euler.x = math.radians(50 * math.sin(t))
        rig.pose.bones['UpperArm.R'].rotation_euler.z = math.radians(-22 * (1 - math.cos(t)) / 2)
    bpy.context.view_layer.update()


def audit(path):
    bpy.ops.wm.open_mainfile(filepath=path)
    rig = bpy.data.objects['Draconia Body Rig - experimental']
    pose(rig, 'wand', 0)
    obj = bpy.data.objects['Cloak']
    ids = {g.index for g in obj.vertex_groups if g.name.startswith(('UpperArm.', 'Forearm.'))}
    mask = [p.index for p in obj.data.polygons if
            sum(sum(g.weight for g in obj.data.vertices[i].groups if g.group in ids)
                for i in p.vertices) / len(p.vertices) > 0.2]
    rest_vertices, polygons = evaluated('Cloak')
    base = {i: area(polygons[i], rest_vertices) for i in mask}
    result = {'faces_over_2x': 0, 'largest_area_ratio': 0.0, 'arm_pairs': 0, 'undersleeve_pairs': 0}
    for side in ('wand', 'other'):
        for frame in range(24):
            pose(rig, side, frame)
            cloak = evaluated('Cloak')
            ratios = [area(polygons[i], cloak[0]) / base[i] for i in mask if base[i] > 1e-9]
            result['faces_over_2x'] += sum(r > 2 for r in ratios)
            result['largest_area_ratio'] = max(result['largest_area_ratio'], max(ratios))
            shell = tree(cloak)
            result['arm_pairs'] += len(shell.overlap(tree(evaluated('Arms'))))
            result['undersleeve_pairs'] += len(shell.overlap(tree(evaluated('Jacket Hands'))))
    return result


def main():
    args = sys.argv[sys.argv.index('--') + 1:]
    if len(args) != 2:
        raise ValueError('Expected BEFORE.blend AFTER.blend')
    before, after = (audit(p) for p in args)
    print('SLEEVE_LOOPS_BEFORE', before)
    print('SLEEVE_LOOPS_AFTER', after)
    assert after['faces_over_2x'] <= before['faces_over_2x']
    assert after['largest_area_ratio'] <= before['largest_area_ratio']
    print('SLEEVE_LOOP_CHECKS_OK')


if __name__ == '__main__':
    main()
