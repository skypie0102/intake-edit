"""Validate manual hair controls, weights and isolated motion.

blender -b RIG.blend --python-exit-code 1 --python tools/draconia_validate_hair_controls.py
"""
import bpy
import math

rig = bpy.data.objects['Draconia Body Rig - experimental']
hair = bpy.data.objects['Hair']
controls = ('Hair.C', 'Hair.L', 'Hair.R')
assert all(name in rig.pose.bones and name in hair.vertex_groups for name in controls)
for vertex in hair.data.vertices:
    deform = {b.name for b in rig.data.bones if b.use_deform}
    ids = {g.index for g in hair.vertex_groups if g.name in deform}
    assert abs(sum(g.weight for g in vertex.groups if g.group in ids)-1) < 1e-4


def coords(name):
    obj = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    points = [obj.matrix_world @ v.co for v in mesh.vertices]
    obj.to_mesh_clear()
    return points

bpy.context.view_layer.update()
base_hair, base_cloak = coords('Hair'), coords('Cloak')
for name in controls:
    bone = rig.pose.bones[name]
    bone.rotation_mode = 'XYZ'
    bone.rotation_euler.x = math.radians(10)
    bpy.context.view_layer.update()
    moved, cloak = coords('Hair'), coords('Cloak')
    maximum = max((a-b).length for a, b in zip(moved, base_hair))
    unchanged = max((a-b).length for a, b in zip(cloak, base_cloak))
    assert maximum > .005 and unchanged < 1e-5, (name, maximum, unchanged)
    print('HAIR_CONTROL', name, 'MAX_MOVE', maximum, 'CLOAK_DRIFT', unchanged, flush=True)
    bone.rotation_euler.x = 0
    bpy.context.view_layer.update()
print('HAIR_CONTROLS_OK', flush=True)
