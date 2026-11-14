"""Check jacket deformation and jacket/cloak intersections over the dance.

blender -b DANCE.blend --python-exit-code 1 --python tools/draconia_validate_jacket_deformation.py
Face-pair counts include hidden layers and do not alone indicate visible clipping.
"""
import bpy
from mathutils.bvhtree import BVHTree

scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 192, 24)
assert 'JacketClearance' in rig
ratios, old_pairs, new_pairs = [], [], []
reversed_normals = 0
max_movement = 0.0


def evaluate(name):
    obj = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    vertices = [obj.matrix_world @ v.co for v in mesh.vertices]
    faces = [tuple(p.vertices) for p in mesh.polygons]
    areas = [p.area for p in mesh.polygons]
    normals = [p.normal.copy() for p in mesh.polygons]
    obj.to_mesh_clear()
    return BVHTree.FromPolygons(vertices, faces, epsilon=1e-6), vertices, areas, normals


for frame in range(1, 193):
    scene.frame_set(frame)
    assert abs(rig['JacketClearance'] - 1) < 1e-5
    variants = []
    for value in (0.0, 1.0):
        rig['JacketClearance'] = value
        rig.update_tag()
        bpy.context.view_layer.update()
        variants.append(evaluate('Jacket'))
    cloak = evaluate('Cloak')[0]
    before, after = variants
    old_pairs.append(len(before[0].overlap(cloak)))
    new_pairs.append(len(after[0].overlap(cloak)))
    max_movement = max(max_movement, max((a - b).length for a, b in zip(before[1], after[1])))
    for area_before, area_after, normal_before, normal_after in zip(before[2], after[2], before[3], after[3]):
        if area_before > 1e-9:
            ratios.append(area_after / area_before)
        reversed_normals += normal_before.dot(normal_after) < 0
assert sum(new_pairs) < sum(old_pairs)
assert not reversed_normals and min(ratios) > .49 and max(ratios) < 1.5
print('JACKET_CLOAK_PAIRS', sum(old_pairs), '->', sum(new_pairs),
      'MAX', max(old_pairs), '->', max(new_pairs), flush=True)
print('JACKET_AREA_RATIOS', min(ratios), max(ratios), 'REVERSED_NORMALS', reversed_normals,
      'MAX_MOVEMENT', max_movement, flush=True)
print('JACKET_DEFORMATION_CHECKS_OK', flush=True)
