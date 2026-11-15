"""Check the chibi reach's hand clearance and cloak deformation at keyed frames.

blender -b REACH.blend --python-exit-code 1 --python tools/draconia_validate_chibi_reach.py
Face-pair counts include hidden layered intersections and do not cover subframes.
"""
import bpy
from mathutils.bvhtree import BVHTree

scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 48, 24)
assert bpy.data.objects['Wand'].hide_render and bpy.data.objects['Arms'].hide_render


def sample_mesh(name, fingers_only=False):
    source = bpy.data.objects[name]
    obj = source.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = obj.to_mesh()
    vertices = [obj.matrix_world @ v.co for v in mesh.vertices]
    if fingers_only:
        ids = {g.index for g in source.vertex_groups if g.name.startswith(('Fingers.', 'Thumb.'))}
        chosen = {v.index for v in source.data.vertices
                  if sum(w.weight for w in v.groups if w.group in ids) > .5}
        assert chosen and len(source.data.vertices) == len(mesh.vertices)
        polygons = [tuple(p.vertices) for p in mesh.polygons if any(i in chosen for i in p.vertices)]
    else:
        polygons = [tuple(p.vertices) for p in mesh.polygons]
    tree = BVHTree.FromPolygons(vertices, polygons, epsilon=1e-6)
    areas = [p.area for p in mesh.polygons]
    normals = [p.normal.copy() for p in mesh.polygons]
    obj.to_mesh_clear()
    return tree, areas, normals


active = .6
totals = {strength: {(side, name): 0 for side in ('L', 'R')
                     for name in ('Cloak', 'Jacket Hands', 'Legs')} for strength in (0, active)}
hair_pairs = {0: [], active: []}
ratios, flips = [], 0
leg_pairs = 0
for frame in range(1, 49):
    scene.frame_set(frame)
    cloaks = []
    for strength in (0, active):
        rig['NeutralArmClearance'] = float(strength)
        rig.update_tag()
        bpy.context.view_layer.update()
        meshes = {name: sample_mesh(name) for name in ('Cloak', 'Jacket Hands', 'Legs', 'Hair', 'Skirt')}
        targets = {name: meshes[name][0] for name in ('Cloak', 'Jacket Hands', 'Legs')}
        for side in ('L', 'R'):
            fingers = sample_mesh('Articulated hand study ' + side, True)[0]
            for name, target in targets.items():
                totals[strength][side, name] += len(fingers.overlap(target))
        hair_pairs[strength].append(len(meshes['Hair'][0].overlap(meshes['Cloak'][0])))
        if strength == active:
            leg_pairs += len(meshes['Cloak'][0].overlap(meshes['Legs'][0]))
            leg_pairs += len(meshes['Skirt'][0].overlap(meshes['Legs'][0]))
        cloaks.append(meshes['Cloak'])
    for old_area, new_area, old_normal, new_normal in zip(
            cloaks[0][1], cloaks[1][1], cloaks[0][2], cloaks[1][2]):
        if old_area > 1e-9:
            ratios.append(new_area / old_area)
        flips += old_normal.dot(new_normal) < 0
assert not any(totals[active].values()), totals[active]
assert totals[0]['R', 'Cloak'] > 0 and not leg_pairs
assert not flips and min(ratios) > .5 and max(ratios) < 2, (min(ratios), max(ratios), flips)
for frame, value in ((1, 0), (9, .5), (10, 1), (11, 2 / 3), (12, 1 / 3), (13, 0), (32, 1), (35, 0), (48, 0)):
    scene.frame_set(frame)
    assert abs(rig['Blink'] - value) < 1e-5
print('REACH_FINGER_CONTACTS', totals[0], '->', totals[active], flush=True)
print('REACH_HAIR_CLOAK_PAIRS', sum(hair_pairs[0]), '->', sum(hair_pairs[active]),
      'MAX', max(hair_pairs[0]), '->', max(hair_pairs[active]), flush=True)
print('REACH_CLOAK_AREA_RATIO', min(ratios), max(ratios), 'FLIPPED', flips,
      'GARMENT_LEG_PAIRS', leg_pairs, flush=True)
