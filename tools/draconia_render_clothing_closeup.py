"""Render all 192 dance frames in a closer front view of the clothing.

blender -b DANCE.blend --python-exit-code 1 --python tools/draconia_render_clothing_closeup.py -- OUTPUT_DIR
"""
import sys
from pathlib import Path

import bpy
from mathutils import Vector

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 1:
    raise ValueError('Expected OUTPUT_DIR')
directory = Path(args[0]).resolve()
directory.mkdir(parents=True, exist_ok=True)
scene = bpy.context.scene
if (scene.frame_start, scene.frame_end, scene.render.fps) != (1, 192, 24):
    raise ValueError('Expected the full dance')
scene.render.engine = 'CYCLES'
scene.cycles.samples = 12
scene.render.resolution_x = scene.render.resolution_y = 256
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.render.image_settings.file_format = 'PNG'
camera = scene.camera
camera.location = (0, -6, 1.18)
camera.rotation_euler = (Vector((0, 0, 1.18)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
camera.data.ortho_scale = 1.5
for frame in range(1, 193):
    scene.frame_set(frame)
    scene.render.filepath = str(directory / f'{frame:03}.png')
    bpy.ops.render.render(write_still=True)
