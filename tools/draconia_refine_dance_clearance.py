"""Key manual hair clearance and the approved blink over the full dance.

blender -b INPUT.blend --python-exit-code 1 --python tools/draconia_refine_dance_clearance.py -- OUTPUT.blend
This action-specific cleanup is not automatic collision detection or physics.
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
if (scene.frame_start, scene.frame_end, scene.render.fps) != (1, 192, 24):
    raise ValueError('Expected the complete dance')
if 'Blink' not in rig or not rig.animation_data or not rig.animation_data.action:
    raise ValueError('Expected a rig with blink and a dance action')
action = rig.animation_data.action
if any(curve.data_path.startswith('pose.bones["Hair.') or curve.data_path == '["Blink"]' for curve in action.fcurves):
    raise ValueError('Expected unkeyed hair and blink controls')
names = ('Hair.C', 'Hair.L', 'Hair.R')
for name in names:
    bone = rig.pose.bones[name]
    if bone.rotation_quaternion.angle > 1e-5:
        raise ValueError('Expected unposed hair controls')
    bone.rotation_mode = 'XYZ'


def smooth(value):
    value = max(0.0, min(1.0, value))
    return value * value * (3 - 2 * value)


# Gradually introduce the correction and return to the original hair position
# at the end, avoiding a jump as the full dance loops in the preview.
for frame in range(1, 193):
    scene.frame_set(frame)
    taper = smooth(min((frame - 1) / 12, (192 - frame) / 12))
    for name in names:
        bone = rig.pose.bones[name]
        bone.rotation_euler.x = math.radians(35 * taper)
        bone.keyframe_insert(data_path='rotation_euler', index=0, frame=frame)

blink_keys = [(1, 0)]
for center in (35, 83, 131, 179):
    blink_keys.extend(((center - 3, 0), (center - 1, 1), (center + 2, 0)))
blink_keys.append((192, 0))
for frame, value in blink_keys:
    rig['Blink'] = float(value)
    rig.keyframe_insert(data_path='["Blink"]', frame=frame)
for curve in action.fcurves:
    if curve.data_path.startswith('pose.bones["Hair.') or curve.data_path == '["Blink"]':
        for key in curve.keyframe_points:
            key.interpolation = 'LINEAR'
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('DANCE_HAIR_CLEARANCE_AND_BLINK_KEYED', flush=True)
