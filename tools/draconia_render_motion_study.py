"""Render every frame of a 48-frame diagnostic from front and rear."""
import bpy
import sys
from pathlib import Path
from mathutils import Vector

out = Path(sys.argv[sys.argv.index('--')+1]).resolve()
scene = bpy.context.scene
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 48, 24)
scene.render.engine = 'CYCLES'
scene.cycles.samples = 12
scene.render.resolution_x = scene.render.resolution_y = 256
scene.render.resolution_percentage = 100
scene.render.film_transparent = True
scene.render.image_settings.file_format = 'PNG'
for view, location in (('front', (0, -6, 1.25)), ('rear', (0, 6, 1.25))):
    directory = out / view
    directory.mkdir(parents=True, exist_ok=True)
    camera = scene.camera
    camera.location = location
    camera.rotation_euler = (Vector((0, 0, 1.17))-camera.location).to_track_quat('-Z', 'Y').to_euler()
    camera.data.ortho_scale = 2.65
    for frame in range(1, 49):
        scene.frame_set(frame)
        scene.render.filepath = str(directory / f'{frame:03}.png')
        bpy.ops.render.render(write_still=True)
