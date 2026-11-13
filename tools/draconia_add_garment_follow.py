"""Add experimental leg-follow layers without occupying the manual hem controls.

blender -b --python-exit-code 1 --python tools/draconia_add_garment_follow.py -- INPUT.blend OUTPUT.blend
"""
import bpy
import sys
from pathlib import Path


def main():
    args = sys.argv[sys.argv.index('--') + 1:]
    if len(args) != 2:
        raise ValueError('Expected INPUT.blend OUTPUT.blend')
    source, output = (Path(p).resolve() for p in args)
    if source == output or output.exists():
        raise ValueError('Output must be a new file, separate from the input')
    bpy.ops.wm.open_mainfile(filepath=str(source))
    rig = bpy.data.objects['Draconia Body Rig - experimental']
    for garment in ('Cloak', 'Skirt'):
        for side in ('L', 'R'):
            if f'{garment}Hem.{side}' not in rig.data.bones or f'{garment}Follow.{side}' in rig.data.bones:
                raise ValueError('Expected the garment-control study with no follow bones')
    if 'CloakHem.C' not in rig.data.bones or 'CloakFollow.C' in rig.data.bones:
        raise ValueError('Expected an unmodified cloak centre control')
    bpy.ops.object.select_all(action='DESELECT')
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode='EDIT')
    for garment in ('Cloak', 'Skirt'):
        for side in ('L', 'R'):
            hem = rig.data.edit_bones[f'{garment}Hem.{side}']
            follow = rig.data.edit_bones.new(f'{garment}Follow.{side}')
            follow.head = hem.head.copy()
            follow.tail = hem.tail.copy()
            follow.roll = hem.roll
            follow.use_deform = False
            follow.parent = rig.data.edit_bones['Hips']
            hem.parent = follow
            hem.use_connect = False
    hem = rig.data.edit_bones['CloakHem.C']
    follow = rig.data.edit_bones.new('CloakFollow.C')
    follow.head, follow.tail, follow.roll = hem.head.copy(), hem.tail.copy(), hem.roll
    follow.use_deform = False
    follow.parent = rig.data.edit_bones['Hips']
    hem.parent = follow
    hem.use_connect = False
    bpy.ops.object.mode_set(mode='OBJECT')
    for garment in ('Cloak', 'Skirt'):
        for side in ('L', 'R'):
            name = f'{garment}Follow.{side}'
            constraint = rig.pose.bones[name].constraints.new('COPY_ROTATION')
            constraint.name = 'Half thigh rotation'
            constraint.target = rig
            constraint.subtarget = f'Thigh.{side}'
            constraint.target_space = constraint.owner_space = 'LOCAL'
            constraint.use_x = constraint.use_z = True
            constraint.use_y = False
            constraint.mix_mode = 'REPLACE'
            constraint.influence = 0.5
            if garment == 'Cloak':
                # Ease the outer hem away from the thigh only near extreme
                # lateral lifts, without taking over the animator's hem bone.
                driver = rig.pose.bones[name].driver_add('location', 0).driver
                variable = driver.variables.new()
                variable.name = 'v'
                variable.type = 'TRANSFORMS'
                target = variable.targets[0]
                target.id = rig
                target.bone_target = f'Thigh.{side}'
                target.transform_type = 'ROT_Z'
                target.transform_space = 'LOCAL_SPACE'
                target.rotation_mode = 'XYZ'
                direction = 'v' if side == 'L' else '-v'
                t = f'max(0,min(1,({direction}-0.7853981634)/0.2617993878))'
                sign = '-' if side == 'L' else ''
                driver.expression = f'{sign}0.08*({t})*({t})*(3-2*({t}))'
            rig.data.bones[name].hide = True
    # The rear centre needs to yield to either leg as well as both together.
    # Separate additive constraints leave the manual centre control free to pose.
    for side in ('L', 'R'):
        constraint = rig.pose.bones['CloakFollow.C'].constraints.new('COPY_ROTATION')
        constraint.name = f'Half thigh rotation {side}'
        constraint.target = rig
        constraint.subtarget = f'Thigh.{side}'
        constraint.target_space = constraint.owner_space = 'LOCAL'
        constraint.use_x = constraint.use_z = True
        constraint.use_y = False
        constraint.mix_mode = 'ADD'
        constraint.influence = 0.5
    rig.data.bones['CloakFollow.C'].hide = True
    bpy.ops.wm.save_as_mainfile(filepath=str(output))


if __name__ == '__main__':
    main()
