"""Check the joined fingers against sleeves and legs over the complete dance.

blender -b DANCE.blend --python-exit-code 1 --python tools/draconia_validate_joined_hand_dance.py
The test considers finger-weighted faces at keyed frames; wrist/sleeve contact
and unsampled times need separate review.
"""
import bpy
from mathutils.bvhtree import BVHTree

scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 192, 24)
assert bpy.data.objects['Arms'].hide_render and bpy.data.objects['Wand'].hide_render


def tree(name, fingers_only=False):
    source = bpy.data.objects[name]
    obj = source.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    vertices = [obj.matrix_world @ vertex.co for vertex in mesh.vertices]
    if fingers_only:
        ids = {group.index for group in source.vertex_groups
               if group.name.startswith(('Index.', 'Middle.', 'Ring.', 'Pinky.', 'Fingers.', 'Thumb.'))}
        chosen = {vertex.index for vertex in source.data.vertices
                  if sum(weight.weight for weight in vertex.groups if weight.group in ids) > .5}
        assert chosen and len(source.data.vertices) == len(mesh.vertices)
        faces = [tuple(face.vertices) for face in mesh.polygons if any(i in chosen for i in face.vertices)]
    else:
        faces = [tuple(face.vertices) for face in mesh.polygons]
    obj.to_mesh_clear()
    return BVHTree.FromPolygons(vertices, faces, epsilon=1e-6)


counts = {(side, name): [] for side in ('L', 'R') for name in ('Cloak', 'Jacket Hands', 'Legs')}
for frame in range(1, 193):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    assert abs(rig['JacketClearance'] - 1) < 1e-5
    targets = {name: tree(name) for name in ('Cloak', 'Jacket Hands', 'Legs')}
    for side in ('L', 'R'):
        fingers = tree('Articulated hand study ' + side, fingers_only=True)
        for name, target in targets.items():
            counts[(side, name)].append(len(fingers.overlap(target)))
for key, values in counts.items():
    assert not any(values), (key, max(values))
    print('FINGER_DANCE_CONTACT', *key, 'pairs', sum(values), flush=True)
for frame, expected in ((32, 0), (33, .5), (34, 1), (35, 2 / 3), (37, 0)):
    scene.frame_set(frame)
    assert abs(rig['Blink'] - expected) < 1e-5
print('JOINED_HAND_DANCE_CHECKS_OK', flush=True)
