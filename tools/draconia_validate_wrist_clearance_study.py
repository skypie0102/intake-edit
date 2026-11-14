"""Audit the moderate wrist test's motion and sampled finger clearance.

blender -b WRIST_STUDY.blend --python-exit-code 1 --python tools/draconia_validate_wrist_clearance_study.py
The test covers keyed frames, not every pose or continuous time.
"""
import math
import sys
from pathlib import Path

import bpy
from mathutils.bvhtree import BVHTree

sys.path.insert(0, str(Path(__file__).resolve().parent))
from draconia_hand_intersections import intersection_segments

scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 96, 24)
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


counts = {(side, name): 0 for side in ('L', 'R') for name in ('Cloak', 'Jacket Hands', 'Legs')}
maximum = (0.0, 0, '')
angle_max = [0.0, 0.0, 0.0]
for frame in range(1, 97):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    assert abs(rig['NeutralArmClearance'] - 1) < 1e-6
    targets = {name: tree(name) for name in ('Cloak', 'Jacket Hands', 'Legs')}
    for side in ('L', 'R'):
        bone = rig.pose.bones['Hand.' + side]
        for axis in range(3):
            angle_max[axis] = max(angle_max[axis], abs(bone.rotation_euler[axis]))
        obj = bpy.data.objects['Articulated hand study ' + side]
        fingers = tree(obj.name, fingers_only=True)
        for name, target in targets.items():
            counts[side, name] += len(fingers.overlap(target))
        length = max(intersection_segments(obj), default=0)
        maximum = max(maximum, (length, frame, side))
assert not any(counts.values()), counts
assert all(math.radians(14) < angle <= math.radians(15.01) for angle in angle_max)
# A small wrist self-intersection remains; this is a bounded diagnostic rather
# than a claim of fully collision-free skinning.
assert maximum[0] < .002, maximum
print('WRIST_STUDY_FINGER_CONTACTS', counts, 'MAX_SELF_INTERSECTION_SEGMENT', maximum, flush=True)
print('WRIST_CLEARANCE_STUDY_CHECKS_OK', flush=True)
