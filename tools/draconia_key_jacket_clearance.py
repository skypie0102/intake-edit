"""Enable the jacket-side clearance control throughout the full dance.

blender -b INPUT.blend --python-exit-code 1 --python tools/draconia_key_jacket_clearance.py -- OUTPUT.blend
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
    raise ValueError('Expected the full dance')
if 'JacketClearance' not in rig or not rig.animation_data or not rig.animation_data.action:
    raise ValueError('Expected a dance with the jacket clearance control')
if any(curve.data_path == '["JacketClearance"]' for curve in rig.animation_data.action.fcurves):
    raise ValueError('Clearance already animated')
for frame in (1, 192):
    rig['JacketClearance'] = 1.0
    rig.keyframe_insert(data_path='["JacketClearance"]', frame=frame)
for curve in rig.animation_data.action.fcurves:
    if curve.data_path == '["JacketClearance"]':
        for point in curve.keyframe_points:
            point.interpolation = 'LINEAR'
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('DANCE_JACKET_CLEARANCE_KEYED', flush=True)
