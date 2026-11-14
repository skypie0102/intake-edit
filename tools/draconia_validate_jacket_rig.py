"""Check jacket corrective control, preserved UVs, and unchanged other meshes.

blender -b --python-exit-code 1 --python tools/draconia_validate_jacket_rig.py -- BEFORE.blend AFTER.blend
"""
import sys

import bpy

paths = sys.argv[sys.argv.index('--') + 1:]
if len(paths) != 2:
    raise ValueError('Expected BEFORE.blend AFTER.blend')


def snapshot():
    bpy.context.view_layer.update()
    graph = bpy.context.evaluated_depsgraph_get()
    result = {}
    for obj in bpy.data.objects:
        if obj.type != 'MESH':
            continue
        evaluated = obj.evaluated_get(graph)
        mesh = evaluated.to_mesh()
        result[obj.name] = ([evaluated.matrix_world @ vertex.co for vertex in mesh.vertices],
                            [tuple(face.vertices) for face in mesh.polygons])
        evaluated.to_mesh_clear()
    return result


def uv_coordinates():
    layer = bpy.data.objects['Jacket'].data.uv_layers.active
    assert layer is not None
    return [tuple(item.uv) for item in layer.data]


bpy.ops.wm.open_mainfile(filepath=paths[0])
before, old_uv = snapshot(), uv_coordinates()
bpy.ops.wm.open_mainfile(filepath=paths[1])
rig = bpy.data.objects['Draconia Body Rig - experimental']
assert rig['JacketClearance'] == 1.0
key = bpy.data.objects['Jacket'].data.shape_keys.key_blocks['Jacket side clearance']
bpy.context.view_layer.update()
assert abs(key.value - 1) < 1e-5
assert old_uv == uv_coordinates()
active = snapshot()
assert before.keys() == active.keys()
for name in before:
    old_vertices, old_faces = before[name]
    new_vertices, new_faces = active[name]
    assert old_faces == new_faces and len(old_vertices) == len(new_vertices), name
    if name != 'Jacket':
        assert max(((a - b).length for a, b in zip(old_vertices, new_vertices)), default=0) < 1e-5, name
rig['JacketClearance'] = 0.0
rig.update_tag()
bpy.context.view_layer.update()
assert abs(key.value) < 1e-5
inactive = snapshot()
maximum = max((a - b).length for name in before
              for a, b in zip(before[name][0], inactive[name][0]))
assert maximum < 1e-5, maximum
print('JACKET_RIG_INACTIVE_MAX_DELTA', maximum, 'JACKET_RIG_CHECKS_OK', flush=True)
