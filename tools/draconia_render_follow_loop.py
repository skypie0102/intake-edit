"""Render the leg-follow diagnostic at two views; does not save the scene.

blender -b RIG.blend --python-exit-code 1 --python tools/draconia_render_follow_loop.py -- OUTPUT_DIR
"""
import bpy
import math
import sys
from pathlib import Path
from mathutils import Vector

out = Path(sys.argv[sys.argv.index('--') + 1]).resolve()
scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
scene.render.engine = 'CYCLES'
scene.cycles.samples = 16
scene.render.resolution_x = scene.render.resolution_y = 256
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.film_transparent = True
for view, location in (('front', (0, -6, 1.25)), ('angle', (4, -5, 1.5))):
    directory = out / view
    directory.mkdir(parents=True, exist_ok=True)
    camera = scene.camera
    camera.location = location
    camera.rotation_euler = (Vector((0, 0, 1.17)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
    camera.data.ortho_scale = 2.65
    for frame in range(24):
        s = math.sin(2 * math.pi * frame / 24)
        for bone in rig.pose.bones:
            bone.rotation_mode = 'XYZ'
            bone.rotation_euler = (0, 0, 0)
        rig.pose.bones['Thigh.R'].rotation_euler.x = math.radians(50 * s)
        rig.pose.bones['Shin.R'].rotation_euler.x = math.radians(-15 * max(0, s))
        scene.render.filepath = str(directory / f'{frame:02d}.png')
        bpy.ops.render.render(write_still=True)
