"""Refine sleeve weights after changing Draconia to an arms-down rest pose.

blender -b --python-exit-code 1 --python tools/draconia_refine_neutral_sleeve.py -- INPUT.blend OUTPUT.blend
The original bent arm left forearm influence high on the upper sleeve. Transfer
part of that influence to the upper arm above the new elbow, preserving each
vertex's total skin weight. The neutral mesh itself is not moved.
"""
import bpy
import sys
from pathlib import Path


def smooth(value):
    x = max(0.0, min(1.0, value))
    return x*x*(3-2*x)


def weight(vertex, group):
    return next((g.weight for g in vertex.groups if g.group == group.index), 0.0)


def main():
    args = sys.argv[sys.argv.index('--')+1:]
    if len(args) != 2:
        raise ValueError('Expected INPUT.blend OUTPUT.blend')
    source, output = (Path(p).resolve() for p in args)
    if source == output or output.exists():
        raise ValueError('Output must be a new file')
    bpy.ops.wm.open_mainfile(filepath=str(source))
    rig = bpy.data.objects['Draconia Body Rig - experimental']
    cloak = bpy.data.objects['Cloak']
    if len(cloak.data.vertices) != 969:
        raise ValueError('Expected the clothing study cloak')
    upper, forearm = (cloak.vertex_groups[name] for name in ('UpperArm.L', 'Forearm.L'))
    elbow_z = (rig.matrix_world @ rig.data.bones['Forearm.L'].head_local).z
    changed, max_transfer = 0, 0.0
    for vertex in cloak.data.vertices:
        p = cloak.matrix_world @ vertex.co
        mask = smooth((p.z-(elbow_z+.015))/.15)*smooth((-p.x-.18)/.12)
        old_upper, old_forearm = weight(vertex, upper), weight(vertex, forearm)
        transfer = old_forearm*mask*.75
        if transfer > 1e-8:
            upper.add([vertex.index], old_upper+transfer, 'REPLACE')
            forearm.add([vertex.index], old_forearm-transfer, 'REPLACE')
            changed += 1
            max_transfer = max(max_transfer, transfer)
    if not changed:
        raise ValueError('No sleeve vertices needed refinement')
    bpy.ops.wm.save_as_mainfile(filepath=str(output))
    print('NEUTRAL_SLEEVE_VERTICES', changed, 'MAX_WEIGHT_TRANSFER', max_transfer, flush=True)

if __name__ == '__main__':
    main()
