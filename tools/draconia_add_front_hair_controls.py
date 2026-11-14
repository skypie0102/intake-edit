"""Add manual controls for the two lower front hair locks.

blender -b INPUT.blend --python-exit-code 1 --python tools/draconia_add_front_hair_controls.py -- OUTPUT.blend
The controls preserve the neutral shape and do not provide collision physics.
"""
import sys
from pathlib import Path

import bpy

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 1:
    raise ValueError('Expected OUTPUT.blend')
output = Path(args[0]).resolve()
if output.exists() or output == Path(bpy.data.filepath).resolve():
    raise ValueError('Output must be a new file')
scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
hair = bpy.data.objects['Hair']
if len(hair.data.vertices) != 274 or any(name in rig.data.bones or name in hair.vertex_groups for name in ('Hair.Front.L', 'Hair.Front.R')):
    raise ValueError('Expected current hair without front-lock controls')
scene.frame_set(1)
bpy.ops.object.select_all(action='DESELECT')
rig.select_set(True)
bpy.context.view_layer.objects.active = rig
bpy.ops.object.mode_set(mode='EDIT')
parent = rig.data.edit_bones['Head']
for name, x in (('Hair.Front.L', -.26), ('Hair.Front.R', .26)):
    bone = rig.data.edit_bones.new(name)
    bone.head = (x, -.15, 1.7)
    bone.tail = (x, -.15, 1.2)
    bone.parent = parent
bpy.ops.object.mode_set(mode='OBJECT')
head = hair.vertex_groups['Head']
groups = {side: hair.vertex_groups.new(name='Hair.Front.' + side) for side in ('L', 'R')}


def smooth(value):
    value = max(0.0, min(1.0, value))
    return value * value * (3 - 2 * value)


changed = 0
for vertex in hair.data.vertices:
    point = hair.matrix_world @ vertex.co
    original = next((weight.weight for weight in vertex.groups if weight.group == head.index), 0)
    mask = (smooth((1.72 - point.z) / .30) * smooth((-point.y - .02) / .18)
            * smooth((abs(point.x) - .15) / .15))
    transfer = original * .9 * mask
    if transfer <= 1e-8:
        continue
    head.add([vertex.index], original - transfer, 'REPLACE')
    groups['L' if point.x < 0 else 'R'].add([vertex.index], transfer, 'REPLACE')
    changed += 1
if not changed:
    raise ValueError('No front-lock vertices assigned')
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('FRONT_HAIR_CONTROL_VERTICES', changed, flush=True)
