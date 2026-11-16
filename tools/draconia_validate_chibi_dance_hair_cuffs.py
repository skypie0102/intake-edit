"""Compare sampled hair/cuff intersections before and after dance cleanup.

blender -b --python-exit-code 1 --python tools/draconia_validate_chibi_dance_hair_cuffs.py -- BEFORE.blend AFTER.blend
Face-pair counts include hidden layers and do not cover all continuous times.
"""
import sys

import bpy
from mathutils.bvhtree import BVHTree

paths = sys.argv[sys.argv.index('--') + 1:]
if len(paths) != 2:
    raise ValueError('Expected BEFORE.blend AFTER.blend')


def samples(path):
    bpy.ops.wm.open_mainfile(filepath=path)
    scene = bpy.context.scene
    assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 192, 24)
    values = []
    for frame in range(1, 193):
        scene.frame_set(frame)
        bpy.context.view_layer.update()
        trees = []
        for name in ('Hair', 'Jacket Hands'):
            obj = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
            mesh = obj.to_mesh()
            vertices = [obj.matrix_world @ v.co for v in mesh.vertices]
            polygons = [tuple(p.vertices) for p in mesh.polygons]
            trees.append(BVHTree.FromPolygons(vertices, polygons, epsilon=1e-6))
            obj.to_mesh_clear()
        values.append(len(trees[0].overlap(trees[1])))
    return values


before, after = (samples(path) for path in paths)
assert all(new <= old for old, new in zip(before, after))
print('DANCE_HAIR_CUFF_PAIRS', sum(before), '->', sum(after),
      'MAX', max(before), '->', max(after), flush=True)
