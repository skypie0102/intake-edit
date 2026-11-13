"""Render static diagnostic poses without saving changes to the scene.

Run: blender -b INPUT.blend --python tools/draconia_render_cloak_samples.py -- OUTPUT_DIR
"""
import bpy
import math
import sys
from pathlib import Path
from mathutils import Vector

out = Path(sys.argv[sys.argv.index('--') + 1]).resolve()
out.mkdir(parents=True, exist_ok=True)
scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
scene.render.engine = 'CYCLES'
scene.cycles.samples = 16
scene.render.resolution_x = scene.render.resolution_y = 512
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.film_transparent = True
poses = {
    'neutral': {},
    'hem_sweep': {n: (15, 0, 0) for n in ('CloakHem.L', 'CloakHem.C', 'CloakHem.R')},
    'hem_sides': {'CloakHem.L': (0, 0, -15), 'CloakHem.R': (0, 0, 15)},
    'combined': {'Thigh.R': (25, 0, 0), 'Torso': (0, 0, 12),
                 'CloakHem.L': (10, 0, -8), 'CloakHem.C': (10, 0, 0),
                 'CloakHem.R': (10, 0, 8)},
}
for name, rotations in poses.items():
    for bone in rig.pose.bones:
        bone.rotation_mode = 'XYZ'
        bone.rotation_euler = (0, 0, 0)
    for bone, angles in rotations.items():
        rig.pose.bones[bone].rotation_euler = [math.radians(a) for a in angles]
    for view, location in (('front', (0, -6, 1.25)), ('angle', (4, -5, 1.5)),
                           ('rear', (0, 6, 1.25))):
        camera = scene.camera
        camera.location = location
        camera.rotation_euler = (Vector((0, 0, 1.17)) - camera.location).to_track_quat('-Z', 'Y').to_euler()
        camera.data.ortho_scale = 2.65
        scene.render.filepath = str(out / f'{name}_{view}.png')
        bpy.ops.render.render(write_still=True)
