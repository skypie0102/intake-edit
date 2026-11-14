"""Check the experimental blink, gaze interactions and default state."""
import bpy

rig = bpy.data.objects['Draconia Body Rig - experimental']
assert rig['Blink'] == 0 and bpy.data.objects['Wand'].hide_render
node = bpy.data.materials['Head'].node_tree.nodes['Blink control']
for blink, gaze in ((0, 1), (0.5, 1), (1, 1), (1, -1), (0, -1)):
    rig['Blink'] = blink
    rig['Gaze_X'] = gaze
    rig['Gaze_Z'] = gaze
    rig.update_tag()
    bpy.context.view_layer.update()
    for name in ('Circle', 'Circle.001', 'Circle.002'):
        keys = bpy.data.objects[name].data.shape_keys.key_blocks
        assert abs(keys['Blink'].value-blink) < 1e-5
        suffix = '1' if gaze > 0 else '-1'
        for axis in ('X', 'Z'):
            assert abs(keys[f'Gaze_{axis}_{suffix}'].value-(1-blink)) < 1e-5
    value = node.outputs[0].default_value
    assert abs(value-blink) < 1e-5, (blink, value)
    print('BLINK_GAZE', blink, gaze, 'MATERIAL_VALUE', value, flush=True)
print('BLINK_STUDY_CHECKS_OK', flush=True)
