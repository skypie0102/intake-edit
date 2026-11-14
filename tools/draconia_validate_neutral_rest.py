"""Check the arms-down rest pose and the hidden, attachable wand prop.

blender -b MODEL.blend --python-exit-code 1 --python tools/draconia_validate_neutral_rest.py
These checks cover the neutral stance and a few prop/arm poses, not every pose.
"""
import bpy
import math
from mathutils import Vector
from mathutils.bvhtree import BVHTree

rig = bpy.data.objects['Draconia Body Rig - experimental']
wand = bpy.data.objects['Wand']
assert wand.hide_render and wand.hide_get(), 'Wand must be hidden by default'
assert not any(m.type == 'ARMATURE' for m in wand.modifiers), 'Wand should be an independent rigid prop'
attach = wand.constraints['Wand hand attachment']
assert attach.type == 'CHILD_OF' and attach.target == rig and attach.subtarget == 'Hand.L'
assert attach.influence == 0, 'Wand should start detached'
assert rig.animation_data is None or rig.animation_data.action is None
for name in ('UpperArm.L', 'Forearm.L', 'Hand.L', 'UpperArm.R', 'Forearm.R', 'Hand.R'):
    bone = rig.pose.bones[name]
    assert (bone.matrix_basis.to_translation()).length < 1e-5, name
    assert bone.matrix_basis.to_quaternion().angle < 1e-4, name
    assert max(abs(x-1) for x in bone.matrix_basis.to_scale()) < 1e-5, name
left = rig.matrix_world @ rig.pose.bones['Hand.L'].head
right = rig.matrix_world @ rig.pose.bones['Hand.R'].head
assert abs(left.z-right.z) < .05 and abs(left.x+right.x) < .05, (left, right)
for side in ('L', 'R'):
    arm = rig.pose.bones[f'Forearm.{side}']
    direction = (rig.matrix_world.to_3x3() @ (arm.tail-arm.head)).normalized()
    assert direction.z < -.9, (side, direction)
    assert (rig.matrix_world @ arm.head).z > (rig.matrix_world @ arm.tail).z + .15
print('HAND_POSITIONS', tuple(left), tuple(right), flush=True)

deforming = {bone.name for bone in rig.data.bones if bone.use_deform}
for obj in bpy.data.objects:
    if obj.type != 'MESH':
        continue
    assert len(obj.data.uv_layers) > 0, obj.name
    ids = {group.index for group in obj.vertex_groups if group.name in deforming}
    for vertex in obj.data.vertices:
        total = sum(g.weight for g in vertex.groups if g.group in ids)
        assert abs(total-1) < 1e-4, (obj.name, vertex.index, total)
assert all(image.packed_file for image in bpy.data.images if image.source == 'FILE')
assert bpy.data.objects['Circle'].data.shape_keys.key_blocks.get('Gaze_X_1')


def tree(name):
    obj = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    result = BVHTree.FromPolygons([obj.matrix_world @ v.co for v in mesh.vertices],
                                  [tuple(p.vertices) for p in mesh.polygons], epsilon=1e-6)
    obj.to_mesh_clear()
    return result

legs = tree('Legs')
for name in ('Cloak', 'Skirt', 'Arms'):
    intersections = len(tree(name).overlap(legs))
    print('NEUTRAL_LEG_OVERLAPS', name, intersections, flush=True)
    assert intersections == 0, (name, intersections)

# The wand is hidden, but its attachment is ready for an animator to key.
# Unhiding here is temporary; this test does not save the file.
wand.hide_set(False)
def centre():
    obj = wand.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    value = sum((obj.matrix_world @ v.co for v in mesh.vertices), Vector()) / len(mesh.vertices)
    obj.to_mesh_clear()
    return value

bpy.context.view_layer.update()
attach.influence = 0
bpy.context.view_layer.update()
unattached = centre()
attach.influence = 1
bpy.context.view_layer.update()
assert (centre()-unattached).length < 1e-5, 'Wand jumps at neutral attachment'
points = []
for angle in (0, 20, -20, 40):
    arm = rig.pose.bones['Forearm.L']
    arm.rotation_mode = 'XYZ'
    arm.rotation_euler.x = math.radians(angle)
    bpy.context.view_layer.update()
    hand = rig.matrix_world @ rig.pose.bones['Hand.L'].matrix
    points.append(hand.inverted() @ centre())
drift = max((p-points[0]).length for p in points)
assert drift < 1e-5, drift
print('WAND_ATTACHMENT_LOCAL_DRIFT', drift, flush=True)
print('NEUTRAL_REST_CHECKS_OK', flush=True)
