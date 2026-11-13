"""Refine the wand-side underarm on the sleeve/cuff study.

blender -b --python-exit-code 1 --python tools/draconia_refine_underarm.py -- INPUT.blend OUTPUT.blend
"""
import bpy
import sys
from pathlib import Path

MASK_VERTICES = (357, 392, 376, 358, 111, 112, 130, 146, 34, 33, 108)
WEIGHT_VERTICES = {33, 34, 73, 108, 111, 112, 130, 145, 146, 147, 172, 173,
                   175, 279, 280, 311, 354, 357, 358, 376, 389, 391, 392,
                   393, 410, 418, 419, 421}


def weight(vertex, group):
    return next((w.weight for w in vertex.groups if w.group == group.index), 0.0)


def main():
    args = sys.argv[sys.argv.index('--') + 1:]
    if len(args) != 2:
        raise ValueError('Expected INPUT.blend OUTPUT.blend')
    source, output = (Path(p).resolve() for p in args)
    if source == output or output.exists():
        raise ValueError('Output must be a new file, separate from the input')
    bpy.ops.wm.open_mainfile(filepath=str(source))
    cloak = bpy.data.objects['Cloak']
    if len(cloak.data.vertices) != 969 or len(cloak.data.polygons) != 998:
        raise ValueError('Expected the cloak from the sleeve/cuff study')
    hips, torso, upper, forearm, smooth = (cloak.vertex_groups[name] for name in
        ('Hips', 'Torso', 'UpperArm.L', 'Forearm.L', 'Sleeve corrective influence'))
    for i in MASK_VERTICES:
        smooth.add([i], 0.0, 'REPLACE')
    found = set()
    for vertex in cloak.data.vertices:
        co = cloak.matrix_world @ vertex.co
        if -0.31 < co.x < -0.22 and 1.09 < co.z < 1.16 and weight(vertex, upper) + weight(vertex, forearm) > 0.75:
            old_hips, old_torso, old_upper = weight(vertex, hips), weight(vertex, torso), weight(vertex, upper)
            shift = 0.5 * (old_hips + old_torso)
            if shift > 0.005:
                hips.add([vertex.index], old_hips * 0.5, 'REPLACE')
                torso.add([vertex.index], old_torso * 0.5, 'REPLACE')
                upper.add([vertex.index], old_upper + shift, 'REPLACE')
                found.add(vertex.index)
    if found != WEIGHT_VERTICES:
        raise ValueError(f'Unexpected underarm vertex set: {sorted(found)}')
    print('UNDERARM_REFINED', len(MASK_VERTICES), len(found))
    bpy.ops.wm.save_as_mainfile(filepath=str(output))


if __name__ == '__main__':
    main()
