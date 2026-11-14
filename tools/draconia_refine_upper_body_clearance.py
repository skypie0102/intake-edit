"""Refine the jacket back and hair clearance in the Draconia clothing rig.

blender -b --python-exit-code 1 --python tools/draconia_refine_upper_body_clearance.py -- INPUT.blend OUTPUT.blend
This keeps the jacket's visible front and the existing armature/action intact.
The hair's back tips blend from head to torso, rather than behaving as a rigid
helmet. Small model-space offsets move hair away from the cloak and give the
back of the jacket additional clearance under the cloak.
"""
import bpy
import sys
from pathlib import Path
from mathutils import Vector


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
    hair, jacket = (bpy.data.objects[name] for name in ('Hair', 'Jacket'))
    if len(hair.data.vertices) != 274 or len(jacket.data.vertices) != 93:
        raise ValueError('Expected the clothing study meshes')
    if set(g.name for g in hair.vertex_groups) != {'Head'} or set(g.name for g in jacket.vertex_groups) != {'Torso'}:
        raise ValueError('Expected the original head and torso bindings')
    head = hair.vertex_groups['Head']
    torso = hair.vertex_groups.new(name='Torso')
    matrix, inverse = hair.matrix_world, hair.matrix_world.inverted()
    hair_moved, hair_max = 0, 0.0
    for vertex in hair.data.vertices:
        p = matrix @ vertex.co
        lower = smooth((1.55-p.z)/.32)
        back = lower * smooth((p.y+.02)/.16)
        side = lower * smooth((abs(p.x)-.18)/.16) * smooth((p.y+.10)/.2)
        shift = Vector((.05*side*(1 if p.x >= 0 else -1), .13*back, 0))
        shifted = p + shift
        # Keep the lower side locks in front of a raised shoulder/sleeve.
        front = (smooth((1.70-shifted.z)/.30) * smooth((abs(shifted.x)-.18)/.1)
                 * smooth((-shifted.y-.02)/.15) * smooth((shifted.y+.4)/.15))
        shift.y -= .10*front
        vertex.co = inverse @ (p + shift)
        w = .7*back
        head.add([vertex.index], 1-w, 'REPLACE')
        torso.add([vertex.index], w, 'REPLACE')
        hair_moved += shift.length > 1e-6
        hair_max = max(hair_max, shift.length)
    matrix, inverse = jacket.matrix_world, jacket.matrix_world.inverted()
    jacket_moved, jacket_max = 0, 0.0
    for vertex in jacket.data.vertices:
        p = matrix @ vertex.co
        # Monotonic compression of the hidden back half. The front stays fixed,
        # and the mapping preserves vertex order instead of folding faces.
        moved = p.copy()
        if p.y > 0:
            moved.y *= .4
        # Tuck the hidden side seams in as well, while leaving the visible
        # front panel alone. Both axes use positive scale, so ordering is kept.
        side = (smooth((moved.y+.04)/.08) * smooth((1.4-moved.z)/.3)
                * smooth((moved.z-.8)/.1))
        moved.x *= 1-.6*side
        vertex.co = inverse @ moved
        distance = (moved - p).length
        jacket_moved += distance > 1e-6
        jacket_max = max(jacket_max, distance)
    cloak = bpy.data.objects['Cloak']
    matrix, inverse = cloak.matrix_world, cloak.matrix_world.inverted()
    cloak_moved, cloak_max = 0, 0.0
    for vertex in cloak.data.vertices:
        p = matrix @ vertex.co
        back = (smooth((p.y-.02)/.10) * smooth((1.46-p.z)/.25)
                * smooth((p.z-.80)/.1))
        shift = Vector((0, .035*back, 0))
        vertex.co = inverse @ (p + shift)
        cloak_moved += shift.length > 1e-6
        cloak_max = max(cloak_max, shift.length)
    print('REFINED', 'HAIR_VERTICES', hair_moved, 'HAIR_MAX_OFFSET', hair_max,
          'JACKET_VERTICES', jacket_moved, 'JACKET_MAX_OFFSET', jacket_max,
          'CLOAK_VERTICES', cloak_moved, 'CLOAK_MAX_OFFSET', cloak_max, flush=True)
    bpy.ops.wm.save_as_mainfile(filepath=str(output))

if __name__ == '__main__':
    main()
