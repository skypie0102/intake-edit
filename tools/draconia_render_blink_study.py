"""Render two views of an experimental two-second blink study.

blender -b BLINK.blend --python-exit-code 1 --python tools/draconia_render_blink_study.py -- OUTPUT_DIR
"""
import bpy
import sys
from pathlib import Path
from mathutils import Vector

args = sys.argv[sys.argv.index('--')+1:]
if len(args) != 1:
    raise ValueError('Expected OUTPUT_DIR')
out = Path(args[0]).resolve()
scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
assert 'Blink' in rig and bpy.data.objects['Wand'].hide_render
scene.render.engine = 'CYCLES'
scene.cycles.samples = 12
scene.render.resolution_x = scene.render.resolution_y = 256
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.render.image_settings.file_format = 'PNG'
# Deliberately short blinks, with open-eye holds between them.
keys = ((1,0), (9,0), (11,.7), (12,1), (14,1), (17,0),
        (30,0), (32,.7), (33,1), (35,1), (38,0), (48,0))
def value(frame):
    for (a, x), (b, y) in zip(keys, keys[1:]):
        if a <= frame <= b:
            return x+(y-x)*(frame-a)/(b-a)
    return 0
for view, location in (('front', (0, -6, 1.72)), ('angle', (3, -6, 1.8))):
    directory = out / view
    directory.mkdir(parents=True, exist_ok=True)
    camera = scene.camera
    camera.location = location
    camera.rotation_euler = (Vector((0, 0, 1.72))-camera.location).to_track_quat('-Z', 'Y').to_euler()
    camera.data.ortho_scale = 1.1
    for frame in range(1, 49):
        rig['Blink'] = value(frame)
        rig.update_tag()
        bpy.context.view_layer.update()
        scene.render.filepath = str(directory / f'{frame:03}.png')
        bpy.ops.render.render(write_still=True)
