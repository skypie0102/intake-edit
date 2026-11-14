"""Animate each chibi hand's joined fingers and separate thumb, at 24 fps.

blender -b CHIBI_HAND.blend --python-exit-code 1 --python tools/draconia_make_chibi_hand_motion.py -- OUTPUT.blend
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
if rig.animation_data and rig.animation_data.action:
    raise ValueError('Expected an unanimated hand study')
rig.animation_data_create()
rig.animation_data.action = bpy.data.actions.new('Chibi hand controls - four seconds')
scene.render.fps = 24


def pulse(frame, start, peak, end):
    if frame < start or frame > end:
        return 0.0
    value = (frame - start) / (peak - start) if frame <= peak else (end - frame) / (end - peak)
    return value * value * (3 - 2 * value)


for frame in range(1, 97):
    for side in ('L', 'R'):
        for name in ('Fingers', 'Thumb'):
            individual = pulse(frame, *((6, 22, 38) if name == 'Fingers' else (40, 54, 68)))
            combined = pulse(frame, 70, 82, 94)
            strength = max(individual, combined * .7)
            for segment in (1, 2):
                bone = rig.pose.bones[f'{name}.{segment:02d}.{side}']
                bone.rotation_mode = 'XYZ'
                degrees = (-25 if name == 'Thumb' else (30 if segment == 1 else 35)) * strength
                bone.rotation_euler.x = math.radians(degrees)
                bone.keyframe_insert(data_path='rotation_euler', index=0, frame=frame)
for curve in rig.animation_data.action.fcurves:
    for point in curve.keyframe_points:
        point.interpolation = 'LINEAR'
scene.frame_start, scene.frame_end = 1, 96
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('CHIBI_HAND_MOTION_STUDY', 96, 'frames at 24 fps', flush=True)
