"""Repair the distorted neutral hand and sleeve after the arms-down rebind.

blender -b --python-exit-code 1 --python tools/draconia_repair_neutral_arm.py -- INPUT.blend OUTPUT.blend
The originally wand-side hand was a single rigid, stretched mesh. Replace that
isolated mesh island with a mirrored copy of the undistorted opposite hand and
its forearm/hand weights. Extend the shortened sleeve gradually to the wrist.
The hidden wand remains a separate prop.
"""
import bpy
import bmesh
import sys
from pathlib import Path


def smooth(value):
    x = max(0.0, min(1.0, value))
    return x*x*(3-2*x)


def mirror_arm(obj):
    if len(obj.data.vertices) != 86 or len(obj.data.polygons) != 84 or not obj.data.uv_layers:
        raise ValueError('Expected the original two-island arm mesh')
    bm = bmesh.new()
    try:
        bm.from_mesh(obj.data)
        bm.verts.ensure_lookup_table()
        right = {v for v in bm.verts if (obj.matrix_world @ v.co).x > 0}
        left = {v for v in bm.verts if (obj.matrix_world @ v.co).x < 0}
        if len(right) != 43 or len(left) != 43:
            raise ValueError('Expected two 43-vertex arm islands')
        edges = [e for e in bm.edges if all(v in right for v in e.verts)]
        faces = [f for f in bm.faces if all(v in right for v in f.verts)]
        copied = bmesh.ops.duplicate(bm, geom=list(right) + edges + faces)
        vertices = [g for g in copied['geom'] if isinstance(g, bmesh.types.BMVert)]
        new_faces = [g for g in copied['geom'] if isinstance(g, bmesh.types.BMFace)]
        if len(vertices) != 43 or len(new_faces) != 42:
            raise ValueError('Unexpected copied arm topology')
        deform = bm.verts.layers.deform.verify()
        mapping = {obj.vertex_groups['Hand.R'].index: obj.vertex_groups['Hand.L'].index,
                   obj.vertex_groups['Forearm.R'].index: obj.vertex_groups['Forearm.L'].index}
        inverse = obj.matrix_world.inverted()
        for vertex in vertices:
            point = obj.matrix_world @ vertex.co
            point.x = -point.x
            vertex.co = inverse @ point
            weights = vertex[deform]
            old = dict(weights.items())
            weights.clear()
            for index, value in old.items():
                weights[mapping.get(index, index)] = value
        # Reflection reverses winding; reverse the copied faces back to preserve normals.
        bmesh.ops.reverse_faces(bm, faces=new_faces)
        bmesh.ops.delete(bm, geom=list(left), context='VERTS')
        bm.to_mesh(obj.data)
    finally:
        bm.free()
    obj.data.update()
    if len(obj.data.vertices) != 86 or len(obj.data.polygons) != 84 or not obj.data.uv_layers:
        raise ValueError('Arm topology or UV layer was lost')


def lengthen_sleeve(obj):
    group = obj.vertex_groups['Forearm.L']
    inverse = obj.matrix_world.inverted()
    changed = 0
    for vertex in obj.data.vertices:
        point = obj.matrix_world @ vertex.co
        influence = next((g.weight for g in vertex.groups if g.group == group.index), 0.0)
        mask = (smooth((1.30-point.z)/.26) * smooth((influence-.10)/.60)
                * smooth((-point.x-.18)/.10))
        if mask > 1e-8:
            point.z -= .10*mask
            vertex.co = inverse @ point
            changed += 1
    return changed


def main():
    args = sys.argv[sys.argv.index('--')+1:]
    if len(args) != 2:
        raise ValueError('Expected INPUT.blend OUTPUT.blend')
    source, output = (Path(p).resolve() for p in args)
    if source == output or output.exists():
        raise ValueError('Output must be a new file')
    bpy.ops.wm.open_mainfile(filepath=str(source))
    wand = bpy.data.objects['Wand']
    if not wand.hide_render or not wand.hide_get():
        raise ValueError('Expected the neutral model with the wand hidden')
    mirror_arm(bpy.data.objects['Arms'])
    for name in ('Cloak', 'Jacket Hands'):
        count = lengthen_sleeve(bpy.data.objects[name])
        if count == 0:
            raise ValueError(f'No sleeve vertices changed: {name}')
        print('LENGTHENED_SLEEVE', name, count, flush=True)
    bpy.ops.wm.save_as_mainfile(filepath=str(output))
    print('NEUTRAL_ARM_REPAIRED', flush=True)

if __name__ == '__main__':
    main()
