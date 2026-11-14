"""Render the retargeted one-second dance from front and angled views.

blender -b DANCE.blend --python-exit-code 1 --python tools/draconia_render_cc0_dance.py -- OUTPUT_DIR
Twenty samples at 50 ms preserve the source's one-second duration within the
installed preview app's minimum frame duration.
"""
import bpy
import math
import sys
from pathlib import Path
from mathutils import Vector

out = Path(sys.argv[sys.argv.index('--') + 1]).resolve()
scene = bpy.context.scene
scene.render.engine = 'CYCLES'
scene.cycles.samples = 12
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
    for i in range(20):
        time = 1 + 1.5*i
        frame = math.floor(time)
        scene.frame_set(frame, subframe=time-frame)
        scene.render.filepath = str(directory / f'{i:02}.png')
        bpy.ops.render.render(write_still=True)
