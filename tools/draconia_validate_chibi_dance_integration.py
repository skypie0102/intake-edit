"""Compare the chibi dance with the accepted original dance across all frames.

blender -b --python-exit-code 1 --python tools/draconia_validate_chibi_dance_integration.py -- ACCEPTED.blend CHIBI.blend
The original arm mesh is replaced by the separately checked chibi hands.
"""
from array import array
import sys

import bpy

paths = sys.argv[sys.argv.index('--') + 1:]
if len(paths) != 2:
    raise ValueError('Expected ACCEPTED.blend CHIBI.blend')
names = ('Cloak', 'Jacket Hands', 'Hair', 'Jacket', 'Legs', 'Skirt', 'Head')


def snapshot():
    graph = bpy.context.evaluated_depsgraph_get()
    result = {}
    for name in names:
        obj = bpy.data.objects[name].evaluated_get(graph)
        mesh = obj.to_mesh()
        coords = array('f', [0.0]) * (len(mesh.vertices) * 3)
        mesh.vertices.foreach_get('co', coords)
        result[name] = (coords, tuple(v for row in obj.matrix_world for v in row), len(mesh.polygons))
        obj.to_mesh_clear()
    return result


bpy.ops.wm.open_mainfile(filepath=paths[0])
scene = bpy.context.scene
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 192, 24)
rig = bpy.data.objects['Draconia Body Rig - experimental']
baseline = []
for frame in range(1, 193):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    baseline.append((snapshot(), rig['Blink'], rig['JacketClearance']))
bpy.ops.wm.open_mainfile(filepath=paths[1])
scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 192, 24)
assert bpy.data.objects['Arms'].hide_render and bpy.data.objects['Wand'].hide_render
assert all(not bpy.data.objects['Articulated hand study ' + side].hide_render for side in ('L', 'R'))
maximum = 0.0
for frame in range(1, 193):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    old, blink, jacket = baseline[frame - 1]
    assert abs(rig['Blink'] - blink) < 1e-6
    assert abs(rig['JacketClearance'] - jacket) < 1e-6
    assert abs(rig['NeutralArmClearance']) < 1e-6
    for name, (coords, matrix, faces) in snapshot().items():
        previous, old_matrix, old_faces = old[name]
        assert len(coords) == len(previous) and faces == old_faces
        maximum = max(maximum, max((abs(a - b) for a, b in zip(coords, previous)), default=0),
                      max(abs(a - b) for a, b in zip(matrix, old_matrix)))
assert maximum < 1e-5, maximum
print('CHIBI_DANCE_INTEGRATION_MAX_COMPONENT_DELTA', maximum, flush=True)
