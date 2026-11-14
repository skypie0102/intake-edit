"""Create a 48-frame reach, head-turn and manual hair-control diagnostic.

blender -b --python-exit-code 1 --python tools/draconia_make_motion_study.py -- RIG.blend OUTPUT.blend
"""
import bpy
import math
import sys
from pathlib import Path

args = sys.argv[sys.argv.index('--')+1:]
if len(args) != 2:
    raise ValueError('Expected RIG.blend OUTPUT.blend')
source, output = (Path(p).resolve() for p in args)
if source == output or output.exists():
    raise ValueError('Output must be a new file')
bpy.ops.wm.open_mainfile(filepath=str(source))
scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
if rig.animation_data and rig.animation_data.action:
    raise ValueError('Expected an unanimated rig')
rig.animation_data_create()
rig.animation_data.action = bpy.data.actions.new('Reach and hair controls diagnostic')
scene.render.fps = 24
for frame in range(1, 49):
    t = 2*math.pi*(frame-1)/48
    reach = (1-math.cos(t))/2
    swing = math.sin(t)
    values = {'Torso': (0, 0, 4*swing), 'Head': (0, 0, 9*swing),
              'UpperArm.L': (0, 0, 55*reach), 'Forearm.L': (10*reach, 0, 0),
              'Hair.C': (7*reach, 0, 0), 'Hair.L': (9*reach, 0, 0), 'Hair.R': (5*reach, 0, 0)}
    for name, angles in values.items():
        bone = rig.pose.bones[name]
        bone.rotation_mode = 'XYZ'
        bone.rotation_euler = [math.radians(a) for a in angles]
        bone.keyframe_insert(data_path='rotation_euler', frame=frame)
for curve in rig.animation_data.action.fcurves:
    for key in curve.keyframe_points:
        key.interpolation = 'LINEAR'
scene.frame_start, scene.frame_end = 1, 48
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('MOTION_STUDY 48 FRAMES AT 24 FPS', flush=True)
