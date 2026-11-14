"""Check the repaired neutral arm, sleeve topology, weights, and UVs.

blender -b --python-exit-code 1 --python tools/draconia_validate_neutral_arm.py -- BEFORE.blend AFTER.blend
These checks cover the rest shape; deformation still needs motion testing.
"""
import bpy
import sys
from pathlib import Path
from mathutils import Vector

sys.path.insert(0, str(Path(__file__).resolve().parent))
from draconia_validate_sleeve_edges import RIG, pose, geometry, check_equal

paths = sys.argv[sys.argv.index('--')+1:]
if len(paths) != 2:
    raise ValueError('Expected BEFORE.blend AFTER.blend')
meshes, evaluated = [], []
for path in paths:
    bpy.ops.wm.open_mainfile(filepath=path)
    rig = bpy.data.objects[RIG]
    pose(rig, {})
    evaluated.append(geometry())
    data = {}
    for name in ('Cloak', 'Jacket Hands'):
        obj = bpy.data.objects[name]
        data[name] = ([obj.matrix_world @ v.co for v in obj.data.vertices],
                      [tuple(p.vertices) for p in obj.data.polygons])
    meshes.append(data)
print('UNCHANGED_MESH_MAX', check_equal(evaluated[0], evaluated[1], exclude=('Arms', 'Cloak', 'Jacket Hands')), flush=True)


def area_normal(vertices, face):
    origin = vertices[face[0]]
    normal = sum(((vertices[face[i]]-origin).cross(vertices[face[i+1]]-origin)
                  for i in range(1, len(face)-1)), Vector())
    return normal.length, normal.normalized()

for name in ('Cloak', 'Jacket Hands'):
    before, faces = meshes[0][name]
    after, after_faces = meshes[1][name]
    assert faces == after_faces
    ratios, dots = [], []
    for face in faces:
        old, a = area_normal(before, face)
        new, b = area_normal(after, face)
        if old > 1e-8:
            ratios.append(new/old)
            dots.append(a.dot(b))
    print('SLEEVE_TOPOLOGY', name, 'MIN_AREA', min(ratios), 'MAX_AREA', max(ratios), 'MIN_NORMAL_DOT', min(dots), flush=True)
    assert min(ratios) > .5 and max(ratios) < 2.1 and min(dots) > 0, name

obj = bpy.data.objects['Arms']
assert len(obj.data.vertices) == 86 and len(obj.data.polygons) == 84 and obj.data.uv_layers
left = [v for v in obj.data.vertices if (obj.matrix_world @ v.co).x < 0]
right = [v for v in obj.data.vertices if (obj.matrix_world @ v.co).x > 0]
assert len(left) == len(right) == 43
left_by_position = {tuple(round(x, 6) for x in (obj.matrix_world @ v.co)): v for v in left}
for vertex in right:
    p = obj.matrix_world @ vertex.co
    match = left_by_position[(-round(p.x, 6), round(p.y, 6), round(p.z, 6))]
    for source, target in (('Forearm.R', 'Forearm.L'), ('Hand.R', 'Hand.L')):
        def group_weight(v, name):
            index = obj.vertex_groups[name].index
            return next((g.weight for g in v.groups if g.group == index), 0.0)
        assert abs(group_weight(vertex, source)-group_weight(match, target)) < 1e-5
uv = obj.data.uv_layers.active.data
face_uvs = {'left': [], 'right': []}
for face in obj.data.polygons:
    x = sum((obj.matrix_world @ obj.data.vertices[i].co).x for i in face.vertices) / len(face.vertices)
    side = 'left' if x < 0 else 'right'
    face_uvs[side].extend((round(uv[i].uv.x, 6), round(uv[i].uv.y, 6)) for i in face.loop_indices)
assert sorted(face_uvs['left']) == sorted(face_uvs['right']), 'Mirrored hand UVs differ'
# Check the actual face assignment and outward normals, not only total UVs.
normal_matrix = obj.matrix_world.to_3x3().inverted().transposed()
def face_key(face, mirror=False):
    points = []
    for index in face.vertices:
        p = obj.matrix_world @ obj.data.vertices[index].co
        if mirror:
            p.x = -p.x
        points.append(tuple(round(x, 6) for x in p))
    return tuple(sorted(points))
left_faces = {face_key(face): face for face in obj.data.polygons
              if sum((obj.matrix_world @ obj.data.vertices[i].co).x for i in face.vertices) < 0}
for face in obj.data.polygons:
    if sum((obj.matrix_world @ obj.data.vertices[i].co).x for i in face.vertices) <= 0:
        continue
    reflected = left_faces[face_key(face, mirror=True)]
    n = (normal_matrix @ face.normal).normalized()
    n.x = -n.x
    assert n.dot((normal_matrix @ reflected.normal).normalized()) > .999
    a = sorted((round(uv[i].uv.x, 6), round(uv[i].uv.y, 6)) for i in face.loop_indices)
    b = sorted((round(uv[i].uv.x, 6), round(uv[i].uv.y, 6)) for i in reflected.loop_indices)
    assert a == b, 'Mirrored face UV mapping differs'
print('MIRRORED_HAND_VERTICES', len(left), 'UV_LOOPS', len(face_uvs['left']), flush=True)
print('NEUTRAL_ARM_CHECKS_OK', flush=True)
