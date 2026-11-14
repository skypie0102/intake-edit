"""Compare ordinary and volume-preserving skinning over the keyed wrist test.

blender -b WRIST_DQ.blend --python-exit-code 1 --python tools/draconia_validate_chibi_volume.py
Only the two hand meshes are changed, and the test covers the 96 keyed poses.
"""
import sys
from pathlib import Path

import bpy

sys.path.insert(0, str(Path(__file__).resolve().parent))
from draconia_hand_intersections import intersection_segments

scene = bpy.context.scene
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 96, 24)
results = {False: [], True: []}
max_drift = 0.0
neutral_drift = 0.0
for frame in range(1, 97):
    scene.frame_set(frame)
    for side in ('L', 'R'):
        obj = bpy.data.objects['Articulated hand study ' + side]
        modifier = next(m for m in obj.modifiers if m.type == 'ARMATURE')
        points = {}
        for enabled in (False, True):
            modifier.use_deform_preserve_volume = enabled
            bpy.context.view_layer.update()
            evaluated = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
            mesh = evaluated.to_mesh()
            points[enabled] = [v.co.copy() for v in mesh.vertices]
            evaluated.to_mesh_clear()
            results[enabled].append(max(intersection_segments(obj), default=0))
        drift = max((a - b).length for a, b in zip(points[False], points[True]))
        max_drift = max(max_drift, drift)
        if frame in (1, 24, 48, 72, 96):
            neutral_drift = max(neutral_drift, drift)
        modifier.use_deform_preserve_volume = True
    if frame % 24 == 0:
        print('CHECKED_CHIBI_VOLUME_FRAME', frame, flush=True)
before, after = max(results[False]), max(results[True])
assert after < before * .75, (before, after)
assert neutral_drift < 1e-5, neutral_drift
print('CHIBI_VOLUME_WRIST_SELF_INTERSECTION_MAX', before, after,
      'MAX_VERTEX_DELTA', max_drift, 'NEUTRAL_DELTA', neutral_drift, flush=True)
