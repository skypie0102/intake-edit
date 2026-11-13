"""Add experimental cloak-hem controls to the gaze/smile study scene.

Run: blender -b --python tools/draconia_add_cloak_controls.py -- INPUT.blend OUTPUT.blend
The source is never overwritten. See docs/DRACONIA_COMPANION.md for limitations.
"""
import bpy
import hashlib
import sys
from pathlib import Path

SOURCE_SHA256 = 'bfeffa616f946ad07aac46efa015037dcb10638eba15b54e9a3a57b37a548e34'
NAMES = ('CloakHem.L', 'CloakHem.C', 'CloakHem.R')


def main():
    args = sys.argv[sys.argv.index('--') + 1:]
    if len(args) != 2:
        raise ValueError('Expected INPUT.blend OUTPUT.blend')
    source, output = (Path(p).resolve() for p in args)
    if source == output or output.exists():
        raise ValueError('Output must be a new file, separate from the input')
    if hashlib.sha256(source.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise ValueError('Expected the unmodified gaze/smile study scene')
    bpy.ops.wm.open_mainfile(filepath=str(source))
    rig = bpy.data.objects['Draconia Body Rig - experimental']
    cloak = bpy.data.objects['Cloak']
    if any(n in rig.data.bones or n in cloak.vertex_groups for n in NAMES):
        raise ValueError('Cloak hem controls already exist')
    bpy.ops.object.select_all(action='DESELECT')
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode='EDIT')
    for name, x in zip(NAMES, (-0.30, 0.0, 0.30)):
        bone = rig.data.edit_bones.new(name)
        bone.head, bone.tail = (x, 0.14, 0.82), (x, 0.14, 0.42)
        bone.parent = rig.data.edit_bones['Hips']
        bone.use_connect = False
    bpy.ops.object.mode_set(mode='OBJECT')
    groups = [cloak.vertex_groups.new(name=n) for n in NAMES]
    hips = cloak.vertex_groups['Hips']
    rig_inverse = rig.matrix_world.inverted()
    for vertex in cloak.data.vertices:
        h = next((g.weight for g in vertex.groups if g.group == hips.index), 0.0)
        co = rig_inverse @ (cloak.matrix_world @ vertex.co)
        t = max(0.0, min(1.0, (0.86 - co.z) / 0.35))
        t = t * t * (3.0 - 2.0 * t)
        amount = h * t * 0.8
        if amount < 1e-6:
            continue
        x = max(-0.30, min(0.30, co.x))
        basis = (max(0.0, -x / 0.30), 1.0 - abs(x) / 0.30, max(0.0, x / 0.30))
        hips.add([vertex.index], h - amount, 'REPLACE')
        for group, weight in zip(groups, basis):
            if weight > 1e-6:
                group.add([vertex.index], amount * weight, 'REPLACE')
    bpy.ops.wm.save_as_mainfile(filepath=str(output))


if __name__ == '__main__':
    main()
