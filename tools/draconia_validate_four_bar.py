"""Verify dance length, visible wand-arm motion, and rigid wand binding.

blender -b DANCE.blend --python-exit-code 1 --python tools/draconia_validate_four_bar.py
"""
import bpy
from mathutils import Vector
scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
wand = bpy.data.objects['Wand']
assert scene.render.fps == 24 and scene.frame_start == 1 and scene.frame_end == 192
assert rig.animation_data.action.name == 'Four-bar CC0 dance - full 8 seconds'
assert [g.name for g in wand.vertex_groups] == ['Hand.L']
hands, centers, offsets = [], [], []
for frame in range(1, 193):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    hand = rig.matrix_world @ rig.pose.bones['Hand.L'].head
    evaluated = wand.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = evaluated.to_mesh()
    center = sum((evaluated.matrix_world @ v.co for v in mesh.vertices), Vector()) / len(mesh.vertices)
    evaluated.to_mesh_clear()
    hands.append(hand.copy()); centers.append(center.copy()); offsets.append((center - hand).length)
hand_motion = max((p - hands[0]).length for p in hands)
wand_motion = max((p - centers[0]).length for p in centers)
assert hand_motion > .10 and wand_motion > .10, (hand_motion, wand_motion)
assert max(offsets) - min(offsets) < .002, (min(offsets), max(offsets))
print('VALIDATED full 192 frames, 24 fps; hand displacement', round(hand_motion, 3),
      'wand displacement', round(wand_motion, 3), 'grip offset variation', round(max(offsets)-min(offsets), 6))
