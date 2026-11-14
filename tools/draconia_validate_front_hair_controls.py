"""Check neutral preservation, skin weights and the two front-lock controls.

blender -b --python-exit-code 1 --python tools/draconia_validate_front_hair_controls.py -- BEFORE.blend AFTER.blend
"""
import math
import sys

import bpy

paths = sys.argv[sys.argv.index('--') + 1:]
if len(paths) != 2:
    raise ValueError('Expected BEFORE.blend AFTER.blend')


def coords(name):
    obj = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    vertices = [obj.matrix_world @ vertex.co for vertex in mesh.vertices]
    obj.to_mesh_clear()
    return vertices


snapshots = []
for path in paths:
    bpy.ops.wm.open_mainfile(filepath=path)
    bpy.context.scene.frame_set(1)
    bpy.context.view_layer.update()
    snapshots.append({name: coords(name) for name in ('Hair', 'Cloak', 'Head', 'Arms', 'Legs')})
maximum = max((a - b).length for name in snapshots[0]
              for a, b in zip(snapshots[0][name], snapshots[1][name]))
assert maximum < 1e-5, maximum
rig = bpy.data.objects['Draconia Body Rig - experimental']
hair = bpy.data.objects['Hair']
deform = {bone.name for bone in rig.data.bones if bone.use_deform}
ids = {group.index for group in hair.vertex_groups if group.name in deform}
for vertex in hair.data.vertices:
    assert abs(sum(weight.weight for weight in vertex.groups if weight.group in ids) - 1) < 1e-4
base_hair, base_cloak = coords('Hair'), coords('Cloak')
for name in ('Hair.Front.L', 'Hair.Front.R'):
    assert name in rig.pose.bones and name in hair.vertex_groups
    bone = rig.pose.bones[name]
    assert bone.parent.name == 'Head'
    bone.rotation_mode = 'XYZ'
    bone.rotation_euler.z = math.radians(-10)
    bpy.context.view_layer.update()
    movement = max((a - b).length for a, b in zip(base_hair, coords('Hair')))
    cloak_drift = max((a - b).length for a, b in zip(base_cloak, coords('Cloak')))
    assert movement > .005 and cloak_drift < 1e-5, (name, movement, cloak_drift)
    print('FRONT_HAIR', name, 'MOVEMENT', movement, 'CLOAK_DRIFT', cloak_drift, flush=True)
    bone.rotation_euler.z = 0
    bpy.context.view_layer.update()
print('FRONT_HAIR_NEUTRAL_MAX_DELTA', maximum, 'FRONT_HAIR_CHECKS_OK', flush=True)
