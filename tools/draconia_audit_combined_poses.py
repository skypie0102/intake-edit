"""Sample coordinated body, arm and leg poses for garment/leg intersections.

blender -b --python-exit-code 1 --python tools/draconia_audit_combined_poses.py -- BEFORE.blend AFTER.blend [SEED]
The sampled ranges are local bone rotations about the model's asymmetric rest pose.
Surface overlaps are diagnostics, not proof of collision-free animation.
"""
import bpy
import math
import random
import sys
from mathutils.bvhtree import BVHTree

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) not in (2, 3):
    raise ValueError('Expected BEFORE.blend AFTER.blend [SEED]')
random.seed(int(args[2]) if len(args) == 3 else 20261114)
limits = {'Thigh.L': (30, 0, 20), 'Thigh.R': (30, 0, 20),
          'Shin.L': (15, 0, 0), 'Shin.R': (15, 0, 0),
          'UpperArm.L': (25, 0, 25), 'UpperArm.R': (25, 0, 25),
          'Forearm.L': (35, 0, 0), 'Forearm.R': (35, 0, 0),
          'Torso': (0, 0, 10)}
poses = [{name: tuple(random.uniform(-v, v) for v in axes)
          for name, axes in limits.items()} for _ in range(240)]


def mesh_tree(name):
    obj = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    vertices = [obj.matrix_world @ v.co for v in mesh.vertices]
    polygons = [tuple(p.vertices) for p in mesh.polygons]
    result = BVHTree.FromPolygons(vertices, polygons, all_triangles=False, epsilon=1e-6)
    obj.to_mesh_clear()
    return result


for path in args[:2]:
    bpy.ops.wm.open_mainfile(filepath=path)
    rig = bpy.data.objects['Draconia Body Rig - experimental']
    legs = bpy.data.objects['Legs']
    sides = []
    for polygon in legs.data.polygons:
        x = sum((legs.matrix_world @ legs.data.vertices[i].co).x for i in polygon.vertices) / len(polygon.vertices)
        sides.append(1 if x > 0 else -1)
    totals = {'poses': 0, 'leg_overlap_poses': 0, 'arm_leg_overlap_poses': 0,
              'clear_body_cloak_pairs': 0, 'clear_body_skirt_pairs': 0,
              'clear_body_cloak_poses': 0, 'clear_body_skirt_poses': 0}
    for index, values in enumerate(poses):
        for bone in rig.pose.bones:
            bone.rotation_mode = 'XYZ'
            bone.rotation_euler = (0, 0, 0)
        for name, angles in values.items():
            rig.pose.bones[name].rotation_euler = [math.radians(a) for a in angles]
        bpy.context.view_layer.update()
        leg_tree = mesh_tree('Legs')
        leg_pairs = {(min(a, b), max(a, b)) for a, b in leg_tree.overlap(leg_tree)
                     if a != b and sides[a] != sides[b]}
        arm_pairs = mesh_tree('Arms').overlap(leg_tree)
        totals['poses'] += 1
        totals['leg_overlap_poses'] += bool(leg_pairs)
        totals['arm_leg_overlap_poses'] += bool(arm_pairs)
        if leg_pairs or arm_pairs:
            continue
        cloak = len(mesh_tree('Cloak').overlap(leg_tree))
        skirt = len(mesh_tree('Skirt').overlap(leg_tree))
        totals['clear_body_cloak_pairs'] += cloak
        totals['clear_body_skirt_pairs'] += skirt
        totals['clear_body_cloak_poses'] += bool(cloak)
        totals['clear_body_skirt_poses'] += bool(skirt)
        if cloak or skirt:
            print('COMBINED_HIT', index, 'cloak', cloak, 'skirt', skirt,
                  {name: tuple(round(a, 2) for a in angles) for name, angles in values.items()}, flush=True)
    print('COMBINED_POSES', path, totals, flush=True)
