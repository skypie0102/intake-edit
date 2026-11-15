"""Give the chibi hands gentle shading while preserving the skin texture.

blender -b CHIBI.blend --python-exit-code 1 --python tools/draconia_shade_chibi_hands.py -- OUTPUT.blend
Only the two hand meshes receive the new material and smooth surface normals.
"""
import sys
from pathlib import Path

import bpy
from mathutils import Vector

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 1:
    raise ValueError('Expected OUTPUT.blend')
output = Path(args[0]).resolve()
if output.exists() or output == Path(bpy.data.filepath).resolve():
    raise ValueError('Output must be a new file')
hands = [bpy.data.objects['Articulated hand study ' + side] for side in ('L', 'R')]
source = bpy.data.materials['Main Body']
if bpy.data.materials.get('Chibi hand soft shading'):
    raise ValueError('Hand shading material already exists')
if any(len(obj.data.materials) != 1 or obj.data.materials[0] != source for obj in hands):
    raise ValueError('Expected the original skin material on both hands')
material = source.copy()
material.name = 'Chibi hand soft shading'
nodes = material.node_tree.nodes
links = material.node_tree.links
emission = next(n for n in nodes if n.type == 'EMISSION')
texture = next(n for n in nodes if n.type == 'TEX_IMAGE')
geometry = nodes.new('ShaderNodeNewGeometry')
direction = nodes.new('ShaderNodeVectorMath')
direction.name = 'Soft hand light direction'
direction.operation = 'DOT_PRODUCT'
direction.inputs[1].default_value = Vector((-.4, -.7, .6)).normalized()
links.new(geometry.outputs['Normal'], direction.inputs[0])
level = nodes.new('ShaderNodeMath')
level.name = 'Soft hand depth'
level.operation = 'MULTIPLY_ADD'
level.inputs[1].default_value = .22
level.inputs[2].default_value = .78
links.new(direction.outputs['Value'], level.inputs[0])
tint = nodes.new('ShaderNodeMixRGB')
tint.name = 'Skin texture with soft volume'
tint.blend_type = 'MULTIPLY'
tint.inputs[0].default_value = 1
links.new(texture.outputs['Color'], tint.inputs[1])
links.new(level.outputs[0], tint.inputs[2])
links.new(tint.outputs['Color'], emission.inputs['Color'])
for obj in hands:
    obj.data.materials.clear()
    obj.data.materials.append(material)
    for polygon in obj.data.polygons:
        polygon.use_smooth = True
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('CHIBI_HAND_SOFT_SHADING_ADDED', flush=True)
