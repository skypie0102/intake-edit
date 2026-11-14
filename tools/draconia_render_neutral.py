"""Render the default arms-down stance from four views, without a wand.

blender -b NEUTRAL.blend --python-exit-code 1 --python tools/draconia_render_neutral.py -- OUTPUT_DIR
"""
import bpy
import sys
from pathlib import Path
from mathutils import Vector

args = sys.argv[sys.argv.index('--')+1:]
if len(args) != 1:
    raise ValueError('Expected OUTPUT_DIR')
out = Path(args[0]).resolve()
out.mkdir(parents=True, exist_ok=True)
scene = bpy.context.scene
assert bpy.data.objects['Wand'].hide_render
scene.render.engine = 'CYCLES'
scene.cycles.samples = 16
scene.render.resolution_x = scene.render.resolution_y = 512
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.film_transparent = True
for view, location in (('front', (0, -6, 1.25)), ('angle', (4, -5, 1.5)),
                       ('side', (6, 0, 1.25)), ('rear', (0, 6, 1.25))):
    camera = scene.camera
    camera.location = location
    camera.rotation_euler = (Vector((0, 0, 1.17)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
    camera.data.ortho_scale = 2.65
    scene.render.filepath = str(out / f'{view}.png')
    bpy.ops.render.render(write_still=True)
