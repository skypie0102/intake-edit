"""Add a controllable side-jacket clearance shape for the cloak.

blender -b INPUT.blend --python-exit-code 1 --python tools/draconia_add_jacket_clearance.py -- OUTPUT.blend
The correction moves the jacket's forward side surfaces inward in depth; its
central panel, topology, UVs and the cloak are retained. This is not a general
collision solver.
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
scene = bpy.context.scene
scene.frame_set(1)
rig = bpy.data.objects['Draconia Body Rig - experimental']
jacket = bpy.data.objects['Jacket']
if len(jacket.data.vertices) != 93 or jacket.data.shape_keys or 'JacketClearance' in rig:
    raise ValueError('Expected the current jacket without a clearance control')
rig['JacketClearance'] = 1.0
rig.id_properties_ui('JacketClearance').update(min=0, max=1, default=1.0, description='Tuck jacket sides under the cloak')
basis = jacket.shape_key_add(name='Basis')
key = jacket.shape_key_add(name='Jacket side clearance')
inverse = jacket.matrix_world.inverted()


def smooth(value):
    value = max(0.0, min(1.0, value))
    return value * value * (3 - 2 * value)


changed = 0
for i in range(len(jacket.data.vertices)):
    point = jacket.matrix_world @ basis.data[i].co
    mask = smooth((point.z - 1.0) / .09) * smooth((abs(point.x) - .05) / .065)
    if point.y < 0:
        point.y *= 1 - .5 * mask
    key.data[i].co = inverse @ point
    changed += (key.data[i].co - basis.data[i].co).length > 1e-8
if not changed:
    raise ValueError('No jacket vertices affected')
driver = key.driver_add('value').driver
variable = driver.variables.new()
variable.name = 'clearance'
variable.type = 'SINGLE_PROP'
variable.targets[0].id = rig
variable.targets[0].data_path = '["JacketClearance"]'
driver.expression = 'clearance'
rig.update_tag()
bpy.context.view_layer.update()
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('JACKET_CLEARANCE_VERTICES', changed, flush=True)
