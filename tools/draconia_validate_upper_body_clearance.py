"""Compare visible jacket breakthrough from the back across a whole animation.

blender -b --python-exit-code 1 --python tools/draconia_validate_upper_body_clearance.py -- BEFORE.blend AFTER.blend
A grid of rear-facing rays measures cases where jacket is in front of cloak.
This is a view-dependent diagnostic, not a general collision solver.
"""
import bpy
import sys
from mathutils import Vector
from mathutils.bvhtree import BVHTree


def tree(name):
    obj = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    result = BVHTree.FromPolygons([obj.matrix_world @ v.co for v in mesh.vertices],
                                  [tuple(p.vertices) for p in mesh.polygons], epsilon=1e-6)
    obj.to_mesh_clear()
    return result


def audit(path):
    bpy.ops.wm.open_mainfile(filepath=path)
    scene = bpy.context.scene
    rig = bpy.data.objects['Draconia Body Rig - experimental']
    assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 192, 24)
    counts = []
    for frame in range(1, 193):
        scene.frame_set(frame)
        bpy.context.view_layer.update()
        cloak, jacket = tree('Cloak'), tree('Jacket')
        hips = rig.matrix_world @ rig.pose.bones['Hips'].head
        count = 0
        for x in range(-24, 25):
            for z in range(16, 55):
                origin = Vector((hips.x + x*.01, 5.0, hips.z + z*.01))
                direction = Vector((0, -1, 0))
                a, b = jacket.ray_cast(origin, direction), cloak.ray_cast(origin, direction)
                if a[0] is not None and b[0] is not None and a[3]+.001 < b[3]:
                    count += 1
        counts.append(count)
    print('REAR_LEAK', path, 'RAY_HITS', sum(counts), 'FRAMES', sum(c > 0 for c in counts),
          'WORST_FRAME', counts.index(max(counts))+1, 'WORST_COUNT', max(counts), flush=True)
    return counts

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 2:
    raise ValueError('Expected BEFORE.blend AFTER.blend')
before, after = map(audit, args)
assert sum(before) > 0, 'The baseline must reproduce the breakthrough'
assert sum(after) <= .05 * sum(before), (sum(before), sum(after))
print('UPPER_BODY_CLEARANCE_CHECK_OK', flush=True)
