"""Join the experimental palms and fingers into closed skinned meshes.

blender -b HAND_STUDY.blend --python-exit-code 1 --python tools/draconia_join_hand_study.py -- OUTPUT.blend
The source pieces remain hidden in the study. A skinned wrist bridge is
joined to each palm and fingers; remeshing and weight transfer are approximate.
These hands still require wider animation and grip review.
"""
import math
import sys
from pathlib import Path

import bmesh
import bpy

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 1:
    raise ValueError('Expected OUTPUT.blend')
output = Path(args[0]).resolve()
if output.exists() or output == Path(bpy.data.filepath).resolve():
    raise ValueError('Output must be a new file')
scene = bpy.context.scene
scene.frame_set(1)
rig = bpy.data.objects['Draconia Body Rig - experimental']
if rig.animation_data and rig.animation_data.action:
    raise ValueError('Expected the unanimated hand study')
for side in ('L', 'R'):
    obj = bpy.data.objects.get('Articulated hand study ' + side)
    if obj is None or len(obj.data.vertices) != 312:
        raise ValueError('Expected the current segmented hand study')

# A small pivot shift places the curl hinge closer to the outer palm surface.
# The separate source hand retains its original rest shape and its rig groups.
bpy.ops.object.select_all(action='DESELECT')
rig.select_set(True)
bpy.context.view_layer.objects.active = rig
bpy.ops.object.mode_set(mode='EDIT')
for side in ('L', 'R'):
    for label in ('Index', 'Middle', 'Ring', 'Pinky'):
        first = rig.data.edit_bones[f'{label}.01.{side}']
        second = rig.data.edit_bones[f'{label}.02.{side}']
        second.use_connect = False
        for bone in (first, second):
            bone.head.y += .01
            bone.tail.y += .01
            bone.head.z -= .005
            bone.tail.z -= .005
        second.use_connect = True
    # The thumb bends in the opposite local X direction from the fingers.
    first = rig.data.edit_bones[f'Thumb.01.{side}']
    second = rig.data.edit_bones[f'Thumb.02.{side}']
    second.use_connect = False
    for bone in (first, second):
        bone.head.y -= .03
        bone.tail.y -= .03
    second.use_connect = True
bpy.ops.object.mode_set(mode='OBJECT')

for side, sign in (('L', -1), ('R', 1)):
    original = bpy.data.objects['Articulated hand study ' + side]
    original.name = 'Unjoined hand study ' + side
    source = original.copy()
    source.data = original.data.copy()
    source.name = 'Hand retopology source ' + side
    bpy.context.collection.objects.link(source)
    center_x = sign * .38
    vertices = source.data.vertices
    # Shorten the palm below the finger roots and add space between the four
    # fingers before joining. Indices are from the guarded source generator.
    for i in range(48, 72):
        point = vertices[i].co
        if i < 60:
            point.x = center_x + (point.x - center_x) * (.038 / .034)
            point.y = -.065 + (point.y + .065) * (.028 / .027)
            point.z = .779
        else:
            point.x = center_x + (point.x - center_x) * (.032 / .026)
            point.y = -.065 + (point.y + .065) * (.024 / .023)
            point.z = .775
    for digit, offset in enumerate((-.028, -.0095, .0095, .028)):
        finger_x = center_x + sign * offset
        for i in range(72 + digit * 48, 72 + (digit + 1) * 48):
            point = vertices[i].co
            point.x = finger_x + .8 * (point.x - finger_x)
            point.y = -.075 + .8 * (point.y + .075)
            point.z -= .005
    source.data.update()

    # Extend a skinned wrist/forearm bridge inside the cuff. The lower ring
    # overlaps the palm and becomes part of the same closed remesh.
    bm = bmesh.new()
    bm.from_mesh(source.data)
    rows = []
    for z, rx, ry in ((.855, .027, .026), (.88, .030, .032),
                       (.91, .034, .040), (.95, .036, .043), (.98, .036, .043)):
        row = []
        for i in range(12):
            angle = 2 * math.pi * i / 12
            row.append(bm.verts.new((center_x + rx * math.cos(angle),
                                     -.065 + ry * math.sin(angle), z)))
        rows.append(row)
    for upper, lower in zip(rows, rows[1:]):
        for i in range(12):
            bm.faces.new((upper[i], upper[(i + 1) % 12], lower[(i + 1) % 12], lower[i]))
    bm.faces.new(tuple(reversed(rows[0])))
    bm.faces.new(tuple(rows[-1]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.verts.index_update()
    added = [[vertex.index for vertex in row] for row in rows]
    bm.to_mesh(source.data)
    bm.free()
    hand_group = source.vertex_groups['Hand.' + side]
    forearm_group = source.vertex_groups.get('Forearm.' + side) or source.vertex_groups.new(name='Forearm.' + side)
    for indices, influence in zip(added, (0.0, .15, .7, 1.0, 1.0)):
        if influence < 1:
            hand_group.add(indices, 1 - influence, 'REPLACE')
        if influence > 0:
            forearm_group.add(indices, influence, 'REPLACE')
    source.data.update()

    joined = source.copy()
    joined.data = source.data.copy()
    joined.name = 'Articulated hand study ' + side
    bpy.context.collection.objects.link(joined)
    for modifier in list(joined.modifiers):
        joined.modifiers.remove(modifier)
    bpy.ops.object.select_all(action='DESELECT')
    joined.select_set(True)
    bpy.context.view_layer.objects.active = joined
    joined.data.remesh_voxel_size = .0015
    bpy.ops.object.voxel_remesh()
    if joined.vertex_groups:
        raise ValueError('Expected the voxel remesh to clear vertex groups')
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
    source_uv = original.data.uv_layers.active.data[0].uv.copy()
    for loop in uv.data:
        loop.uv = source_uv
    skin = joined.modifiers.new('Body rig skin', 'ARMATURE')
    skin.object = rig
    bm = bmesh.new()
    bm.from_mesh(joined.data)
    nonmanifold = sum(not edge.is_manifold for edge in bm.edges)
    bm.free()
    if nonmanifold:
        raise ValueError(f'Joined hand {side} has {nonmanifold} nonmanifold edges')
    original.hide_render = True
    original.hide_set(True)
    source.hide_render = True
    source.hide_set(True)
    print('JOINED_HAND', side, 'vertices', len(joined.data.vertices),
          'faces', len(joined.data.polygons), 'nonmanifold', nonmanifold, flush=True)

scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('JOINED_HAND_STUDY_CREATED', flush=True)
