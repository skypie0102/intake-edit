"""Render all unique frames of the 8-second CC0 dance for the preview pack.

blender -b DANCE.blend --python-exit-code 1 --python tools/draconia_render_four_bar.py -- OUTPUT_DIR [sample]
Pass 'sample' to render only the eight one-second check frames.
"""
import bpy
import sys
from pathlib import Path
from mathutils import Vector
args = sys.argv[sys.argv.index('--') + 1:]
out = Path(args[0]).resolve()
out.mkdir(parents=True, exist_ok=True)
scene = bpy.context.scene
scene.render.engine = 'CYCLES'
scene.cycles.samples = 12
scene.render.resolution_x = scene.render.resolution_y = 256
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.film_transparent = True
camera = scene.camera
camera.location = (0, -6, 1.25)
camera.rotation_euler = (Vector((0, 0, 1.17)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
camera.data.ortho_scale = 2.65
frames = range(1, 193, 24) if 'sample' in args else range(1, 193)
for frame in frames:
    scene.frame_set(frame)
    scene.render.filepath = str(out / f'{frame:03}.png')
    bpy.ops.render.render(write_still=True)
