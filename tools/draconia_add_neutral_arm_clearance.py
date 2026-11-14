"""Add a reversible, additive neutral-arm clearance control.

blender -b JOINED_HAND.blend --python-exit-code 1 --python tools/draconia_add_neutral_arm_clearance.py -- OUTPUT.blend
The small shoulder offset keeps the arms down while moving the joined fingers
away from the cloak. This is a pose control, not automatic collision physics.
"""
import math
import sys
from pathlib import Path

import bpy
from mathutils import Matrix

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 1:
    raise ValueError('Expected OUTPUT.blend')
output = Path(args[0]).resolve()
if output.exists() or output == Path(bpy.data.filepath).resolve():
    raise ValueError('Output must be a new file')
scene = bpy.context.scene
scene.frame_set(1)
rig = bpy.data.objects['Draconia Body Rig - experimental']
if rig.animation_data and rig.animation_data.action:
    raise ValueError('Expected an unanimated neutral rig')
if 'NeutralArmClearance' in rig or any('ArmClearance.' + side in rig.data.bones for side in ('L', 'R')):
    raise ValueError('Arm clearance control already exists')
for side in ('L', 'R'):
    upper = rig.pose.bones['UpperArm.' + side]
    assert all(abs(upper.matrix_basis[i][j] - Matrix.Identity(4)[i][j]) < 1e-6
               for i in range(4) for j in range(4))
    assert bpy.data.objects.get('Articulated hand study ' + side)
rig['NeutralArmClearance'] = 0.0
rig.id_properties_ui('NeutralArmClearance').update(
    min=0, max=1, default=1.0, description='Move neutral arms slightly clear of the cloak')

bpy.ops.object.select_all(action='DESELECT')
rig.select_set(True)
bpy.context.view_layer.objects.active = rig
bpy.ops.object.mode_set(mode='EDIT')
for side in ('L', 'R'):
    upper = rig.data.edit_bones['UpperArm.' + side]
    if upper.use_connect:
        raise ValueError('Expected unconnected upper-arm bones')
    control = rig.data.edit_bones.new('ArmClearance.' + side)
    control.head = upper.head.copy()
    control.tail = upper.tail.copy()
    control.roll = upper.roll
    control.parent = upper.parent
    control.use_deform = False
    upper.parent = control
bpy.ops.object.mode_set(mode='OBJECT')
for side, degrees in (('L', 5), ('R', -5)):
    control = rig.pose.bones['ArmClearance.' + side]
    control.rotation_mode = 'XYZ'
    driver = control.driver_add('rotation_euler', 2).driver
    variable = driver.variables.new()
    variable.name = 'clearance'
    variable.type = 'SINGLE_PROP'
    variable.targets[0].id = rig
    variable.targets[0].data_path = '["NeutralArmClearance"]'
    driver.expression = f'clearance*({math.radians(degrees):.14g})'
rig['NeutralArmClearance'] = 1.0
rig.update_tag()
bpy.context.view_layer.update()
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('NEUTRAL_ARM_CLEARANCE_ADDED', flush=True)
