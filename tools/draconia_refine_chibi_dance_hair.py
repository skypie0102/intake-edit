"""Reduce hair/cloak overlaps in the existing full dance.

blender -b DANCE.blend --python-exit-code 1 --python tools/draconia_refine_chibi_dance_hair.py -- OUTPUT.blend
This is a motion-specific adjustment to the existing manual hair controls.
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
if not rig.animation_data or not rig.animation_data.action:
    raise ValueError('Expected an animated dance rig')


def smooth(value):
    value = max(0.0, min(1.0, value))
    return value * value * (3 - 2 * value)


for frame in range(1, 193):
    scene.frame_set(frame)
    taper = smooth(min((frame - 1) / 12, (192 - frame) / 12))
    for name in ('Hair.C', 'Hair.L', 'Hair.R'):
        bone = rig.pose.bones[name]
        if bone.rotation_mode != 'XYZ':
            raise ValueError(f'Expected Euler hair control: {name}')
        bone.rotation_euler.x += math.radians(15 * taper)
        bone.keyframe_insert(data_path='rotation_euler', index=0, frame=frame)
for curve in rig.animation_data.action.fcurves:
    if curve.data_path in {f'pose.bones["Hair.{side}"].rotation_euler' for side in ('C', 'L', 'R')} and curve.array_index == 0:
        for point in curve.keyframe_points:
            point.interpolation = 'LINEAR'
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('CHIBI_DANCE_HAIR_REFINED', flush=True)
