"""Move the eye frame down during the experimental blink.

blender -b BLINK.blend --python-exit-code 1 --python tools/draconia_refine_blink.py -- OUTPUT.blend

The input is the study made by draconia_add_blink_study.py. The face's
sclera is painted on the head mesh, so a descending shader mask covers it;
the separate eye-frame mesh and iris meshes close with shape keys. This is
still a stylized, Blender-specific approximation rather than eyelid anatomy.
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
    raise ValueError('Input must be in its open-eyed default state')

# Close the irises toward the lower eyelid, not toward their own centers.
for name in ('Circle', 'Circle.001', 'Circle.002'):
    obj = bpy.data.objects[name]
    basis = obj.data.shape_keys.key_blocks['Basis']
    blink = obj.data.shape_keys.key_blocks['Blink']
    inverse = obj.matrix_world.inverted()
    for i in range(len(obj.data.vertices)):
        point = obj.matrix_world @ basis.data[i].co
        point.z = 1.615 + (point.z - 1.67) * .015
        blink.data[i].co = inverse @ point

# The dark eye frame is its own mesh. Lower and flatten it into a closed-lid
# line; leave enough thickness and curvature for the lashes to remain visible.
outline = bpy.data.objects['Plane.004']
if outline.data.shape_keys:
    raise ValueError('Eye-frame mesh already has shape keys')
basis = outline.shape_key_add(name='Basis')
blink = outline.shape_key_add(name='Blink')
inverse = outline.matrix_world.inverted()
for i in range(len(outline.data.vertices)):
    point = outline.matrix_world @ basis.data[i].co
    point.z = 1.617 + (point.z - 1.69) * .45
    point.y -= .018
    blink.data[i].co = inverse @ point
driver = blink.driver_add('value').driver
variable = driver.variables.new()
variable.name = 'blink'
variable.type = 'SINGLE_PROP'
variable.targets[0].id = rig
variable.targets[0].data_path = '["Blink"]'
driver.expression = 'blink'

# Replace uniform fading of the painted whites with a descending cover. The
# generated coordinate stays tied to the head's local mesh rather than world Z.
nodes = bpy.data.materials['Head'].node_tree
control = nodes.nodes['Blink control']
fac = control.outputs[0].links[0].to_node
coord = nodes.nodes.new('ShaderNodeTexCoord')
separate = nodes.nodes.new('ShaderNodeSeparateXYZ')
nodes.links.new(coord.outputs['Generated'], separate.inputs[0])
travel = nodes.nodes.new('ShaderNodeMath')
travel.operation = 'MULTIPLY'
travel.inputs[1].default_value = .29
nodes.links.new(control.outputs[0], travel.inputs[0])
threshold = nodes.nodes.new('ShaderNodeMath')
threshold.operation = 'SUBTRACT'
threshold.inputs[0].default_value = .53
nodes.links.new(travel.outputs[0], threshold.inputs[1])
cover = nodes.nodes.new('ShaderNodeMath')
cover.name = 'Descending eyelid cover'
cover.operation = 'GREATER_THAN'
nodes.links.new(separate.outputs['Z'], cover.inputs[0])
nodes.links.new(threshold.outputs[0], cover.inputs[1])
nodes.links.new(cover.outputs[0], fac.inputs[1])

rig.update_tag()
bpy.context.view_layer.update()
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('BLINK_REFINEMENT_CREATED', flush=True)
