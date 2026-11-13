"""Make the rear cloak respond to one or both legs without overswinging.

blender -b --python-exit-code 1 --python tools/draconia_refine_cloak_center.py -- INPUT.blend OUTPUT.blend
The manual CloakHem.C control remains a child of the driven follow bone.
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
    center = rig.pose.bones['CloakFollow.C']
    if set(c.name for c in center.constraints) != {'Thigh follow L', 'Thigh follow R'}:
        raise ValueError('Expected the unmodified garment-follow constraints')
    if rig.pose.bones['CloakHem.C'].parent != center:
        raise ValueError('The manual centre hem control has an unexpected parent')
    center.rotation_mode = 'XYZ'
    for constraint in list(center.constraints):
        center.constraints.remove(constraint)
    for index, transform in ((0, 'ROT_X'), (2, 'ROT_Z')):
        driver = center.driver_add('rotation_euler', index).driver
        driver.type = 'SCRIPTED'
        for name, side in (('l', 'L'), ('r', 'R')):
            variable = driver.variables.new()
            variable.name = name
            variable.type = 'TRANSFORMS'
            target = variable.targets[0]
            target.id = rig
            target.bone_target = f'Thigh.{side}'
            target.transform_type = transform
            target.transform_space = 'LOCAL_SPACE'
            target.rotation_mode = 'XYZ'
        # One moving leg contributes 70%; equal, same-direction legs do not
        # exceed their shared angle. Opposing movements can cancel naturally.
        driver.expression = ('0.7*(l+r)-0.4*min(max(l,0),max(r,0))'
                             '+0.4*min(max(-l,0),max(-r,0))')
    bpy.ops.wm.save_as_mainfile(filepath=str(output))


if __name__ == '__main__':
    main()
