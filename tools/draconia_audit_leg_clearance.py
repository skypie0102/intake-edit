"""Compare garment intersections in sampled poses, reporting leg/leg overlaps.

blender -b --python-exit-code 1 --python tools/draconia_audit_leg_clearance.py -- BEFORE.blend AFTER.blend [SEED] [REPEATS]
A surface overlap is only a diagnostic, and filtering intersecting legs does not
prove the remaining poses are anatomically valid or visually acceptable.
"""
import bpy
import math
import random
import sys
from mathutils.bvhtree import BVHTree

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) not in (2, 3, 4):
    raise ValueError('Expected BEFORE.blend AFTER.blend [SEED] [REPEATS]')
random.seed(int(args[2]) if len(args) >= 3 else 42)
repeats = int(args[3]) if len(args) == 4 else 1
if not 1 <= repeats <= 20:
    raise ValueError('REPEATS must be between 1 and 20')
poses = []
for count, limits in (
    (120, {'Thigh.L': (55, 0, 45), 'Thigh.R': (55, 0, 45),
           'Shin.L': (20, 0, 0), 'Shin.R': (20, 0, 0), 'Torso': (0, 0, 15)}),
    (60, {'Thigh.L': (40, 0, 60), 'Thigh.R': (40, 0, 60), 'Torso': (0, 0, 15)}),
):
    for _ in range(count * repeats):
        poses.append({name: tuple(random.uniform(-v, v) for v in axes)
                      for name, axes in limits.items()})


def mesh_tree(name):
    obj = bpy.data.objects[name]
    evaluated = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = evaluated.to_mesh()
    vertices = [evaluated.matrix_world @ v.co for v in mesh.vertices]
    polygons = [tuple(p.vertices) for p in mesh.polygons]
    tree = BVHTree.FromPolygons(vertices, polygons, all_triangles=False, epsilon=1e-6)
    evaluated.to_mesh_clear()
    return tree


for path in args[:2]:
    bpy.ops.wm.open_mainfile(filepath=path)
    rig = bpy.data.objects['Draconia Body Rig - experimental']
    legs = bpy.data.objects['Legs']
    # This scene's two leg islands are on opposite sides in the rest pose.
    sides = []
    for polygon in legs.data.polygons:
        x = sum((legs.matrix_world @ legs.data.vertices[i].co).x for i in polygon.vertices) / len(polygon.vertices)
        sides.append(1 if x > 0 else -1)
    totals = {'poses': 0, 'leg_overlap_poses': 0, 'clear_leg_cloak_pairs': 0,
              'clear_leg_skirt_pairs': 0, 'clear_leg_cloak_poses': 0,
              'clear_leg_skirt_poses': 0}
    for values in poses:
        for bone in rig.pose.bones:
            bone.rotation_mode = 'XYZ'
            bone.rotation_euler = (0, 0, 0)
        for name, angles in values.items():
            rig.pose.bones[name].rotation_euler = [math.radians(a) for a in angles]
        bpy.context.view_layer.update()
        leg_tree = mesh_tree('Legs')
        leg_pairs = {(min(a, b), max(a, b)) for a, b in leg_tree.overlap(leg_tree)
                     if a != b and sides[a] != sides[b]}
        totals['poses'] += 1
        if leg_pairs:
            totals['leg_overlap_poses'] += 1
            continue
        cloak = len(mesh_tree('Cloak').overlap(leg_tree))
        skirt = len(mesh_tree('Skirt').overlap(leg_tree))
        totals['clear_leg_cloak_pairs'] += cloak
        totals['clear_leg_skirt_pairs'] += skirt
        totals['clear_leg_cloak_poses'] += bool(cloak)
        totals['clear_leg_skirt_poses'] += bool(skirt)
    print('LEG_CLEARANCE', path, totals, flush=True)
