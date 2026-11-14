"""Animate moderate wrist turns on the arms-down clearance rig.

blender -b ARM_CLEARANCE.blend --python-exit-code 1 --python tools/draconia_make_wrist_clearance_study.py -- OUTPUT.blend
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
    raise ValueError('Expected an unanimated rig')
if rig.get('NeutralArmClearance') != 1.0:
    raise ValueError('Expected enabled neutral arm clearance')
rig.animation_data_create()
rig.animation_data.action = bpy.data.actions.new('Wrist clearance - four seconds')
scene.render.fps = 24
for frame in range(1, 97):
    # Three one-second single-axis cycles, then a neutral hold for inspection.
    block = (frame - 1) // 24
    phase = (frame - 1) % 24
    angles = [0.0, 0.0, 0.0]
    if block < 3:
        angles[block] = math.radians(15) * math.sin(2 * math.pi * phase / 23)
    for side in ('L', 'R'):
        bone = rig.pose.bones['Hand.' + side]
        bone.rotation_mode = 'XYZ'
        for axis, angle in enumerate(angles):
            bone.rotation_euler[axis] = angle
            bone.keyframe_insert(data_path='rotation_euler', index=axis, frame=frame)
for curve in rig.animation_data.action.fcurves:
    for point in curve.keyframe_points:
        point.interpolation = 'LINEAR'
scene.frame_start, scene.frame_end = 1, 96
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('WRIST_CLEARANCE_STUDY', 96, 'frames at 24 fps', flush=True)
