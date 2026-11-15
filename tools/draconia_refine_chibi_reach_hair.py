"""Adjust the existing hair-control keys for the chibi reach's cloak shape.

blender -b REACH.blend --python-exit-code 1 --python tools/draconia_refine_chibi_reach_hair.py -- OUTPUT.blend
This is a correction for the 48-frame reach, not automatic hair physics.
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
    raise ValueError('Expected the 48-frame reach')
if not rig.animation_data or not rig.animation_data.action or abs(rig['NeutralArmClearance'] - .6) > 1e-6:
    raise ValueError('Expected the chibi reach with three-degree shoulder clearance')
for frame in range(1, 49):
    scene.frame_set(frame)
    reach = (1 - math.cos(2 * math.pi * (frame - 1) / 48)) / 2
    for name in ('Hair.C', 'Hair.L', 'Hair.R'):
        bone = rig.pose.bones[name]
        bone.rotation_euler.x += math.radians(15 * reach)
        bone.keyframe_insert(data_path='rotation_euler', index=0, frame=frame)
for curve in rig.animation_data.action.fcurves:
    if curve.data_path.startswith('pose.bones["Hair.') and curve.array_index == 0:
        for key in curve.keyframe_points:
            key.interpolation = 'LINEAR'
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('CHIBI_REACH_HAIR_REFINED', flush=True)
