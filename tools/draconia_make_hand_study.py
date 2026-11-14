"""Create a separate experimental articulated-hand study.

blender -b INPUT.blend --python-exit-code 1 --python tools/draconia_make_hand_study.py -- OUTPUT.blend

The source arms end in mitten-shaped hands. This study hides that mesh and
adds separate low-poly replacement hands with two deform bones per digit. The
replacement parts overlap at their roots and are not a finished watertight
hand mesh; the original remains in the scene for comparison.
"""
import math
import sys
from pathlib import Path

import bmesh
import bpy
from mathutils import Matrix, Vector

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 1:
    raise ValueError('Expected OUTPUT.blend')
output = Path(args[0]).resolve()
if output.exists() or output == Path(bpy.data.filepath).resolve():
    raise ValueError('Output must be a new file')
scene = bpy.context.scene
scene.frame_set(1)
rig = bpy.data.objects['Draconia Body Rig - experimental']
original = bpy.data.objects['Arms']
if len(original.data.vertices) != 86 or any(name.startswith(('Thumb.01.', 'Index.01.')) for name in rig.data.bones.keys()):
    raise ValueError('Expected the current mitten hands without finger bones')
if any(abs(rig.matrix_world[i][j] - Matrix.Identity(4)[i][j]) > 1e-6 for i in range(4) for j in range(4)):
    raise ValueError('Expected the current rig transform')
if not original.data.uv_layers.active:
    raise ValueError('Expected the source skin UV map')
# Use a skin-colored source texel and the original material for visual continuity.
first_loop = original.data.polygons[0].loop_indices[0]
skin_uv = original.data.uv_layers.active.data[first_loop].uv.copy()
material = bpy.data.materials['Main Body']

# The mirrored source hands are around x=+/-0.38 in this rig's rest coordinates.
bpy.ops.object.select_all(action='DESELECT')
rig.select_set(True)
bpy.context.view_layer.objects.active = rig
bpy.ops.object.mode_set(mode='EDIT')
digits = {}
for side, sign in (('L', -1), ('R', 1)):
    hand_bone = rig.data.edit_bones['Hand.' + side]
    center_x = sign * .38
    for label, offset, length in (('Index', -.028, .067), ('Middle', -.0095, .078),
                                  ('Ring', .0095, .073), ('Pinky', .028, .059)):
        start = Vector((center_x + sign * offset, -.075, .785))
        middle = start + Vector((0, 0, -length * .52))
        end = start + Vector((0, 0, -length))
        digits[(side, label)] = (start, middle, end, [])
    digits[(side, 'Thumb')] = (
        Vector((center_x - sign * .032, -.075, .824)),
        Vector((center_x - sign * .052, -.085, .804)),
        Vector((center_x - sign * .065, -.09, .785)), [])
    for label in ('Index', 'Middle', 'Ring', 'Pinky', 'Thumb'):
        start, middle, end, names = digits[(side, label)]
        for number, head, tail in ((1, start, middle), (2, middle, end)):
            name = f'{label}.{number:02d}.{side}'
            bone = rig.data.edit_bones.new(name)
            bone.head, bone.tail = head, tail
            bone.parent = hand_bone if number == 1 else rig.data.edit_bones[names[0]]
            bone.use_connect = number == 2
            names.append(name)
bpy.ops.object.mode_set(mode='OBJECT')

# The source remains available in the file; hiding it is confined to this study.
original.hide_render = True
original.hide_set(True)
for side, sign in (('L', -1), ('R', 1)):
    vertices, faces, weights = [], [], []
    center_x = sign * .38

    def vertex(point, assigned):
        vertices.append(tuple(point))
        weights.append(assigned)
        return len(vertices) - 1

    # A low-poly palm overlaps the finger roots. This is intentionally a visual
    # study, not a single continuous topology suitable for close-up production.
    palm_rows = []
    for z, rx, ry in ((.875, .026, .026), (.85, .036, .032), (.82, .041, .033),
                       (.79, .040, .030), (.77, .034, .027), (.758, .026, .023)):
        row = []
        for i in range(12):
            angle = 2 * math.pi * i / 12
            point = Vector((center_x + rx * math.cos(angle), -.065 + ry * math.sin(angle), z))
            row.append(vertex(point, {'Hand.' + side: 1.0}))
        palm_rows.append(row)
    for upper, lower in zip(palm_rows, palm_rows[1:]):
        for i in range(12):
            faces.append((upper[i], upper[(i + 1) % 12], lower[(i + 1) % 12], lower[i]))
    faces.extend((tuple(reversed(palm_rows[0])), tuple(palm_rows[-1])))

    for label in ('Index', 'Middle', 'Ring', 'Pinky', 'Thumb'):
        start, _, end, names = digits[(side, label)]
        axis = (end - start).normalized()
        u = axis.cross(Vector((0, 1, 0))).normalized()
        v = axis.cross(u).normalized()
        radius = .012 if label == 'Thumb' else .010
        rows = []
        for position, scale in ((0, .8), (.22, 1), (.49, .95), (.56, .92), (.82, .85), (1, .43)):
            center = start.lerp(end, position)
            if position <= .22:
                influence = {'Hand.' + side: (.22 - position) / .22, names[0]: position / .22}
            elif position <= .49:
                influence = {names[0]: 1.0}
            elif position <= .56:
                mix = (position - .49) / .07
                influence = {names[0]: 1 - mix, names[1]: mix}
            else:
                influence = {names[1]: 1.0}
            row = []
            for i in range(8):
                angle = 2 * math.pi * i / 8
                point = center + radius * scale * (math.cos(angle) * u + math.sin(angle) * v)
                row.append(vertex(point, influence))
            rows.append(row)
        for upper, lower in zip(rows, rows[1:]):
            for i in range(8):
                faces.append((upper[i], upper[(i + 1) % 8], lower[(i + 1) % 8], lower[i]))
        faces.extend((tuple(reversed(rows[0])), tuple(rows[-1])))

    mesh = bpy.data.meshes.new('Hand study ' + side)
    mesh.from_pydata(vertices, [], faces)
    mesh.update()
    obj = bpy.data.objects.new('Articulated hand study ' + side, mesh)
    bpy.context.collection.objects.link(obj)
    mesh.materials.append(material)
    uv = mesh.uv_layers.new(name='UVMap')
    for loop in uv.data:
        loop.uv = skin_uv
    groups = {name: obj.vertex_groups.new(name=name)
              for name in sorted({name for assigned in weights for name in assigned})}
    for i, assigned in enumerate(weights):
        for name, value in assigned.items():
            if value > 1e-8:
                groups[name].add([i], value, 'REPLACE')
    modifier = obj.modifiers.new('Body rig skin', 'ARMATURE')
    modifier.object = rig
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(mesh)
    bm.free()
    mesh.update()

bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('HAND_STUDY_CREATED', len(digits), 'digits, two bones each', flush=True)
