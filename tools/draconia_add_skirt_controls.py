"""Add experimental manual skirt-hem controls to the cloak-control study.

Run: blender -b --python-exit-code 1 --python tools/draconia_add_skirt_controls.py -- INPUT.blend OUTPUT.blend
"""
import bpy
import sys
from pathlib import Path

NAMES = ('SkirtHem.L', 'SkirtHem.C', 'SkirtHem.R')


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
    if not all(n in rig.data.bones for n in ('CloakHem.L', 'CloakHem.C', 'CloakHem.R')):
        raise ValueError('Expected the cloak-control study as input')
    if any(n in rig.data.bones or n in skirt.vertex_groups for n in NAMES):
        raise ValueError('Skirt hem controls already exist')
    bpy.ops.object.select_all(action='DESELECT')
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode='EDIT')
    for name, x in zip(NAMES, (-0.22, 0.0, 0.22)):
        bone = rig.data.edit_bones.new(name)
        bone.head, bone.tail = (x, 0.0, 0.89), (x, 0.0, 0.62)
        bone.parent = rig.data.edit_bones['Hips']
        bone.use_connect = False
    bpy.ops.object.mode_set(mode='OBJECT')
    groups = [skirt.vertex_groups.new(name=n) for n in NAMES]
    hips = skirt.vertex_groups['Hips']
    rig_inverse = rig.matrix_world.inverted()
    for vertex in skirt.data.vertices:
        h = next((g.weight for g in vertex.groups if g.group == hips.index), 0.0)
        co = rig_inverse @ (skirt.matrix_world @ vertex.co)
        t = max(0.0, min(1.0, (0.91 - co.z) / 0.26))
        t = t * t * (3.0 - 2.0 * t)
        amount = h * t * 0.65
        if amount < 1e-6:
            continue
        x = max(-0.22, min(0.22, co.x))
        basis = (max(0.0, -x / 0.22), 1.0 - abs(x) / 0.22, max(0.0, x / 0.22))
        hips.add([vertex.index], h - amount, 'REPLACE')
        for group, weight in zip(groups, basis):
            if weight > 1e-6:
                group.add([vertex.index], amount * weight, 'REPLACE')
    bpy.ops.wm.save_as_mainfile(filepath=str(output))


if __name__ == '__main__':
    main()
