"""Disable the neutral-only arm offset on an existing complete dance.

blender -b INPUT.blend --python-exit-code 1 --python tools/draconia_disable_arm_clearance_for_dance.py -- OUTPUT.blend
"""
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
if 'NeutralArmClearance' not in rig or not rig.animation_data or not rig.animation_data.action:
    raise ValueError('Expected the dance with neutral arm controls')
if any(curve.data_path == '["NeutralArmClearance"]' for curve in rig.animation_data.action.fcurves):
    raise ValueError('Neutral arm control is already animated')
for frame in (1, 192):
    rig['NeutralArmClearance'] = 0.0
    rig.keyframe_insert(data_path='["NeutralArmClearance"]', frame=frame)
for curve in rig.animation_data.action.fcurves:
    if curve.data_path == '["NeutralArmClearance"]':
        for point in curve.keyframe_points:
            point.interpolation = 'LINEAR'
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('DANCE_NEUTRAL_ARM_CLEARANCE_DISABLED', flush=True)
