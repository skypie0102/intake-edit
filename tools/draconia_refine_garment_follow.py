"""Refine cloak-side and skirt follow for mixed leg movements.

blender -b --python-exit-code 1 --python tools/draconia_refine_garment_follow.py -- INPUT.blend OUTPUT.blend
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
    for side in ('L', 'R'):
        bone = rig.pose.bones[f'CloakFollow.{side}']
        original = bone.constraints['Thigh follow']
        if not original.use_x or not original.use_z or 'Sideways thigh follow' in bone.constraints:
            raise ValueError('Expected the original cloak-side follow constraint')
        original.use_z = False
        sideways = bone.constraints.new('COPY_ROTATION')
        sideways.name = 'Sideways thigh follow'
        sideways.target = rig
        sideways.subtarget = f'Thigh.{side}'
        sideways.target_space = sideways.owner_space = 'LOCAL'
        sideways.use_x = sideways.use_y = False
        sideways.use_z = True
        sideways.mix_mode = 'ADD'
        driver = sideways.driver_add('influence').driver
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
        # Ease from 30% for inward rotation to 60% for outward rotation,
        # reaching the latter by 10 degrees. This leaves manual hem controls free.
        t = f'max(0,min(1,({direction})/0.1745329252))'
        driver.expression = f'0.3+0.3*({t})*({t})*(3-2*({t}))'
        skirt = rig.pose.bones[f'SkirtFollow.{side}'].constraints['Thigh follow']
        if abs(skirt.influence - 0.5) > 1e-5:
            raise ValueError('Expected the original skirt-follow strength')
        skirt.influence = 0.65
    # A lightly following centre keeps the skirt between the two thighs while
    # leaving SkirtHem.C itself available for animator adjustments.
    if 'SkirtFollow.C' in rig.data.bones:
        raise ValueError('Skirt centre follow already exists')
    bpy.ops.object.select_all(action='DESELECT')
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode='EDIT')
    hem = rig.data.edit_bones['SkirtHem.C']
    follow = rig.data.edit_bones.new('SkirtFollow.C')
    follow.head, follow.tail, follow.roll = hem.head.copy(), hem.tail.copy(), hem.roll
    follow.parent = rig.data.edit_bones['Hips']
    follow.use_deform = False
    hem.parent = follow
    hem.use_connect = False
    bpy.ops.object.mode_set(mode='OBJECT')
    for side in ('L', 'R'):
        constraint = rig.pose.bones['SkirtFollow.C'].constraints.new('COPY_ROTATION')
        constraint.name = f'Thigh follow {side}'
        constraint.target = rig
        constraint.subtarget = f'Thigh.{side}'
        constraint.target_space = constraint.owner_space = 'LOCAL'
        constraint.use_x = constraint.use_z = True
        constraint.use_y = False
        constraint.mix_mode = 'ADD'
        constraint.influence = 0.15
    rig.data.bones['SkirtFollow.C'].hide = True
    bpy.ops.wm.save_as_mainfile(filepath=str(output))


if __name__ == '__main__':
    main()
