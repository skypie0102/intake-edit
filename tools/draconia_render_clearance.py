"""Render both sides of the complete clearance-study dance for the app.

blender -b DANCE.blend --python-exit-code 1 --python tools/draconia_render_clearance.py -- OUTPUT_DIR [front|side|rear]
"""
import bpy
import sys
from pathlib import Path
from mathutils import Vector

args = sys.argv[sys.argv.index('--')+1:]
if len(args) not in (1, 2):
    raise ValueError('Expected OUTPUT_DIR [front|side|rear]')
out = Path(args[0]).resolve()
views = [('front', (0, -6, 1.25)), ('side', (6, 0, 1.25)), ('rear', (0, 6, 1.25))]
if len(args) == 2:
    views = [v for v in views if v[0] == args[1]]
    if not views:
        raise ValueError('Unknown view')
scene = bpy.context.scene
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 192, 24)
scene.render.engine = 'CYCLES'
scene.cycles.samples = 12
scene.render.resolution_x = scene.render.resolution_y = 256
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.film_transparent = True
for view, location in views:
    directory = out / view
    directory.mkdir(parents=True, exist_ok=True)
    camera = scene.camera
    camera.location = location
    camera.rotation_euler = (Vector((0, 0, 1.17)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
    camera.data.ortho_scale = 2.65
    for frame in range(1, 193):
        scene.frame_set(frame)
        scene.render.filepath = str(directory / f'{frame:03}.png')
        bpy.ops.render.render(write_still=True)
