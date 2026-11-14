"""Compare all dance frames with the neutral-only arm control disabled.

blender -b --python-exit-code 1 --python tools/draconia_validate_arm_clearance_dance.py -- BEFORE.blend AFTER.blend
"""
from array import array
import sys

import bpy

paths = sys.argv[sys.argv.index('--') + 1:]
if len(paths) != 2:
    raise ValueError('Expected BEFORE.blend AFTER.blend')
names = ('Cloak', 'Jacket Hands', 'Hair', 'Jacket', 'Legs',
         'Articulated hand study L', 'Articulated hand study R')


def data():
    graph = bpy.context.evaluated_depsgraph_get()
    result = {}
    for name in names:
        obj = bpy.data.objects[name].evaluated_get(graph)
        mesh = obj.to_mesh()
        values = array('f', [0.0]) * (len(mesh.vertices) * 3)
        mesh.vertices.foreach_get('co', values)
        result[name] = (values, tuple(v for row in obj.matrix_world for v in row), len(mesh.polygons))
        obj.to_mesh_clear()
    return result


bpy.ops.wm.open_mainfile(filepath=paths[0])
scene = bpy.context.scene
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 192, 24)
baseline = []
for frame in range(1, 193):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    baseline.append((data(), bpy.data.objects['Draconia Body Rig - experimental']['Blink']))
bpy.ops.wm.open_mainfile(filepath=paths[1])
scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 192, 24)
assert bpy.data.objects['Wand'].hide_render
maximum = 0.0
for frame in range(1, 193):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    assert abs(rig['NeutralArmClearance']) < 1e-6
    assert abs(rig['Blink'] - baseline[frame - 1][1]) < 1e-6
    current = data()
    previous = baseline[frame - 1][0]
    for name in names:
        old, old_matrix, old_faces = previous[name]
        new, new_matrix, new_faces = current[name]
        assert len(old) == len(new) and old_faces == new_faces
        maximum = max(maximum, max((abs(a - b) for a, b in zip(old, new)), default=0),
                      max(abs(a - b) for a, b in zip(old_matrix, new_matrix)))
assert maximum < 1e-5, maximum
print('DANCE_ALL_FRAMES_MAX_COMPONENT_DELTA', maximum, 'ARM_CLEARANCE_DANCE_CHECKS_OK', flush=True)
