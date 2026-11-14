"""Add three optional lower-hair controls without changing the neutral shape.

blender -b --python-exit-code 1 --python tools/draconia_add_hair_controls.py -- INPUT.blend OUTPUT.blend
Controls are manual, not collision or physics simulation.
"""
import bpy
import sys
from pathlib import Path


def smooth(value):
    x = max(0.0, min(1.0, value))
    return x*x*(3-2*x)


def main():
    args = sys.argv[sys.argv.index('--')+1:]
    if len(args) != 2:
        raise ValueError('Expected INPUT.blend OUTPUT.blend')
    source, output = (Path(p).resolve() for p in args)
    if source == output or output.exists():
        raise ValueError('Output must be a new file')
    bpy.ops.wm.open_mainfile(filepath=str(source))
    rig = bpy.data.objects['Draconia Body Rig - experimental']
    hair = bpy.data.objects['Hair']
    if len(hair.data.vertices) != 274 or any(n in rig.data.bones for n in ('Hair.C', 'Hair.L', 'Hair.R')):
        raise ValueError('Expected the current hair and no existing hair controls')
    bpy.ops.object.select_all(action='DESELECT')
    rig.select_set(True)
    bpy.context.view_layer.objects.active = rig
    bpy.ops.object.mode_set(mode='EDIT')
    parent = rig.data.edit_bones['Head']
    for name, x in (('Hair.C', 0), ('Hair.L', -.17), ('Hair.R', .17)):
        bone = rig.data.edit_bones.new(name)
        bone.head = (x, .1, 1.53)
        bone.tail = (x, .1, 1.12)
        bone.parent = parent
    bpy.ops.object.mode_set(mode='OBJECT')
    head = hair.vertex_groups['Head']
    groups = {name: hair.vertex_groups.new(name='Hair.'+name) for name in ('C','L','R')}
    changed = 0
    for vertex in hair.data.vertices:
        p = hair.matrix_world @ vertex.co
        old = next((g.weight for g in vertex.groups if g.group == head.index), 0)
        mask = smooth((1.67-p.z)/.33)*smooth((p.y+.06)/.16)
        transfer = old*.65*mask
        if transfer <= 1e-8:
            continue
        side = smooth((abs(p.x)-.025)/.18)
        head.add([vertex.index], old-transfer, 'REPLACE')
        groups['C'].add([vertex.index], transfer*(1-side), 'REPLACE')
        groups['L' if p.x < 0 else 'R'].add([vertex.index], transfer*side, 'REPLACE')
        changed += 1
    if not changed:
        raise ValueError('No hair vertices were assigned')
    bpy.ops.wm.save_as_mainfile(filepath=str(output))
    print('HAIR_CONTROL_VERTICES', changed, flush=True)

if __name__ == '__main__':
    main()
