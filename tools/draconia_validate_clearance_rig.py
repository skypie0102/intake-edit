"""Check the clearance refinement preserves the other meshes and bindings.

blender -b --python-exit-code 1 --python tools/draconia_validate_clearance_rig.py -- BEFORE.blend AFTER.blend
The checks use several poses and do not guarantee collision-free movement.
"""
import bpy
import sys
from pathlib import Path
from mathutils import Vector

sys.path.insert(0, str(Path(__file__).resolve().parent))
from draconia_validate_sleeve_edges import RIG, pose, geometry, check_equal

poses = ({}, {'Torso': (0, 0, 20)}, {'Head': (20, 0, 15)},
         {'UpperArm.L': (0, 0, -40), 'Forearm.L': (45, 0, 0)},
         {'UpperArm.R': (0, 0, -40), 'Forearm.R': (60, 0, 0)},
         {'Thigh.R': (45, 0, 0), 'Shin.R': (-20, 0, 0)})
paths = sys.argv[sys.argv.index('--')+1:]
if len(paths) != 2:
    raise ValueError('Expected BEFORE.blend AFTER.blend')
all_samples = []
for path in paths:
    bpy.ops.wm.open_mainfile(filepath=path)
    rig = bpy.data.objects[RIG]
    deforms = {bone.name for bone in rig.data.bones if bone.use_deform}
    for obj in bpy.data.objects:
        if obj.type != 'MESH':
            continue
        ids = {g.index for g in obj.vertex_groups if g.name in deforms}
        for vertex in obj.data.vertices:
            total = sum(g.weight for g in vertex.groups if g.group in ids)
            assert abs(total - 1) < 1e-4, (obj.name, vertex.index, total)
    samples = []
    for values in poses:
        pose(rig, values)
        samples.append(geometry())
    all_samples.append(samples)
for i, (before, after) in enumerate(zip(*all_samples)):
    print('UNCHANGED_MESH_MAX', i, check_equal(before, after, exclude=('Hair', 'Jacket', 'Cloak')))
# Check the edited rest meshes for folded faces and severe area collapse.
# This guards the clothing fix against hiding clipping by inverting geometry.
for name, minimum in (('Hair', .5), ('Jacket', .35), ('Cloak', .5)):
    before_vertices, before_faces = all_samples[0][0][name]
    after_vertices, after_faces = all_samples[1][0][name]
    assert before_faces == after_faces
    ratios, normals = [], []
    def area_normal(vertices, face):
        origin = vertices[face[0]]
        normal = sum(((vertices[face[i]] - origin).cross(vertices[face[i+1]] - origin)
                      for i in range(1, len(face)-1)), Vector())
        return normal.length, normal.normalized()
    for face in before_faces:
        old_area, old_normal = area_normal(before_vertices, face)
        new_area, new_normal = area_normal(after_vertices, face)
        if old_area > 1e-8:
            ratios.append(new_area / old_area)
            normals.append(old_normal.dot(new_normal))
    print('REST_FACE_CHECK', name, 'MIN_AREA_RATIO', min(ratios), 'MIN_NORMAL_DOT', min(normals))
    assert min(ratios) > minimum and min(normals) > 0, name
assert 'Torso' in bpy.data.objects['Hair'].vertex_groups
assert all(image.packed_file for image in bpy.data.images if image.source == 'FILE')
print('CLEARANCE_RIG_CHECKS_OK')
