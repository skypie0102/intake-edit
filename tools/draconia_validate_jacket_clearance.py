"""Audit front jacket breakthrough and rear clearance in the full dance.

blender -b DANCE.blend --python-exit-code 1 --python tools/draconia_validate_jacket_clearance.py
The front metric counts disconnected jacket-visible regions on a sampled ray
grid; it does not prove all views or poses are free of clipping.
"""
from collections import deque

import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree

scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 192, 24)
assert 'JacketClearance' in rig and bpy.data.objects['Wand'].hide_render
forward, backward = Vector((0, 1, 0)), Vector((0, -1, 0))


def tree(name):
    obj = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    vertices = [obj.matrix_world @ vertex.co for vertex in mesh.vertices]
    faces = [tuple(face.vertices) for face in mesh.polygons]
    obj.to_mesh_clear()
    return BVHTree.FromPolygons(vertices, faces, epsilon=1e-6)


def front(jacket, occluders, cx, cz):
    visible = set()
    for ix in range(-42, 43):
        for iz in range(12, 64):
            origin = Vector((cx + ix * .008, -5, cz + iz * .008))
            hit = jacket.ray_cast(origin, forward)
            if hit[0] is None:
                continue
            distance = hit[3]
            if any((other := occluder.ray_cast(origin, forward))[0] is not None
                   and other[3] < distance - 1e-5 for occluder in occluders):
                continue
            visible.add((ix, iz))
    center = sum(abs(ix) <= 6 for ix, _ in visible)
    total = len(visible)
    components = []
    while visible:
        queue = deque([visible.pop()])
        size = 0
        while queue:
            x, z = queue.popleft()
            size += 1
            for point in ((x - 1, z), (x + 1, z), (x, z - 1), (x, z + 1)):
                if point in visible:
                    visible.remove(point)
                    queue.append(point)
        if size >= 2:
            components.append(size)
    components.sort(reverse=True)
    return {'islands': sum(components[1:]), 'center': center, 'all': total}


def rear(jacket, cloak, cx, cz):
    count = 0
    for ix in range(-24, 25):
        for iz in range(16, 55):
            origin = Vector((cx + ix * .01, 5, cz + iz * .01))
            inner = jacket.ray_cast(origin, backward)
            outer = cloak.ray_cast(origin, backward)
            if inner[0] is not None and outer[0] is not None and inner[3] + .001 < outer[3]:
                count += 1
    return count


values = []
for frame in range(1, 193):
    scene.frame_set(frame)
    assert abs(rig['JacketClearance'] - 1) < 1e-5
    bpy.context.view_layer.update()
    hips = rig.matrix_world @ rig.pose.bones['Hips'].head
    occluders = [tree(name) for name in ('Cloak', 'Hair', 'Arms', 'Jacket Hands', 'Head', 'Plane.001')]
    variants = []
    for value in (0.0, 1.0):
        rig['JacketClearance'] = value
        rig.update_tag()
        bpy.context.view_layer.update()
        jacket = tree('Jacket')
        variants.append({'front': front(jacket, occluders, hips.x, hips.z),
                         'rear': rear(jacket, occluders[0], hips.x, hips.z)})
    values.append(variants)
    if frame % 48 == 0:
        print('CHECKED_JACKET_FRAME', frame, flush=True)

for field in ('islands', 'center', 'all'):
    before = [v[0]['front'][field] for v in values]
    after = [v[1]['front'][field] for v in values]
    print('FRONT', field, 'before', sum(before), 'after', sum(after),
          'max', max(before), '->', max(after),
          'worse', sum(b > a for a, b in zip(before, after)), flush=True)
old_islands = [v[0]['front']['islands'] for v in values]
new_islands = [v[1]['front']['islands'] for v in values]
old_rear = [v[0]['rear'] for v in values]
new_rear = [v[1]['rear'] for v in values]
print('REAR_BREAKTHROUGH_RAYS', sum(old_rear), '->', sum(new_rear), flush=True)
assert sum(old_islands) > 0 and sum(new_islands) < .01 * sum(old_islands)
assert all(after <= before + 2 for before, after in zip(old_islands, new_islands))
assert all(abs(v[0]['front']['center'] - v[1]['front']['center']) <= 3 for v in values)
assert all(after <= before for before, after in zip(old_rear, new_rear))
print('JACKET_VISIBILITY_CHECKS_OK', flush=True)
