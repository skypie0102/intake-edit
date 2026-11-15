"""Smooth a small region at each chibi thumb root to reduce surface folding.

blender -b CHIBI.blend --python-exit-code 1 --python tools/draconia_smooth_chibi_thumb_roots.py -- OUTPUT.blend
Topology, weights, controls and other meshes remain unchanged.
"""
import sys
from pathlib import Path

import bmesh
import bpy
from mathutils import Vector

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 1:
    raise ValueError('Expected OUTPUT.blend')
output = Path(args[0]).resolve()
if output.exists() or output == Path(bpy.data.filepath).resolve():
    raise ValueError('Output must be a new file')
for side, sign in (('L', -1), ('R', 1)):
    obj = bpy.data.objects['Articulated hand study ' + side]
    if not any(m.type == 'ARMATURE' and m.use_deform_preserve_volume for m in obj.modifiers):
        raise ValueError('Expected volume-preserving chibi hand skinning')
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    center = Vector((sign * .343, -.07, .827))
    vertices = [v for v in bm.verts if (v.co - center).length < .003]
    if not 15 <= len(vertices) <= 30:
        raise ValueError(f'Unexpected thumb root geometry on {side}: {len(vertices)} vertices')
    for _ in range(3):
        bmesh.ops.smooth_vert(bm, verts=vertices, factor=.2,
                              use_axis_x=True, use_axis_y=True, use_axis_z=True)
    if any(not e.is_manifold for e in bm.edges):
        raise ValueError(f'Nonmanifold hand on {side}')
    bm.to_mesh(obj.data)
    bm.free()
    obj.data.update()
    print('CHIBI_THUMB_ROOT_SMOOTHED', side, len(vertices), flush=True)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
