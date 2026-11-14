"""Render the complete experimental hand study from a front and angled view.

blender -b HAND_MOTION.blend --python-exit-code 1 --python tools/draconia_render_hand_study.py -- OUTPUT_DIR [front|angle]
"""
import sys
from pathlib import Path

import bpy
from mathutils import Vector

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 2 or args[1] not in ('front', 'angle'):
    raise ValueError('Expected OUTPUT_DIR front|angle')
root, view = Path(args[0]).resolve(), args[1]
out = root / view
out.mkdir(parents=True, exist_ok=True)
scene = bpy.context.scene
if (scene.frame_start, scene.frame_end, scene.render.fps) != (1, 96, 24):
    raise ValueError('Expected the 96-frame hand study')
scene.render.engine = 'CYCLES'
scene.cycles.samples = 12
scene.render.resolution_x = scene.render.resolution_y = 256
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.render.image_settings.file_format = 'PNG'
target = Vector((-.38, -.07, .80))
offset = Vector((0, -5, 0) if view == 'front' else (-2, -5, 1))
camera = scene.camera
camera.location = target + offset
camera.rotation_euler = (target - camera.location).to_track_quat('-Z', 'Y').to_euler()
camera.data.ortho_scale = .32
for frame in range(1, 97):
    scene.frame_set(frame)
    scene.render.filepath = str(out / f'{frame:03}.png')
    bpy.ops.render.render(write_still=True)
