"""Key the front-left hair lock clear of the raised sleeve in the dance.

blender -b INPUT.blend --python-exit-code 1 --python tools/draconia_refine_front_lock_dance.py -- OUTPUT.blend
This is a manually keyed, animation-specific correction.
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
if (scene.frame_start, scene.frame_end, scene.render.fps) != (1, 192, 24) or not rig.animation_data or not rig.animation_data.action:
    raise ValueError('Expected the complete animated dance')
for name in ('Hair.Front.L', 'Hair.Front.R'):
    if name not in rig.pose.bones:
        raise ValueError('Missing front hair controls')
bone = rig.pose.bones['Hair.Front.L']
bone.rotation_mode = 'XYZ'
for frame, angle in ((1, 0), (88, 0), (97, -20), (103, -30), (108, -25), (116, 0), (192, 0)):
    bone.rotation_euler.z = math.radians(angle)
    bone.keyframe_insert(data_path='rotation_euler', index=2, frame=frame)
for curve in rig.animation_data.action.fcurves:
    if curve.data_path == 'pose.bones["Hair.Front.L"].rotation_euler' and curve.array_index == 2:
        for point in curve.keyframe_points:
            point.interpolation = 'LINEAR'
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('FRONT_LOCK_DANCE_CORRECTION_KEYED', flush=True)
