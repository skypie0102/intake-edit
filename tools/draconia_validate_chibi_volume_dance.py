"""Sample hand self-intersections with both skinning methods during dance.

blender -b DANCE_DQ.blend --python-exit-code 1 --python tools/draconia_validate_chibi_volume_dance.py
Sixteen keyed poses per hand are sampled; this does not cover continuous time.
"""
import sys
from pathlib import Path

import bpy

sys.path.insert(0, str(Path(__file__).resolve().parent))
from draconia_hand_intersections import intersection_segments

scene = bpy.context.scene
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 192, 24)
samples = []
for frame in range(1, 193, 12):
    scene.frame_set(frame)
    for side in ('L', 'R'):
        obj = bpy.data.objects['Articulated hand study ' + side]
        modifier = next(m for m in obj.modifiers if m.type == 'ARMATURE')
        maxima = []
        for enabled in (False, True):
            modifier.use_deform_preserve_volume = enabled
            bpy.context.view_layer.update()
            maxima.append(max(intersection_segments(obj), default=0))
        modifier.use_deform_preserve_volume = True
        samples.append((frame, side, *maxima))
before = max(row[2] for row in samples)
after = max(row[3] for row in samples)
regression = max(row[3] - row[2] for row in samples)
assert after < .00075, (before, after)
print('CHIBI_DANCE_SAMPLED_SELF_INTERSECTION', before, after,
      'MAX_PER_SAMPLE_INCREASE', regression, 'SAMPLES', len(samples), flush=True)
