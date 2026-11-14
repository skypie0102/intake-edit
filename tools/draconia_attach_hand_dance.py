"""Attach the existing full dance to the separate joined-hand study rig.

blender -b --python-exit-code 1 --python tools/draconia_attach_hand_dance.py -- HAND_RIG.blend DANCE.blend OUTPUT.blend
Finger bones have no dance keys and remain open for wrist/sleeve review.
"""
import sys
from pathlib import Path

import bpy

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 3:
    raise ValueError('Expected HAND_RIG.blend DANCE.blend OUTPUT.blend')
rig_path, dance_path, output = (Path(p).resolve() for p in args)
if output.exists() or output in (rig_path, dance_path):
    raise ValueError('Output must be a new file')
bpy.ops.wm.open_mainfile(filepath=str(dance_path))
source = bpy.data.objects['Draconia Body Rig - experimental']
scene = bpy.context.scene
if (scene.frame_start, scene.frame_end, scene.render.fps) != (1, 192, 24):
    raise ValueError('Expected the complete dance')
if not source.animation_data or not source.animation_data.action:
    raise ValueError('Missing dance action')
action_name = source.animation_data.action.name
modes = {bone.name: bone.rotation_mode for bone in source.pose.bones}
bpy.ops.wm.open_mainfile(filepath=str(rig_path))
rig = bpy.data.objects['Draconia Body Rig - experimental']
if not set(modes).issubset({bone.name for bone in rig.pose.bones}):
    raise ValueError('Hand rig is missing dance bones')
if rig.animation_data and rig.animation_data.action:
    raise ValueError('Expected the unanimated hand rig')
with bpy.data.libraries.load(str(dance_path), link=False) as (source_data, target_data):
    if action_name not in source_data.actions:
        raise ValueError('Dance action is missing')
    target_data.actions = [action_name]
for bone in rig.pose.bones:
    if bone.name in modes:
        bone.rotation_mode = modes[bone.name]
rig.animation_data_create()
rig.animation_data.action = target_data.actions[0]
scene = bpy.context.scene
scene.frame_start, scene.frame_end, scene.render.fps = 1, 192, 24
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('JOINED_HAND_DANCE_CREATED', flush=True)
