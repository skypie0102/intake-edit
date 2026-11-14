"""Animate the joined hand study through individual finger motions.

blender -b JOINED_HAND.blend --python-exit-code 1 --python tools/draconia_make_joined_hand_motion.py -- OUTPUT.blend
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
if not all(bpy.data.objects.get('Articulated hand study ' + side) for side in ('L', 'R')):
    raise ValueError('Expected both study hands')
rig.animation_data_create()
rig.animation_data.action = bpy.data.actions.new('Joined hand controls - four seconds')
scene.render.fps = 24


def smooth(value):
    value = max(0.0, min(1.0, value))
    return value * value * (3 - 2 * value)


def pulse(frame, start, peak, end):
    if frame < start or frame > end:
        return 0.0
    if frame <= peak:
        return smooth((frame - start) / (peak - start))
    return smooth((end - frame) / (end - peak))


schedule = {'Index': (6, 13, 20), 'Middle': (19, 26, 33),
            'Ring': (32, 39, 46), 'Pinky': (45, 52, 59),
            'Thumb': (58, 65, 72)}
for frame in range(1, 97):
    for side in ('L', 'R'):
        for name, (start, peak, end) in schedule.items():
            individual = pulse(frame, start, peak, end)
            together = 0 if name == 'Thumb' else pulse(frame, 74, 83, 92)
            strength = max(individual, together)
            for segment in (1, 2):
                bone = rig.pose.bones[f'{name}.{segment:02d}.{side}']
                bone.rotation_mode = 'XYZ'
                degrees = (-35 if name == 'Thumb' else (48 if segment == 1 else 58)) * strength
                bone.rotation_euler.x = math.radians(degrees)
                bone.keyframe_insert(data_path='rotation_euler', index=0, frame=frame)
for curve in rig.animation_data.action.fcurves:
    for point in curve.keyframe_points:
        point.interpolation = 'LINEAR'
scene.frame_start, scene.frame_end = 1, 96
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('JOINED_HAND_MOTION_STUDY', 96, 'frames at 24 fps', flush=True)
