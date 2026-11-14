"""Check sampled self-intersections throughout the joined-hand diagnostic.

blender -b HAND_MOTION.blend --python-exit-code 1 --python tools/draconia_validate_joined_motion_collision.py
The segment-length threshold is in scene units and is not a general guarantee.
"""
import sys
from pathlib import Path

import bpy

sys.path.insert(0, str(Path(__file__).resolve().parent))
from draconia_hand_intersections import intersection_segments

scene = bpy.context.scene
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 96, 24)
results = []
for frame in range(1, 97):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    for side in ('L', 'R'):
        obj = bpy.data.objects['Articulated hand study ' + side]
        lengths = intersection_segments(obj)
        maximum = max(lengths, default=0)
        assert maximum < .00075, (frame, side, maximum)
        results.append((maximum, frame, side))
    if frame % 24 == 0:
        print('CHECKED_JOINED_HAND_FRAME', frame, flush=True)
print('JOINED_MOTION_MAX_SELF_INTERSECTION_SEGMENT', max(results),
      'JOINED_MOTION_COLLISION_CHECKS_OK', flush=True)
