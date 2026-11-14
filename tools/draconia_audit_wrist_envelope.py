"""Compare finger/clothing intersections across sampled wrist turns.

blender -b ARM_CLEARANCE.blend --python-exit-code 1 --python tools/draconia_audit_wrist_envelope.py
Counts are face pairs in selected poses, not visibility or collision proof.
"""
import math

import bpy
from mathutils.bvhtree import BVHTree

rig = bpy.data.objects['Draconia Body Rig - experimental']
assert rig.get('NeutralArmClearance') == 1.0
assert not rig.animation_data.action


def tree(name, fingers_only=False):
    source = bpy.data.objects[name]
    obj = source.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    vertices = [obj.matrix_world @ v.co for v in mesh.vertices]
    if fingers_only:
        ids = {group.index for group in source.vertex_groups
               if group.name.startswith(('Index.', 'Middle.', 'Ring.', 'Pinky.', 'Thumb.'))}
        chosen = {v.index for v in source.data.vertices
                  if sum(w.weight for w in v.groups if w.group in ids) > .5}
        assert chosen and len(source.data.vertices) == len(mesh.vertices)
        faces = [tuple(p.vertices) for p in mesh.polygons if any(i in chosen for i in p.vertices)]
    else:
        faces = [tuple(p.vertices) for p in mesh.polygons]
    obj.to_mesh_clear()
    return BVHTree.FromPolygons(vertices, faces, epsilon=1e-6)


results = {}
for strength in (0.0, 1.0):
    rig['NeutralArmClearance'] = strength
    rig.update_tag()
    bpy.context.view_layer.update()
    for side in ('L', 'R'):
        wrist = rig.pose.bones['Hand.' + side]
        wrist.rotation_mode = 'XYZ'
        wrist.rotation_euler = (0, 0, 0)
        bpy.context.view_layer.update()
        targets = {name: tree(name) for name in ('Cloak', 'Jacket Hands', 'Legs')}
        for axis in range(3):
            for angle in (-60, -45, -30, -15, 0, 15, 30, 45, 60):
                wrist.rotation_euler = (0, 0, 0)
                wrist.rotation_euler[axis] = math.radians(angle)
                bpy.context.view_layer.update()
                fingers = tree('Articulated hand study ' + side, fingers_only=True)
                results[strength, side, axis, angle] = {
                    name: len(fingers.overlap(target)) for name, target in targets.items()}
        wrist.rotation_euler = (0, 0, 0)
        bpy.context.view_layer.update()

for side in ('L', 'R'):
    before = [results[0.0, side, axis, angle]['Cloak']
              for axis in range(3) for angle in (-60, -45, -30, -15, 0, 15, 30, 45, 60)]
    after = [results[1.0, side, axis, angle]['Cloak']
             for axis in range(3) for angle in (-60, -45, -30, -15, 0, 15, 30, 45, 60)]
    assert results[0.0, side, 0, 0]['Cloak'] > 0 and results[1.0, side, 0, 0]['Cloak'] == 0
    assert sum(after) < sum(before)
    worse = [(axis, angle, results[0.0, side, axis, angle]['Cloak'],
              results[1.0, side, axis, angle]['Cloak'])
             for axis in range(3) for angle in (-60, -45, -30, -15, 0, 15, 30, 45, 60)
             if results[1.0, side, axis, angle]['Cloak'] > results[0.0, side, axis, angle]['Cloak']]
    assert all(not results[value, side, axis, angle][name]
               for value in (0.0, 1.0) for axis in range(3)
               for angle in (-60, -45, -30, -15, 0, 15, 30, 45, 60)
               for name in ('Jacket Hands', 'Legs'))
    print('WRIST_ENVELOPE', side, 'pairs', sum(before), '->', sum(after),
          'max', max(before), '->', max(after), 'worse poses', worse, flush=True)
print('WRIST_ENVELOPE_AUDITED', flush=True)
