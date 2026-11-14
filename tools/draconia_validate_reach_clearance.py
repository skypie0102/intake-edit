"""Compare hair clearance and deformation over both 48-frame reach scenes.

blender -b --python-exit-code 1 --python tools/draconia_validate_reach_clearance.py -- BASE.blend REFINED.blend
Face-pair intersections include inherited layers; this checks this motion
only and does not establish general collision-free movement.
"""
import sys

import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 2:
    raise ValueError('Expected BASE.blend REFINED.blend')


def sample(path):
    bpy.ops.wm.open_mainfile(filepath=path)
    scene = bpy.context.scene
    assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 48, 24)
    assert bpy.data.objects['Wand'].hide_render
    frames = []
    for frame in range(1, 49):
        scene.frame_set(frame)
        bpy.context.view_layer.update()
        depsgraph = bpy.context.evaluated_depsgraph_get()
        geometry, trees = {}, {}
        for name in ('Hair', 'Cloak', 'Skirt', 'Arms', 'Legs'):
            obj = bpy.data.objects[name].evaluated_get(depsgraph)
            mesh = obj.to_mesh()
            vertices = [obj.matrix_world @ v.co for v in mesh.vertices]
            faces = [tuple(p.vertices) for p in mesh.polygons]
            geometry[name] = {'vertices': vertices,
                              'areas': [p.area for p in mesh.polygons],
                              'normals': [p.normal.copy() for p in mesh.polygons]}
            trees[name] = BVHTree.FromPolygons(vertices, faces, epsilon=1e-6)
            obj.to_mesh_clear()
        pairs = len(trees['Hair'].overlap(trees['Cloak']))
        legs = {name: len(trees[name].overlap(trees['Legs']))
                for name in ('Cloak', 'Skirt', 'Arms')}
        frames.append((geometry, pairs, legs))
    return frames


baseline, refined = (sample(path) for path in args)
ratios, flipped, unchanged = [], 0, 0
for (before, old_pairs, _), (after, new_pairs, legs) in zip(baseline, refined):
    assert new_pairs <= old_pairs, (old_pairs, new_pairs)
    assert not any(legs.values()), legs
    for name in ('Cloak', 'Skirt', 'Arms', 'Legs'):
        unchanged = max(unchanged, max((a - b).length for a, b in zip(before[name]['vertices'], after[name]['vertices'])))
    for old_area, new_area, old_normal, new_normal in zip(
            before['Hair']['areas'], after['Hair']['areas'],
            before['Hair']['normals'], after['Hair']['normals']):
        if old_area > 1e-9:
            ratios.append(new_area / old_area)
        flipped += old_normal.dot(new_normal) < 0
assert unchanged < 1e-6 and not flipped
assert min(ratios) > .5 and max(ratios) < 2
old_total = sum(frame[1] for frame in baseline)
new_total = sum(frame[1] for frame in refined)
assert new_total < old_total
scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
for frame, value in ((1, 0), (9, .5), (10, 1), (11, 2 / 3), (12, 1 / 3), (13, 0), (32, 1), (35, 0), (48, 0)):
    scene.frame_set(frame)
    assert abs(rig['Blink'] - value) < 1e-5
print('HAIR_CLOAK_PAIRS', old_total, '->', new_total,
      'MAX', max(f[1] for f in baseline), '->', max(f[1] for f in refined), flush=True)
print('HAIR_AREA_RATIO', min(ratios), max(ratios), 'FLIPPED', flipped,
      'OTHER_MESH_DRIFT', unchanged, 'LEG_PAIRS', 0, flush=True)
print('REACH_CLEARANCE_CHECKS_OK', flush=True)
