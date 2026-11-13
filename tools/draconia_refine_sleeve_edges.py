"""Refine the cloak cuff edges and fix stray hip weights on an undersleeve.

Run with Blender 4.3.2:
blender -b --python-exit-code 1 --python tools/draconia_refine_sleeve_edges.py -- INPUT.blend OUTPUT.blend
The input is the garment-follow study; the output must be a new file.
"""
import bpy
import sys
from pathlib import Path


def main():
    args = sys.argv[sys.argv.index('--') + 1:]
    if len(args) != 2:
        raise ValueError('Expected INPUT.blend OUTPUT.blend')
    source, output = (Path(p).resolve() for p in args)
    if source == output or output.exists():
        raise ValueError('Output must be a new file, separate from the input')
    bpy.ops.wm.open_mainfile(filepath=str(source))
    rig = bpy.data.objects['Draconia Body Rig - experimental']
    cloak = bpy.data.objects['Cloak']
    if len(cloak.data.vertices) != 969 or len(cloak.data.polygons) != 998:
        raise ValueError('Expected the cloak in the garment-follow study')
    arm_groups = {g.index for g in cloak.vertex_groups
                  if g.name.startswith(('UpperArm.', 'Forearm.'))}
    deform_groups = {b.name for b in rig.data.bones if b.use_deform}
    mask = cloak.vertex_groups['Sleeve corrective influence']
    parents = list(range(len(cloak.data.vertices)))

    def root(i):
        while parents[i] != i:
            parents[i] = parents[parents[i]]
            i = parents[i]
        return i

    def arm_weight(vertex):
        return sum(g.weight for g in vertex.groups if g.group in arm_groups)

    edges = []
    for edge in cloak.data.edges:
        a, b = edge.vertices
        va, vb = cloak.data.vertices[a], cloak.data.vertices[b]
        if (va.co - vb.co).length < 0.006 and min(arm_weight(va), arm_weight(vb)) > 0.3:
            ra, rb = root(a), root(b)
            if ra != rb:
                parents[rb] = ra
            edges.append((a, b))
    if len(edges) != 26:
        raise ValueError(f'Expected 26 thin sleeve edges, found {len(edges)}')
    components = {}
    for edge in edges:
        for i in edge:
            components.setdefault(root(i), set()).add(i)
    for indices in components.values():
        means = {}
        for i in indices:
            for weight in cloak.data.vertices[i].groups:
                name = cloak.vertex_groups[weight.group].name
                if name in deform_groups:
                    means[name] = means.get(name, 0.0) + weight.weight / len(indices)
        if abs(sum(means.values()) - 1.0) > 1e-4:
            raise ValueError(f'Unnormalized source weights at sleeve edge {indices}')
        for i in indices:
            existing = {x.group for x in cloak.data.vertices[i].groups}
            for group in cloak.vertex_groups:
                if group.name in deform_groups:
                    value = means.get(group.name, 0.0)
                    if value > 1e-8:
                        group.add([i], value, 'REPLACE')
                    elif group.index in existing:
                        group.remove([i])
            # Corrective smoothing separates the two very close edge surfaces.
            # Keep this small strip on its matched armature weights instead.
            mask.add([i], 0.0, 'REPLACE')
    print('REFINED_SLEEVE_EDGES', len(edges), 'VERTICES', sum(map(len, components.values())))

    # Two cuff vertices are close to the wrist but were nearly half weighted to
    # the hips, so shoulder movement tore the cuff away from the forearm.
    jacket = bpy.data.objects['Jacket Hands']
    if len(jacket.data.vertices) != 130:
        raise ValueError('Expected the undersleeves in the garment-follow study')
    hips = jacket.vertex_groups['Hips']
    forearm = jacket.vertex_groups['Forearm.R']
    smooth = jacket.vertex_groups['Sleeve corrective influence']

    def group_weight(vertex, group):
        return next((w.weight for w in vertex.groups if w.group == group.index), 0.0)

    cuff = []
    for vertex in jacket.data.vertices:
        co = jacket.matrix_world @ vertex.co
        old_hips = group_weight(vertex, hips)
        if co.x > 0.32 and co.z < 0.9 and old_hips > 0.2:
            cuff.append(vertex.index)
    if set(cuff) != {100, 104}:
        raise ValueError(f'Unexpected cuff vertices: {cuff}')
    for i in cuff:
        vertex = jacket.data.vertices[i]
        old_hips = group_weight(vertex, hips)
        old_forearm = group_weight(vertex, forearm)
        old_smooth = group_weight(vertex, smooth)
        hips.add([i], 0.0, 'REPLACE')
        forearm.add([i], old_forearm + old_hips, 'REPLACE')
        smooth.add([i], min(1.0, old_smooth + old_hips), 'REPLACE')
    print('REFINED_UNDERSLEEVE_CUFF', cuff)
    bpy.ops.wm.save_as_mainfile(filepath=str(output))


if __name__ == '__main__':
    main()
