"""Give the front and back skirt panels separate leg-follow corrections.

blender -b --python-exit-code 1 --python tools/draconia_refine_skirt_panels.py -- INPUT.blend OUTPUT.blend
The existing manual SkirtHem.C control remains the parent of both panels.
"""
import bpy
import sys
from pathlib import Path


def smooth(x):
    x = max(0.0, min(1.0, x))
    return x*x*(3.0 - 2.0*x)


def main():
    args = sys.argv[sys.argv.index('--') + 1:]
    if len(args) != 2:
        raise ValueError('Expected INPUT.blend OUTPUT.blend')
    source, output = (Path(p).resolve() for p in args)
    if source == output or output.exists():
        raise ValueError('Output must be a new file, separate from the input')
    bpy.ops.wm.open_mainfile(filepath=str(source))
    rig = bpy.data.objects['Draconia Body Rig - experimental']
    skirt = bpy.data.objects['Skirt']
    if len(skirt.data.vertices) != 84 or len(skirt.data.polygons) != 72:
        raise ValueError('Expected the skirt in the clothing rig study')
    names = ('SkirtFront.C', 'SkirtBack.C')
    if any(n in rig.data.bones or n in skirt.vertex_groups for n in names):
        raise ValueError('Skirt-panel controls already exist')
    bpy.ops.object.select_all(action='DESELECT')
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode='EDIT')
    center = rig.data.edit_bones['SkirtHem.C']
    for name in names:
        bone = rig.data.edit_bones.new(name)
        bone.head, bone.tail, bone.roll = center.head.copy(), center.tail.copy(), center.roll
        bone.parent = center
        bone.use_connect = False
    bpy.ops.object.mode_set(mode='OBJECT')
    original = skirt.vertex_groups['SkirtHem.C']
    front = skirt.vertex_groups.new(name='SkirtFront.C')
    back = skirt.vertex_groups.new(name='SkirtBack.C')
    changed = 0
    for vertex in skirt.data.vertices:
        p = skirt.matrix_world @ vertex.co
        w = next((g.weight for g in vertex.groups if g.group == original.index), 0.0)
        a = smooth((-p.y - .04) / .13)
        b = smooth((p.y - .04) / .13)
        if w > 1e-8 and a + b > 0:
            original.add([vertex.index], w * (1-a-b), 'REPLACE')
            if a > 1e-8:
                front.add([vertex.index], w*a, 'REPLACE')
            if b > 1e-8:
                back.add([vertex.index], w*b, 'REPLACE')
            changed += 1
    if not changed:
        raise ValueError('No centre-weighted skirt-panel vertices were found')
    for name, expression in (
        ('SkirtFront.C', '0.3*min(l,r,0)-0.15*(l+r)'),
        ('SkirtBack.C', '0.3*max(l,r,0)-0.15*(l+r)'),
    ):
        bone = rig.pose.bones[name]
        bone.rotation_mode = 'XYZ'
        driver = bone.driver_add('rotation_euler', 0).driver
        driver.type = 'SCRIPTED'
        for var, side in (('l', 'L'), ('r', 'R')):
            variable = driver.variables.new()
            variable.name = var
            variable.type = 'TRANSFORMS'
            target = variable.targets[0]
            target.id = rig
            target.bone_target = f'Thigh.{side}'
            target.transform_type = 'ROT_X'
            target.transform_space = 'LOCAL_SPACE'
            target.rotation_mode = 'XYZ'
        driver.expression = expression
        rig.data.bones[name].hide = True
    print('REFINED_SKIRT_PANEL_VERTICES', changed)
    bpy.ops.wm.save_as_mainfile(filepath=str(output))


if __name__ == '__main__':
    main()
