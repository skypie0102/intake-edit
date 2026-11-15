"""Report sampled chibi finger/clothing contacts for wider wrist turns.

blender -b CHIBI_RIG.blend --python-exit-code 1 --python tools/draconia_audit_chibi_wrist_envelope.py
Face-pair counts include hidden surfaces; poses are sampled independently.
"""
import math

import bpy
from mathutils.bvhtree import BVHTree

rig = bpy.data.objects['Draconia Body Rig - experimental']
assert abs(rig['NeutralArmClearance'] - 1) < 1e-6
assert not (rig.animation_data and rig.animation_data.action)


def tree(name, fingers_only=False):
    source = bpy.data.objects[name]
    obj = source.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    vertices = [obj.matrix_world @ v.co for v in mesh.vertices]
    if fingers_only:
        ids = {g.index for g in source.vertex_groups if g.name.startswith(('Fingers.', 'Thumb.'))}
        chosen = {v.index for v in source.data.vertices
                  if sum(w.weight for w in v.groups if w.group in ids) > .5}
        assert chosen and len(source.data.vertices) == len(mesh.vertices)
        faces = [tuple(p.vertices) for p in mesh.polygons if any(i in chosen for i in p.vertices)]
    else:
        faces = [tuple(p.vertices) for p in mesh.polygons]
    result = BVHTree.FromPolygons(vertices, faces, epsilon=1e-6)
    obj.to_mesh_clear()
    return result


for side in ('L', 'R'):
    wrist = rig.pose.bones['Hand.' + side]
    wrist.rotation_mode = 'XYZ'
    wrist.rotation_euler = (0, 0, 0)
    bpy.context.view_layer.update()
    targets = {name: tree(name) for name in ('Cloak', 'Jacket Hands', 'Legs')}
    for axis in range(3):
        for degrees in (-60, -45, -30, -15, 0, 15, 30, 45, 60):
            wrist.rotation_euler = (0, 0, 0)
            wrist.rotation_euler[axis] = math.radians(degrees)
            bpy.context.view_layer.update()
            fingers = tree('Articulated hand study ' + side, True)
            contacts = {name: len(fingers.overlap(target)) for name, target in targets.items()}
            if any(contacts.values()):
                print('CHIBI_WIDE_WRIST_CONTACT', side, 'axis', axis, 'degrees', degrees, contacts, flush=True)
            if degrees == 0:
                assert not any(contacts.values()), (side, contacts)
    wrist.rotation_euler = (0, 0, 0)
    bpy.context.view_layer.update()
print('CHIBI_WIDE_WRIST_AUDIT_COMPLETE', flush=True)
