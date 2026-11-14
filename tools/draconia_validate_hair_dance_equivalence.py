"""Check the hair controls do not alter an existing dance when unposed.

blender -b --python-exit-code 1 --python tools/draconia_validate_hair_dance_equivalence.py -- BEFORE.blend AFTER.blend
"""
import bpy
import sys

paths = sys.argv[sys.argv.index('--')+1:]
if len(paths) != 2:
    raise ValueError('Expected BEFORE.blend AFTER.blend')
samples = []
for path in paths:
    bpy.ops.wm.open_mainfile(filepath=path)
    scene = bpy.context.scene
    assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 192, 24)
    frames = []
    for frame in (1, 25, 49, 73, 97, 121, 145, 169, 192):
        scene.frame_set(frame)
        bpy.context.view_layer.update()
        result = {}
        graph = bpy.context.evaluated_depsgraph_get()
        for name in ('Hair', 'Cloak', 'Skirt', 'Arms', 'Legs'):
            obj = bpy.data.objects[name].evaluated_get(graph)
            mesh = obj.to_mesh()
            result[name] = [obj.matrix_world @ v.co for v in mesh.vertices]
            obj.to_mesh_clear()
        frames.append(result)
    samples.append(frames)
maximum = 0
for before, after in zip(*samples):
    assert before.keys() == after.keys()
    for name in before:
        assert len(before[name]) == len(after[name])
        maximum = max(maximum, max(((a-b).length for a, b in zip(before[name], after[name])), default=0))
assert maximum < 1e-5, maximum
print('DANCE_GEOMETRY_MAX_DELTA', maximum, 'SAMPLES', len(samples[0]), flush=True)
