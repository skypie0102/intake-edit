"""Replace shrinking irises with a descending eyelid coverage mask.

blender -b INPUT.blend --python-exit-code 1 --python tools/draconia_cover_irises.py -- OUTPUT.blend

Input is the fuller-lid blink study. The eye whites are painted on the head,
while the irises are separate meshes, so the two materials use matching
head-local coverage thresholds. The irises retain their original geometry and
gaze controls throughout the blink. This Blender shader-based study is not
portable eyelid geometry.
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
head = bpy.data.objects['Head']
if rig['Blink'] != 0:
    raise ValueError('Input must have Blink at zero')

# Preserve iris size and position; do not suppress the existing gaze controls
# when the eyelid closes. This removes the earlier shrinking approximation.
for name in ('Circle', 'Circle.001', 'Circle.002'):
    obj = bpy.data.objects[name]
    keys = obj.data.shape_keys.key_blocks
    for point, original in zip(keys['Blink'].data, keys['Basis'].data):
        point.co = original.co
    for curve in obj.data.shape_keys.animation_data.drivers:
        if 'Gaze_' in curve.data_path:
            if '*(1-closing)' not in curve.driver.expression:
                raise ValueError('Unexpected gaze driver expression')
            curve.driver.expression = curve.driver.expression.replace('*(1-closing)', '')
            for variable in list(curve.driver.variables):
                if variable.name == 'closing':
                    curve.driver.variables.remove(variable)

# Bring the eye-frame endpoint down to the lower edge of the open eye. This
# makes the dark frame follow the coverage edge instead of floating above it.
frame = bpy.data.objects['Plane.004'].data.shape_keys.key_blocks['Blink']
for point in frame.data:
    point.co.z -= .035

# The empty follows the Head bone and provides a stable head-local position
# for the iris shader, including when the character turns her head.
reference = bpy.data.objects.new('Eyelid mask reference', None)
bpy.context.collection.objects.link(reference)
reference.parent = rig
reference.parent_type = 'BONE'
reference.parent_bone = 'Head'
reference.matrix_world = head.matrix_world.copy()
bpy.context.view_layer.update()

material = bpy.data.materials['Face and Eyes'].copy()
material.name = 'Iris - eyelid coverage'
nt = material.node_tree
output_node = nt.nodes['Material Output']
emission = nt.nodes['Sketchfab-style unlit color']
coordinates = nt.nodes.new('ShaderNodeTexCoord')
coordinates.object = reference
separate = nt.nodes.new('ShaderNodeSeparateXYZ')
nt.links.new(coordinates.outputs['Object'], separate.inputs[0])
control = nt.nodes.new('ShaderNodeValue')
control.name = 'Blink control'
driver = control.outputs[0].driver_add('default_value').driver
variable = driver.variables.new()
variable.name = 'blink'
variable.type = 'SINGLE_PROP'
variable.targets[0].id = rig
variable.targets[0].data_path = '["Blink"]'
driver.expression = 'blink'
threshold = nt.nodes.new('ShaderNodeMath')
threshold.operation = 'SUBTRACT'
cover = nt.nodes.new('ShaderNodeMath')
cover.name = 'Eyelid coverage'
cover.operation = 'GREATER_THAN'
nt.links.new(separate.outputs['Z'], cover.inputs[0])
nt.links.new(threshold.outputs[0], cover.inputs[1])
transparent = nt.nodes.new('ShaderNodeBsdfTransparent')
mix = nt.nodes.new('ShaderNodeMixShader')
nt.links.new(cover.outputs[0], mix.inputs[0])
nt.links.new(emission.outputs[0], mix.inputs[1])
nt.links.new(transparent.outputs[0], mix.inputs[2])
nt.links.new(mix.outputs[0], output_node.inputs['Surface'])
for name in ('Circle', 'Circle.001', 'Circle.002'):
    bpy.data.objects[name].data.materials[0] = material

# The original head cover reads normalized Generated Z; the iris cover reads
# head-local Z. Set both thresholds to the same rest-space height:
# 1.70 - .11 * blink + .06 * (1 - blink)^8. The opening term ensures every
# iris is visible at zero, and the lid reaches the lower edge when closed.
head_z = [(head.matrix_world @ v.co).z for v in head.data.vertices]
head_min, head_span = min(head_z), max(head_z) - min(head_z)
for mat, comparison_name, scale, offset in (
        (bpy.data.materials['Head'], 'Descending eyelid cover', head_span, head_min),
        (material, 'Eyelid coverage', 1, head.matrix_world.translation.z)):
    tree = mat.node_tree
    value = tree.nodes['Blink control']
    boundary = tree.nodes[comparison_name].inputs[1].links[0].from_node
    boundary.inputs[0].default_value = (1.70 - offset) / scale
    linear = tree.nodes.new('ShaderNodeMath')
    linear.operation = 'MULTIPLY'
    linear.inputs[1].default_value = .11 / scale
    tree.links.new(value.outputs[0], linear.inputs[0])
    inverse = tree.nodes.new('ShaderNodeMath')
    inverse.operation = 'SUBTRACT'
    inverse.inputs[0].default_value = 1
    tree.links.new(value.outputs[0], inverse.inputs[1])
    power = tree.nodes.new('ShaderNodeMath')
    power.operation = 'POWER'
    power.inputs[1].default_value = 8
    tree.links.new(inverse.outputs[0], power.inputs[0])
    opening = tree.nodes.new('ShaderNodeMath')
    opening.operation = 'MULTIPLY'
    opening.inputs[1].default_value = .06 / scale
    tree.links.new(power.outputs[0], opening.inputs[0])
    difference = tree.nodes.new('ShaderNodeMath')
    difference.operation = 'SUBTRACT'
    tree.links.new(linear.outputs[0], difference.inputs[0])
    tree.links.new(opening.outputs[0], difference.inputs[1])
    tree.links.new(difference.outputs[0], boundary.inputs[1])

rig.update_tag()
bpy.context.view_layer.update()
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('IRIS_COVERAGE_CREATED', flush=True)
