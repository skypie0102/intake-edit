"""Compare hair clearance and deformation over both full 192-frame dance scenes.

blender -b --python-exit-code 1 --python tools/draconia_validate_dance_hair_clearance.py -- BASE.blend REFINED.blend
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
    assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 192, 24)
    assert bpy.data.objects['Wand'].hide_render
    frames = []
    for frame in range(1, 193):
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

# Check halfway between keyed frames as well; these are still a sample, not a
# guarantee for all continuous times.
def half_frame_pairs(path):
    bpy.ops.wm.open_mainfile(filepath=path)
    scene = bpy.context.scene
    values = []
    for frame in range(1, 192):
        scene.frame_set(frame, subframe=.5)
        bpy.context.view_layer.update()
        trees = []
        for name in ('Hair', 'Cloak'):
            obj = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
            mesh = obj.to_mesh()
            vertices = [obj.matrix_world @ vertex.co for vertex in mesh.vertices]
            faces = [tuple(face.vertices) for face in mesh.polygons]
            trees.append(BVHTree.FromPolygons(vertices, faces, epsilon=1e-6))
            obj.to_mesh_clear()
        values.append(len(trees[0].overlap(trees[1])))
    return values

old_half, new_half = (half_frame_pairs(path) for path in args)
assert all(after <= before for before, after in zip(old_half, new_half))
assert sum(new_half) < sum(old_half)
print('HAIR_CLOAK_HALF_FRAME_PAIRS', sum(old_half), '->', sum(new_half),
      'MAX', max(old_half), '->', max(new_half), flush=True)
scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
for frame, value in ((1, 0), (33, .5), (34, 1), (35, 2 / 3), (36, 1 / 3), (37, 0), (82, 1), (85, 0), (130, 1), (133, 0), (178, 1), (181, 0), (192, 0)):
    scene.frame_set(frame)
    assert abs(rig['Blink'] - value) < 1e-5
print('HAIR_CLOAK_PAIRS', old_total, '->', new_total,
      'MAX', max(f[1] for f in baseline), '->', max(f[1] for f in refined), flush=True)
print('HAIR_AREA_RATIO', min(ratios), max(ratios), 'FLIPPED', flipped,
      'OTHER_MESH_DRIFT', unchanged, 'LEG_PAIRS', 0, flush=True)
print('DANCE_HAIR_CLEARANCE_CHECKS_OK', flush=True)
