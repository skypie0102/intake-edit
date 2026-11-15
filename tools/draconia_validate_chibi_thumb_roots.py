"""Compare the chibi rig before and after local thumb-root smoothing.

blender -b --python-exit-code 1 --python tools/draconia_validate_chibi_thumb_roots.py -- BEFORE.blend AFTER.blend
"""
import sys

import bmesh
import bpy

paths = sys.argv[sys.argv.index('--') + 1:]
if len(paths) != 2:
    raise ValueError('Expected BEFORE.blend AFTER.blend')


def snapshot():
    result = {}
    for obj in bpy.data.objects:
        if obj.type != 'MESH':
            continue
        mesh = obj.data
        result[obj.name] = (
            [v.co.copy() for v in mesh.vertices],
            [tuple(poly.vertices) for poly in mesh.polygons],
            [[(obj.vertex_groups[g.group].name, g.weight) for g in v.groups] for v in mesh.vertices],
        )
    return result


bpy.ops.wm.open_mainfile(filepath=paths[0])
before = snapshot()
bpy.ops.wm.open_mainfile(filepath=paths[1])
after = snapshot()
assert before.keys() == after.keys()
for name, (vertices, faces, weights) in after.items():
    old_vertices, old_faces, old_weights = before[name]
    assert faces == old_faces and weights == old_weights and len(vertices) == len(old_vertices), name
    moved = [(a - b).length for a, b in zip(vertices, old_vertices)]
    if name.startswith('Articulated hand study '):
        assert 0 < sum(v > 1e-8 for v in moved) <= 30
        assert max(moved) < .001, (name, max(moved))
        bm = bmesh.new()
        bm.from_mesh(bpy.data.objects[name].data)
        assert all(edge.is_manifold for edge in bm.edges)
        bm.free()
        print('THUMB_ROOT_MESH_DELTA', name, 'vertices', sum(v > 1e-8 for v in moved),
              'maximum', max(moved), flush=True)
    else:
        assert max(moved, default=0) == 0, name
assert bpy.data.objects['Wand'].hide_render
print('CHIBI_THUMB_ROOT_TOPOLOGY_AND_WEIGHTS_OK', flush=True)
