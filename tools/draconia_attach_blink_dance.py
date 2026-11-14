"""Transfer the existing full dance onto the rig with the accepted blink.

blender -b --python-exit-code 1 --python tools/draconia_attach_blink_dance.py -- RIG.blend DANCE.blend OUTPUT.blend
The source dance's per-bone rotation modes are required by its quaternion
channels and are copied along with its action.
"""
import sys
from pathlib import Path

import bpy

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 3:
    raise ValueError('Expected RIG.blend DANCE.blend OUTPUT.blend')
rig_path, dance_path, output = (Path(p).resolve() for p in args)
if output.exists() or output in (rig_path, dance_path):
    raise ValueError('Output must be a new file')
name = 'Four-bar CC0 dance - full 8 seconds'
bpy.ops.wm.open_mainfile(filepath=str(dance_path))
source_rig = bpy.data.objects['Draconia Body Rig - experimental']
source_scene = bpy.context.scene
if (source_scene.frame_start, source_scene.frame_end, source_scene.render.fps) != (1, 192, 24):
    raise ValueError('Expected the complete 192-frame dance')
if not source_rig.animation_data or not source_rig.animation_data.action or source_rig.animation_data.action.name != name:
    raise ValueError('Expected the full dance action')
modes = {bone.name: bone.rotation_mode for bone in source_rig.pose.bones}

bpy.ops.wm.open_mainfile(filepath=str(rig_path))
rig = bpy.data.objects['Draconia Body Rig - experimental']
if 'Blink' not in rig or set(modes) != {bone.name for bone in rig.pose.bones}:
    raise ValueError('Expected matching hair-control rig with blink')
if rig.animation_data and rig.animation_data.action:
    raise ValueError('Expected an unanimated target rig')
with bpy.data.libraries.load(str(dance_path), link=False) as (source, target):
    if name not in source.actions:
        raise ValueError('Missing dance action')
    target.actions = [name]
for bone in rig.pose.bones:
    bone.rotation_mode = modes[bone.name]
rig.animation_data_create()
rig.animation_data.action = target.actions[0]
scene = bpy.context.scene
scene.frame_start, scene.frame_end, scene.render.fps = 1, 192, 24
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('BLINK_DANCE_BASELINE_CREATED', flush=True)
