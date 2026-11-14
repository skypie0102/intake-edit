"""Check that corner and iris geometry closes without changing the rest pose."""
import bpy

rig = bpy.data.objects['Draconia Body Rig - experimental']
assert rig['Blink'] == 0 and bpy.data.objects['Wand'].hide_render
outline = bpy.data.objects['Plane.004']
basis = outline.data.shape_keys.key_blocks['Basis']
closed = outline.data.shape_keys.key_blocks['Blink']
# The frame remains a visible strip while its hanging corners are compressed.
assert max(v.co.z for v in closed.data) - min(v.co.z for v in closed.data) < .06
assert max(v.co.z for v in closed.data) - min(v.co.z for v in closed.data) > .01
for name in ('Plane.006', 'Plane.007'):
    obj = bpy.data.objects[name]
    key = obj.data.shape_keys.key_blocks['Blink']
    for face in obj.data.polygons:
        a, b, c = (key.data[i].co for i in face.vertices)
        assert (b - a).cross(c - a).length < 1e-8
for name in ('Circle', 'Circle.001', 'Circle.002'):
    obj = bpy.data.objects[name]
    key = obj.data.shape_keys.key_blocks['Blink']
    for face in obj.data.polygons:
        points = [key.data[i].co for i in face.vertices]
        assert max(p.x for p in points) - min(p.x for p in points) < 1e-6
for value in (0, .5, 1):
    rig['Blink'] = value
    rig.update_tag()
    bpy.context.view_layer.update()
    for name in ('Plane.004', 'Plane.006', 'Plane.007', 'Circle', 'Circle.001', 'Circle.002'):
        assert abs(bpy.data.objects[name].data.shape_keys.key_blocks['Blink'].value - value) < 1e-5
    print('BLINK_CORNER_VALUE', value, flush=True)
print('BLINK_CORNERS_OK', flush=True)
