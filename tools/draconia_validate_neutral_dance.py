"""Check full-length wand-free dance motion on the neutral-rest rig.

blender -b DANCE.blend --python-exit-code 1 --python tools/draconia_validate_neutral_dance.py
This samples keyed frames, not all in-between poses or all possible collisions.
"""
import bpy
from mathutils.bvhtree import BVHTree

scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
wand = bpy.data.objects['Wand']
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 192, 24)
assert wand.hide_render and wand.hide_get()
assert rig.animation_data.action.name == 'Four-bar CC0 dance - full 8 seconds'


def mesh(name):
    obj = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
    data = obj.to_mesh()
    verts = [obj.matrix_world @ v.co for v in data.vertices]
    faces = [tuple(p.vertices) for p in data.polygons]
    obj.to_mesh_clear()
    return verts, BVHTree.FromPolygons(verts, faces, epsilon=1e-6)

hands = {'L': [], 'R': []}
leg_overlaps = {'Cloak': 0, 'Skirt': 0, 'Arms': 0}
lowest = float('inf')
for frame in range(1, 193):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    torso = rig.matrix_world @ rig.pose.bones['Torso'].head
    for side in hands:
        hand = rig.matrix_world @ rig.pose.bones[f'Hand.{side}'].head
        hands[side].append((hand-torso).copy())
    verts, legs = mesh('Legs')
    lowest = min(lowest, min(v.z for v in verts))
    for name in leg_overlaps:
        _, garment = mesh(name)
        leg_overlaps[name] += len(garment.overlap(legs))
assert all(count == 0 for count in leg_overlaps.values()), leg_overlaps
for side, positions in hands.items():
    displacement = max((p-positions[0]).length for p in positions)
    assert displacement > .1, (side, displacement)
    print('HAND_MOTION', side, displacement, flush=True)
print('DANCE_LEG_OVERLAPS', leg_overlaps, 'LOWEST_BOOT_Z', lowest, flush=True)
print('NEUTRAL_DANCE_CHECKS_OK', flush=True)
