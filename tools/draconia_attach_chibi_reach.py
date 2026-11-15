"""Attach the existing reach study to the current chibi rig.

blender -b --python-exit-code 1 --python tools/draconia_attach_chibi_reach.py -- CHIBI.blend REACH.blend OUTPUT.blend
The shoulder-clearance control is set to 0.6 (a three-degree offset) to keep
the new hands clear of the cloak in this particular reach.
"""
import sys
from pathlib import Path

import bpy

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 3:
    raise ValueError('Expected CHIBI.blend REACH.blend OUTPUT.blend')
rig_path, reach_path, output = (Path(p).resolve() for p in args)
if output.exists() or output in (rig_path, reach_path):
    raise ValueError('Output must be a new file')
bpy.ops.wm.open_mainfile(filepath=str(reach_path))
source = bpy.data.objects['Draconia Body Rig - experimental']
scene = bpy.context.scene
if (scene.frame_start, scene.frame_end, scene.render.fps) != (1, 48, 24):
    raise ValueError('Expected the complete reach study')
if not source.animation_data or not source.animation_data.action:
    raise ValueError('Missing reach action')
action_name = source.animation_data.action.name
modes = {bone.name: bone.rotation_mode for bone in source.pose.bones}
bpy.ops.wm.open_mainfile(filepath=str(rig_path))
rig = bpy.data.objects['Draconia Body Rig - experimental']
if not set(modes).issubset({bone.name for bone in rig.pose.bones}):
    raise ValueError('Chibi rig is missing reach bones')
if rig.animation_data and rig.animation_data.action:
    raise ValueError('Expected an unanimated chibi rig')
with bpy.data.libraries.load(str(reach_path), link=False) as (source_data, target_data):
    if action_name not in source_data.actions:
        raise ValueError('Reach action is missing')
    target_data.actions = [action_name]
for bone in rig.pose.bones:
    if bone.name in modes:
        bone.rotation_mode = modes[bone.name]
rig['NeutralArmClearance'] = 0.6
rig.animation_data_create()
rig.animation_data.action = target_data.actions[0]
scene = bpy.context.scene
scene.frame_start, scene.frame_end, scene.render.fps = 1, 48, 24
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('CHIBI_REACH_ATTACHED', flush=True)
