"""Check the keyed reach study's movement and sampled leg clearance.

blender -b STUDY.blend --python-exit-code 1 --python tools/draconia_validate_motion_study.py
Surface intersections include pre-existing layers; this is not a collision solver.
"""
import bpy
from mathutils.bvhtree import BVHTree

scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 48, 24)
assert bpy.data.objects['Wand'].hide_render

def mesh(name):
    obj = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
    data = obj.to_mesh()
    vertices = [obj.matrix_world @ v.co for v in data.vertices]
    faces = [tuple(p.vertices) for p in data.polygons]
    obj.to_mesh_clear()
    return vertices, BVHTree.FromPolygons(vertices, faces, epsilon=1e-6)

initial = None
hair_move = 0
leg_pairs = {'Cloak': 0, 'Skirt': 0, 'Arms': 0}
hair_pairs = []
for frame in range(1, 49):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    hair, hair_tree = mesh('Hair')
    if initial is None:
        initial = [v.copy() for v in hair]
    hair_move = max(hair_move, max((a-b).length for a, b in zip(hair, initial)))
    _, legs = mesh('Legs')
    for name in leg_pairs:
        _, other = mesh(name)
        leg_pairs[name] += len(other.overlap(legs))
    _, cloak = mesh('Cloak')
    hair_pairs.append(len(cloak.overlap(hair_tree)))
assert hair_move > .02, hair_move
assert all(count == 0 for count in leg_pairs.values()), leg_pairs
print('REACH_LEG_FACE_PAIRS', leg_pairs, 'HAIR_CLOAK_MAX', max(hair_pairs), 'HAIR_WORLD_MOVE', hair_move, flush=True)
print('MOTION_STUDY_CHECKED', flush=True)
