"""Render 24-frame sleeve diagnostics from two views without saving the scene.

blender -b RIG.blend --python-exit-code 1 --python tools/draconia_render_sleeve_loops.py -- OUTPUT_DIR
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
scene.cycles.samples = 12
scene.render.resolution_x = scene.render.resolution_y = 256
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.film_transparent = True
for side in ('wand', 'other'):
    for view, location in (('front', (0, -6, 1.25)), ('angle', (4, -5, 1.5))):
        directory = out / f'{side}_{view}'
        directory.mkdir(parents=True, exist_ok=True)
        camera = scene.camera
        camera.location = location
        camera.rotation_euler = (Vector((0, 0, 1.17)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
        camera.data.ortho_scale = 2.65
        for frame in range(24):
            t = 2 * math.pi * frame / 24
            for bone in rig.pose.bones:
                bone.rotation_mode = 'XYZ'
                bone.rotation_euler = (0, 0, 0)
            if side == 'wand':
                rig.pose.bones['Forearm.L'].rotation_euler.x = math.radians(45 * math.sin(t))
                rig.pose.bones['UpperArm.L'].rotation_euler.z = math.radians(8 * math.sin(t))
            else:
                rig.pose.bones['Forearm.R'].rotation_euler.x = math.radians(50 * math.sin(t))
                rig.pose.bones['UpperArm.R'].rotation_euler.z = math.radians(-22 * (1 - math.cos(t)) / 2)
            scene.render.filepath = str(directory / f'{frame:02d}.png')
            bpy.ops.render.render(write_still=True)
