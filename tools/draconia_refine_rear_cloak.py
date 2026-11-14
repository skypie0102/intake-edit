"""Refine rear-cloak weights and follow for opposite leg motion.

blender -b --python-exit-code 1 --python tools/draconia_refine_rear_cloak.py -- INPUT.blend OUTPUT.blend
"""
import bpy
import sys
from pathlib import Path


def weight(vertex, group):
    return next((w.weight for w in vertex.groups if w.group == group.index), 0.0)


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
    cloak = bpy.data.objects['Cloak']
    if len(cloak.data.vertices) != 969 or len(cloak.data.polygons) != 998:
        raise ValueError('Expected the cloak from the clothing rig study')
    hips = cloak.vertex_groups['Hips']
    hem = [cloak.vertex_groups[n] for n in ('CloakHem.L', 'CloakHem.C', 'CloakHem.R')]
    changed = []
    for vertex in cloak.data.vertices:
        p = cloak.matrix_world @ vertex.co
        blend = smooth((p.y - .12) / .12) * smooth((.8 - p.z) / .2)
        old_hips = weight(vertex, hips)
        old_hem = [weight(vertex, group) for group in hem]
        total_hem = sum(old_hem)
        shift = .6 * blend * old_hips
        if shift > 1e-5 and total_hem > 1e-5:
            hips.add([vertex.index], old_hips - shift, 'REPLACE')
            for group, old in zip(hem, old_hem):
                if old > 1e-8:
                    group.add([vertex.index], old + shift * old / total_hem, 'REPLACE')
            changed.append(vertex.index)
    if len(changed) != 45:
        raise ValueError(f'Unexpected rear-cloak vertex count: {len(changed)}')
    for side in ('L', 'R'):
        constraint = rig.pose.bones[f'CloakFollow.{side}'].constraints['Thigh follow']
        if abs(constraint.influence - .6) > 1e-5 or constraint.use_z:
            raise ValueError('Expected the refined cloak-side follow')
        constraint.influence = .7
    if not rig.animation_data:
        raise ValueError('Expected the existing cloak-follow drivers')
    center = [curve for curve in rig.animation_data.drivers
              if curve.data_path == 'pose.bones["CloakFollow.C"].rotation_euler'
              and curve.array_index == 0]
    if len(center) != 1:
        raise ValueError('Expected one rear-cloak centre X driver')
    # Backward motion of either leg moves the rear panel out of the way;
    # a forward-moving opposite leg should not cancel that movement. Equal
    # same-direction legs do not amplify the centre beyond their angle.
    center[0].driver.expression = (
        '0.7*max(l,r,0)+0.3*min(max(l,0),max(r,0))+min(max(l,r),0)')
    print('REAR_CLOAK_REFINED_VERTICES', len(changed))
    bpy.ops.wm.save_as_mainfile(filepath=str(output))


if __name__ == '__main__':
    main()
