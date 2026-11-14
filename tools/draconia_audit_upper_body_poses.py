"""Compare hair/cloak overlap and rear jacket breakthrough in seeded poses.

blender -b --python-exit-code 1 --python tools/draconia_audit_upper_body_poses.py -- BEFORE.blend AFTER.blend
These ranges are samples; ray hits and face intersections do not prove all
poses or all views are collision-free.
"""
import bpy
import math
import random
import sys
from mathutils import Vector
from mathutils.bvhtree import BVHTree


def mesh_tree(name):
    obj = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    result = BVHTree.FromPolygons([obj.matrix_world @ v.co for v in mesh.vertices],
                                  [tuple(p.vertices) for p in mesh.polygons], epsilon=1e-6)
    obj.to_mesh_clear()
    return result

rng = random.Random(20261114)
limits = {'Torso': (12, 0, 12), 'Head': (12, 0, 15),
          'UpperArm.L': (25, 0, 25), 'UpperArm.R': (25, 0, 25),
          'Forearm.L': (35, 0, 0), 'Forearm.R': (35, 0, 0)}
poses = [{name: tuple(rng.uniform(-v, v) for v in xyz) for name, xyz in limits.items()} for _ in range(120)]
paths = sys.argv[sys.argv.index('--')+1:]
if len(paths) != 2:
    raise ValueError('Expected BEFORE.blend AFTER.blend')
results = []
for path in paths:
    bpy.ops.wm.open_mainfile(filepath=path)
    rig = bpy.data.objects['Draconia Body Rig - experimental']
    totals = {'hair_cloak_pairs': 0, 'hair_cloak_max': 0, 'jacket_leak_rays': 0, 'leaky_poses': 0}
    for values in poses:
        for bone in rig.pose.bones:
            bone.rotation_mode = 'XYZ'
            bone.rotation_euler = (0, 0, 0)
        for name, angles in values.items():
            rig.pose.bones[name].rotation_euler = [math.radians(a) for a in angles]
        bpy.context.view_layer.update()
        cloak, hair, jacket = (mesh_tree(n) for n in ('Cloak', 'Hair', 'Jacket'))
        pairs = len(cloak.overlap(hair))
        totals['hair_cloak_pairs'] += pairs
        totals['hair_cloak_max'] = max(totals['hair_cloak_max'], pairs)
        hips = rig.matrix_world @ rig.pose.bones['Hips'].head
        count = 0
        for x in range(-24, 25, 2):
            for z in range(16, 55, 2):
                point = Vector((hips.x + x*.01, 5, hips.z + z*.01))
                direction = Vector((0, -1, 0))
                a, b = jacket.ray_cast(point, direction), cloak.ray_cast(point, direction)
                count += a[0] is not None and b[0] is not None and a[3]+.001 < b[3]
        totals['jacket_leak_rays'] += count
        totals['leaky_poses'] += count > 0
    print('UPPER_BODY_POSES', path, totals, flush=True)
    results.append(totals)
assert results[1]['jacket_leak_rays'] == 0, results[1]
assert results[1]['hair_cloak_pairs'] < results[0]['hair_cloak_pairs'], results
print('UPPER_BODY_POSE_CHECK_OK', flush=True)
