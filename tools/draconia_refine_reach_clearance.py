"""Key a smoother lower-hair clearance correction on the reach study.

blender -b INPUT.blend --python-exit-code 1 --python tools/draconia_refine_reach_clearance.py -- OUTPUT.blend

Input is the 48-frame reach made from the rig with the accepted blink. The
correction is keyed for this motion, not an automatic collision solver.
"""
import math
import sys
from pathlib import Path

import bpy

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 1:
    raise ValueError('Expected OUTPUT.blend')
output = Path(args[0]).resolve()
if output.exists() or output == Path(bpy.data.filepath).resolve():
    raise ValueError('Output must be a new file')
scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
if (scene.frame_start, scene.frame_end, scene.render.fps) != (1, 48, 24):
    raise ValueError('Expected the 48-frame reach study')
if not rig.animation_data or not rig.animation_data.action or 'Blink' not in rig:
    raise ValueError('Expected an animated reach rig with the blink control')
for frame in range(1, 49):
    scene.frame_set(frame)
    reach = (1 - math.cos(2 * math.pi * (frame - 1) / 48)) / 2
    for name in ('Hair.C', 'Hair.L', 'Hair.R'):
        bone = rig.pose.bones[name]
        bone.rotation_euler.x += math.radians(35 * reach)
        bone.keyframe_insert(data_path='rotation_euler', index=0, frame=frame)

# Exercise the accepted blink in the same scene as the reach. Blink zero at
# both ends keeps the looping preview's default open-eyed appearance.
for frame, value in ((1, 0), (8, 0), (10, 1), (13, 0),
                     (30, 0), (32, 1), (35, 0), (48, 0)):
    rig['Blink'] = float(value)
    rig.keyframe_insert(data_path='["Blink"]', frame=frame)
for curve in rig.animation_data.action.fcurves:
    if curve.data_path == '["Blink"]' or (curve.data_path.startswith('pose.bones["Hair.') and curve.array_index == 0):
        for key in curve.keyframe_points:
            key.interpolation = 'LINEAR'
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('REACH_CLEARANCE_AND_BLINK_KEYED', flush=True)
