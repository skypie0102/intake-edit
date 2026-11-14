"""Refine the closed-lid silhouette on the revised blink study.

blender -b BLINK.blend --python-exit-code 1 --python tools/draconia_refine_blink_corners.py -- OUTPUT.blend

The side wings are flattened with the lid; the tiny detached lower-lash
triangles and the iris geometry shrink away at full closure. The open shape
and pre-existing blink drivers remain unchanged.
"""
import sys
from pathlib import Path

import bpy

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 1:
    raise ValueError('Expected OUTPUT.blend')
output = Path(args[0]).resolve()
if output.exists() or output == Path(bpy.data.filepath).resolve():
    raise ValueError('Output must be a new file')
rig = bpy.data.objects['Draconia Body Rig - experimental']
if rig['Blink'] != 0:
    raise ValueError('Input must have Blink at zero')

# Flatten the frame's outer wings and retain a shallow arc in the closed lid.
outline = bpy.data.objects['Plane.004']
basis = outline.data.shape_keys.key_blocks['Basis']
blink = outline.data.shape_keys.key_blocks['Blink']
inverse = outline.matrix_world.inverted()
for i in range(len(outline.data.vertices)):
    point = outline.matrix_world @ basis.data[i].co
    center = -.150 if point.x < .0047 else .220
    horizontal = max(-1, min(1, (point.x - center) / .110))
    thickness = .65 - .45 * abs(horizontal)
    point.z = 1.610 + .012 * (1 - horizontal * horizontal) + thickness * (point.z - 1.69)
    point.y -= .018
    blink.data[i].co = inverse @ point

# Collapse each iris to a point when closed, avoiding a green line under the lid.
for name in ('Circle', 'Circle.001', 'Circle.002'):
    obj = bpy.data.objects[name]
    basis = obj.data.shape_keys.key_blocks['Basis']
    blink = obj.data.shape_keys.key_blocks['Blink']
    inverse = obj.matrix_world.inverted()
    for side in (-1, 1):
        indices = [v.index for v in obj.data.vertices
                   if ((obj.matrix_world @ basis.data[v.index].co).x - .0047) * side > 0]
        if not indices:
            raise ValueError(f'Missing eye geometry on {name}, side {side}')
        center_x = sum((obj.matrix_world @ basis.data[i].co).x for i in indices) / len(indices)
        for i in indices:
            point = obj.matrix_world @ basis.data[i].co
            point.x = center_x
            point.z = 1.610
            blink.data[i].co = inverse @ point

# Each of these meshes is two small, detached triangular lower lashes. Shrink
# each triangle independently so they cannot float below the closed lid.
for name in ('Plane.006', 'Plane.007'):
    obj = bpy.data.objects[name]
    if obj.data.shape_keys or len(obj.data.polygons) != 2:
        raise ValueError(f'Unexpected lower-lash mesh {name}')
    basis = obj.shape_key_add(name='Basis')
    blink = obj.shape_key_add(name='Blink')
    for face in obj.data.polygons:
        indices = list(face.vertices)
        center = sum((basis.data[i].co for i in indices), basis.data[indices[0]].co * 0) / len(indices)
        for i in indices:
            blink.data[i].co = center
    driver = blink.driver_add('value').driver
    variable = driver.variables.new()
    variable.name = 'blink'
    variable.type = 'SINGLE_PROP'
    variable.targets[0].id = rig
    variable.targets[0].data_path = '["Blink"]'
    driver.expression = 'blink'

rig.update_tag()
bpy.context.view_layer.update()
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('BLINK_CORNERS_REFINED', flush=True)
