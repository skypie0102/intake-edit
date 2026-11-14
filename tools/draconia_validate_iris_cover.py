"""Verify that blinking covers, rather than deforms, the iris meshes."""
import bpy

rig = bpy.data.objects['Draconia Body Rig - experimental']
assert rig['Blink'] == 0 and bpy.data.objects['Wand'].hide_render
reference = bpy.data.objects['Eyelid mask reference']
assert reference.parent == rig and reference.parent_type == 'BONE' and reference.parent_bone == 'Head'
material = bpy.data.materials['Iris - eyelid coverage']
assert material.node_tree.nodes['Eyelid coverage'].operation == 'GREATER_THAN'
for name in ('Circle', 'Circle.001', 'Circle.002'):
    obj = bpy.data.objects[name]
    assert obj.data.materials[0] == material
    keys = obj.data.shape_keys.key_blocks
    assert all((a.co - b.co).length < 1e-8 for a, b in zip(keys['Blink'].data, keys['Basis'].data))

# Compare evaluated vertices at several blink values with gaze held fixed. The
# eyes must retain their shape and position even though the shader covers them.
for gaze in (-1.0, 0.0, 1.0):
    rig['Gaze_X'] = gaze
    rig['Gaze_Z'] = gaze
    snapshots = {}
    for blink in (0.0, .5, 1.0):
        rig['Blink'] = blink
        rig.update_tag()
        bpy.context.view_layer.update()
        graph = bpy.context.evaluated_depsgraph_get()
        for name in ('Circle', 'Circle.001', 'Circle.002'):
            evaluated = bpy.data.objects[name].evaluated_get(graph)
            mesh = evaluated.to_mesh()
            vertices = [v.co.copy() for v in mesh.vertices]
            evaluated.to_mesh_clear()
            if blink == 0:
                snapshots[name] = vertices
            else:
                assert max((a - b).length for a, b in zip(vertices, snapshots[name])) < 1e-6
            keys = bpy.data.objects[name].data.shape_keys.key_blocks
            if gaze:
                suffix = '1' if gaze > 0 else '-1'
                for axis in ('X', 'Z'):
                    assert abs(keys[f'Gaze_{axis}_{suffix}'].value - 1) < 1e-5
        assert abs(material.node_tree.nodes['Blink control'].outputs[0].default_value - blink) < 1e-5
        print('IRIS_UNCHANGED', 'gaze', gaze, 'blink', blink, flush=True)

# The mask reference must follow the head bone instead of staying in world space.
rig['Blink'] = rig['Gaze_X'] = rig['Gaze_Z'] = 0
bone = rig.pose.bones['Head']
original = bone.matrix_basis.copy()
bpy.context.view_layer.update()
relative_before = (rig.matrix_world @ bone.matrix).inverted() @ reference.matrix_world
bone.rotation_mode = 'XYZ'
bone.rotation_euler.y += .25
bpy.context.view_layer.update()
relative_after = (rig.matrix_world @ bone.matrix).inverted() @ reference.matrix_world
assert max(abs(relative_before[i][j] - relative_after[i][j]) for i in range(4) for j in range(4)) < 1e-5
bone.matrix_basis = original
print('IRIS_COVER_CHECKS_OK', flush=True)
