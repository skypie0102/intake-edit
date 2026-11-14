"""Audit surface intersections through every keyed frame of a dance.

blender -b --python-exit-code 1 --python tools/draconia_audit_dance_collisions.py -- REST.blend DANCE.blend OUTPUT.json
Face-pair counts include deliberate overlaps in this layered, open-surface model;
compare them to rest and inspect the actual images, not just the raw totals.
"""
import bpy
import json
import sys
from mathutils.bvhtree import BVHTree

PAIRS = (('Cloak', 'Legs'), ('Skirt', 'Legs'), ('Cloak', 'Arms'),
         ('Jacket Hands', 'Arms'), ('Cloak', 'Jacket Hands'),
         ('Cloak', 'Hair'), ('Cloak', 'Head'), ('Cloak', 'Jacket'),
         ('Cloak', 'Plane.001'), ('Arms', 'Legs'),
         ('Wand', 'Head'), ('Wand', 'Hair'), ('Wand', 'Cloak'), ('Wand', 'Jacket'))
NAMES = sorted({name for pair in PAIRS for name in pair})


def trees():
    dg = bpy.context.evaluated_depsgraph_get()
    result = {}
    for name in NAMES:
        obj = bpy.data.objects[name].evaluated_get(dg)
        mesh = obj.to_mesh()
        vertices = [obj.matrix_world @ v.co for v in mesh.vertices]
        polys = [tuple(p.vertices) for p in mesh.polygons]
        result[name] = BVHTree.FromPolygons(vertices, polys, all_triangles=False, epsilon=1e-6)
        obj.to_mesh_clear()
    return result


def overlaps(ts):
    return {f'{a} / {b}': set(ts[a].overlap(ts[b])) for a, b in PAIRS}


rest, dance, output = sys.argv[sys.argv.index('--')+1:]
bpy.ops.wm.open_mainfile(filepath=rest)
bpy.context.scene.frame_set(1)
bpy.context.view_layer.update()
base = overlaps(trees())
bpy.ops.wm.open_mainfile(filepath=dance)
scene = bpy.context.scene
samples = {key: [] for key in base}
for frame in range(scene.frame_start, scene.frame_end + 1):
    scene.frame_set(frame)
    bpy.context.view_layer.update()
    values = overlaps(trees())
    for key, pairs in values.items():
        samples[key].append({'frame': frame, 'pairs': len(pairs), 'new_pairs': len(pairs - base[key]),
                             'faces': sorted(set(i for i, _ in pairs)),
                             'other_faces': sorted(set(i for _, i in pairs))})
    if frame % 24 == 0:
        print('AUDITED', frame, flush=True)
summary = {}
for key, frames in samples.items():
    worst = sorted(frames, key=lambda x: x['pairs'], reverse=True)[:5]
    summary[key] = {'rest_pairs': len(base[key]), 'hit_frames': sum(x['pairs'] > 0 for x in frames),
                    'max_pairs': max(x['pairs'] for x in frames),
                    'total_pairs': sum(x['pairs'] for x in frames),
                    'worst': [(x['frame'], x['pairs'], x['new_pairs']) for x in worst]}
    print('COLLISIONS', key, summary[key], flush=True)
with open(output, 'w') as f:
    json.dump({'summary': summary, 'samples': samples}, f, indent=2)
