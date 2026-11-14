"""Check that the eye frames follow the blink while the neutral pose stays open."""
import bpy

rig = bpy.data.objects['Draconia Body Rig - experimental']
outline = bpy.data.objects['Plane.004']
keys = outline.data.shape_keys.key_blocks
assert rig['Blink'] == 0 and bpy.data.objects['Wand'].hide_render
basis, closed = keys['Basis'], keys['Blink']
changes = [(closed.data[i].co - basis.data[i].co).length for i in range(len(outline.data.vertices))]
assert min(changes) > .01 and max(changes) > .05
assert bpy.data.materials['Head'].node_tree.nodes['Descending eyelid cover'].operation == 'GREATER_THAN'
for value in (0, .5, 1):
    rig['Blink'] = value
    rig.update_tag()
    bpy.context.view_layer.update()
    for name in ('Plane.004', 'Circle', 'Circle.001', 'Circle.002'):
        assert abs(bpy.data.objects[name].data.shape_keys.key_blocks['Blink'].value - value) < 1e-5
    print('EYELID_BLINK', value, flush=True)
print('REFINED_BLINK_CHECKS_OK', flush=True)
