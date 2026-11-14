"""Check the reversible neutral arm control and its neutral clearance.

blender -b --python-exit-code 1 --python tools/draconia_validate_neutral_arm_clearance.py -- BEFORE.blend AFTER.blend
The surface-pair check covers the neutral pose, not every possible movement.
"""
import sys

import bpy
from mathutils import Matrix
from mathutils.bvhtree import BVHTree

paths = sys.argv[sys.argv.index('--') + 1:]
if len(paths) != 2:
    raise ValueError('Expected BEFORE.blend AFTER.blend')


def snapshot():
    bpy.context.view_layer.update()
    result = {}
    for source in bpy.data.objects:
        if source.type != 'MESH':
            continue
        obj = source.evaluated_get(bpy.context.evaluated_depsgraph_get())
        mesh = obj.to_mesh()
        result[source.name] = ([obj.matrix_world @ v.co for v in mesh.vertices],
                               [tuple(p.vertices) for p in mesh.polygons],
                               [p.area for p in mesh.polygons],
                               [p.normal.copy() for p in mesh.polygons])
        obj.to_mesh_clear()
    return result


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


def finger_contacts():
    targets = {name: tree(name) for name in ('Cloak', 'Jacket Hands', 'Legs')}
    return {(side, name): len(tree('Articulated hand study ' + side, True).overlap(target))
            for side in ('L', 'R') for name, target in targets.items()}


bpy.ops.wm.open_mainfile(filepath=paths[0])
before = snapshot()
bpy.ops.wm.open_mainfile(filepath=paths[1])
rig = bpy.data.objects['Draconia Body Rig - experimental']
assert rig['NeutralArmClearance'] == 1.0 and bpy.data.objects['Wand'].hide_render
for side in ('L', 'R'):
    control = rig.data.bones['ArmClearance.' + side]
    upper = rig.pose.bones['UpperArm.' + side]
    assert not control.use_deform and upper.parent.name == control.name
    assert all(abs(upper.matrix_basis[i][j] - Matrix.Identity(4)[i][j]) < 1e-6
               for i in range(4) for j in range(4))
    shoulder = rig.matrix_world @ upper.head
    hand_tip = rig.matrix_world @ rig.pose.bones['Hand.' + side].tail
    assert hand_tip.z < shoulder.z - .25
active = snapshot()
active_contacts = finger_contacts()
rig['NeutralArmClearance'] = 0.0
rig.update_tag()
bpy.context.view_layer.update()
inactive = snapshot()
inactive_contacts = finger_contacts()
assert before.keys() == inactive.keys()
max_delta = 0.0
for name in before:
    a, b = before[name], inactive[name]
    assert len(a[0]) == len(b[0]) and a[1] == b[1], name
    max_delta = max(max_delta, max(((p - q).length for p, q in zip(a[0], b[0])), default=0))
assert max_delta < 1e-5, max_delta
for name in ('Hair', 'Jacket', 'Legs', 'Head'):
    assert max(((p - q).length for p, q in zip(before[name][0], active[name][0])), default=0) < 1e-5
ratios = [b / a for a, b in zip(before['Cloak'][2], active['Cloak'][2]) if a > 1e-9]
flips = sum(a.dot(b) < 0 for a, b in zip(before['Cloak'][3], active['Cloak'][3]))
assert min(ratios) > .8 and max(ratios) < 1.3 and not flips
for side in ('L', 'R'):
    assert inactive_contacts[side, 'Cloak'] > 0 and active_contacts[side, 'Cloak'] == 0
    assert all(active_contacts[side, name] == 0 for name in ('Jacket Hands', 'Legs'))
print('NEUTRAL_FINGER_CONTACTS', inactive_contacts, '->', active_contacts, flush=True)
print('DISABLED_MAX_DELTA', max_delta, 'CLOAK_AREA_RATIOS', min(ratios), max(ratios),
      'FLIPPED', flips, flush=True)
print('NEUTRAL_ARM_CLEARANCE_CHECKS_OK', flush=True)
