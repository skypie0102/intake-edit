"""Build an alternate chibi hand study with a mitten and separate thumb.

blender -b INPUT.blend --python-exit-code 1 --python tools/draconia_make_chibi_hand_study.py -- OUTPUT.blend
The original mitten-shaped arm mesh remains hidden in this study. Each new
hand has a combined four-finger section, a separate thumb, and a wrist bridge.
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
if len(original.data.vertices) != 86 or rig.animation_data and rig.animation_data.action:
    raise ValueError('Expected the unanimated original hand rig')
if any(name.startswith(('Thumb.01.', 'Fingers.01.')) for name in rig.data.bones.keys()):
    raise ValueError('Expected no existing chibi digit bones')
if any(abs(rig.matrix_world[i][j] - Matrix.Identity(4)[i][j]) > 1e-6 for i in range(4) for j in range(4)):
    raise ValueError('Expected the current rig transform')
uv_source = original.data.uv_layers.active
if not uv_source:
    raise ValueError('Expected source skin UVs')
skin_uv = uv_source.data[original.data.polygons[0].loop_indices[0]].uv.copy()

bpy.ops.object.select_all(action='DESELECT')
rig.select_set(True)
bpy.context.view_layer.objects.active = rig
bpy.ops.object.mode_set(mode='EDIT')
thumb_points = {}
for side, sign in (('L', -1), ('R', 1)):
    center_x = sign * .38
    hand = rig.data.edit_bones['Hand.' + side]
    for label, points in (
        ('Fingers', (Vector((center_x, -.055, .785)), Vector((center_x, -.055, .75)),
                     Vector((center_x, -.055, .71)))),
        ('Thumb', (Vector((center_x - sign * .032, -.105, .824)),
                   Vector((center_x - sign * .052, -.115, .804)),
                   Vector((center_x - sign * .065, -.12, .785))))):
        start, middle, end = points
        first = rig.data.edit_bones.new(f'{label}.01.{side}')
        first.head, first.tail, first.parent = start, middle, hand
        second = rig.data.edit_bones.new(f'{label}.02.{side}')
        second.head, second.tail, second.parent, second.use_connect = middle, end, first, True
    # Geometry's thumb center is offset from its bend pivot as in the joined study.
    thumb_points[side] = (Vector((center_x - sign * .032, -.075, .824)),
                          Vector((center_x - sign * .065, -.09, .785)))
bpy.ops.object.mode_set(mode='OBJECT')
original.hide_render = True
original.hide_set(True)

for side, sign in (('L', -1), ('R', 1)):
    center_x = sign * .38
    vertices, faces, weights = [], [], []

    def vertex(point, influences):
        vertices.append(tuple(point))
        weights.append(influences)
        return len(vertices) - 1

    rows = []
    # Wrist, palm, and the four-finger mitten share one ring-based surface.
    # Weights blend gradually from forearm to hand to the two finger joints.
    sections = (
        (.98, .036, .043, {'Forearm.' + side: 1.0}),
        (.95, .036, .043, {'Forearm.' + side: 1.0}),
        (.91, .034, .040, {'Forearm.' + side: .7, 'Hand.' + side: .3}),
        (.88, .030, .032, {'Forearm.' + side: .15, 'Hand.' + side: .85}),
        (.855, .027, .026, {'Hand.' + side: 1.0}),
        (.82, .040, .033, {'Hand.' + side: 1.0}),
        (.79, .040, .030, {'Hand.' + side: 1.0}),
        (.78, .039, .029, {'Hand.' + side: .75, 'Fingers.01.' + side: .25}),
        (.765, .038, .027, {'Fingers.01.' + side: 1.0}),
        (.75, .037, .026, {'Fingers.01.' + side: .75, 'Fingers.02.' + side: .25}),
        (.735, .035, .025, {'Fingers.01.' + side: .35, 'Fingers.02.' + side: .65}),
        (.72, .027, .020, {'Fingers.02.' + side: 1.0}),
        (.711, .014, .012, {'Fingers.02.' + side: 1.0}),
        (.708, .006, .006, {'Fingers.02.' + side: 1.0}),
    )
    for z, rx, ry, assigned in sections:
        row = []
        for i in range(12):
            angle = 2 * math.pi * i / 12
            row.append(vertex((center_x + rx * math.cos(angle), -.065 + ry * math.sin(angle), z), assigned))
        rows.append(row)
    for upper, lower in zip(rows, rows[1:]):
        for i in range(12):
            faces.append((upper[i], upper[(i + 1) % 12], lower[(i + 1) % 12], lower[i]))
    faces.extend((tuple(reversed(rows[0])), tuple(rows[-1])))

    # The thumb overlaps the palm at its root; the following voxel union joins it.
    start, end = thumb_points[side]
    axis = (end - start).normalized()
    u = axis.cross(Vector((0, 1, 0))).normalized()
    v = axis.cross(u).normalized()
    rows = []
    for position, scale in ((0, .8), (.22, 1), (.49, .95), (.56, .92), (.82, .85), (1, .43)):
        center = start.lerp(end, position)
        if position <= .22:
            influences = {'Hand.' + side: (.22 - position) / .22, f'Thumb.01.{side}': position / .22}
        elif position <= .49:
            influences = {f'Thumb.01.{side}': 1.0}
        elif position <= .56:
            mix = (position - .49) / .07
            influences = {f'Thumb.01.{side}': 1 - mix, f'Thumb.02.{side}': mix}
        else:
            influences = {f'Thumb.02.{side}': 1.0}
        row = []
        for i in range(8):
            angle = 2 * math.pi * i / 8
            row.append(vertex(center + .012 * scale * (math.cos(angle) * u + math.sin(angle) * v), influences))
        rows.append(row)
    for upper, lower in zip(rows, rows[1:]):
        for i in range(8):
            faces.append((upper[i], upper[(i + 1) % 8], lower[(i + 1) % 8], lower[i]))
    faces.extend((tuple(reversed(rows[0])), tuple(rows[-1])))

    mesh = bpy.data.meshes.new('Chibi hand source ' + side)
    mesh.from_pydata(vertices, [], faces)
    mesh.update()
    source = bpy.data.objects.new('Chibi hand source ' + side, mesh)
    bpy.context.collection.objects.link(source)
    mesh.materials.append(bpy.data.materials['Main Body'])
    uv = mesh.uv_layers.new(name='UVMap')
    for loop in uv.data:
        loop.uv = skin_uv
    groups = {name: source.vertex_groups.new(name=name)
              for name in sorted({name for assigned in weights for name in assigned})}
    for index, assigned in enumerate(weights):
        for name, value in assigned.items():
            if value > 1e-8:
                groups[name].add([index], value, 'REPLACE')
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(mesh)
    bm.free()
    mesh.update()

    joined = source.copy()
    joined.data = source.data.copy()
    joined.name = 'Articulated hand study ' + side
    bpy.context.collection.objects.link(joined)
    bpy.ops.object.select_all(action='DESELECT')
    joined.select_set(True)
    bpy.context.view_layer.objects.active = joined
    joined.data.remesh_voxel_size = .0015
    bpy.ops.object.voxel_remesh()
    if joined.vertex_groups:
        raise ValueError('Expected voxel remesh to clear vertex groups')
    for group in source.vertex_groups:
        joined.vertex_groups.new(name=group.name)
    transfer = joined.modifiers.new('Transfer skin', 'DATA_TRANSFER')
    transfer.object = source
    transfer.use_vert_data = True
    transfer.data_types_verts = {'VGROUP_WEIGHTS'}
    transfer.vert_mapping = 'POLYINTERP_NEAREST'
    transfer.layers_vgroup_select_src = 'ALL'
    transfer.layers_vgroup_select_dst = 'NAME'
    bpy.ops.object.modifier_apply(modifier=transfer.name)
    decimate = joined.modifiers.new('Reduce topology', 'DECIMATE')
    decimate.ratio = .25
    bpy.ops.object.modifier_apply(modifier=decimate.name)
    joined.data.materials.clear()
    joined.data.materials.append(bpy.data.materials['Main Body'])
    uv = joined.data.uv_layers.active or joined.data.uv_layers.new(name='UVMap')
    for loop in uv.data:
        loop.uv = skin_uv
    modifier = joined.modifiers.new('Body rig skin', 'ARMATURE')
    modifier.object = rig
    bm = bmesh.new()
    bm.from_mesh(joined.data)
    nonmanifold = sum(not edge.is_manifold for edge in bm.edges)
    bm.free()
    if nonmanifold:
        raise ValueError(f'Hand {side} has {nonmanifold} nonmanifold edges')
    source.hide_render = True
    source.hide_set(True)
    print('CHIBI_HAND', side, 'vertices', len(joined.data.vertices),
          'faces', len(joined.data.polygons), 'nonmanifold', nonmanifold, flush=True)

scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('CHIBI_HAND_STUDY_CREATED', flush=True)
