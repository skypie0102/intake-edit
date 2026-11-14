"""Match the distorted sleeve to its opposite using corresponding UV vertices.

blender -b --python-exit-code 1 --python tools/draconia_match_neutral_sleeve.py -- INPUT.blend OUTPUT.blend
The transition is blended into the connected cloak. The wand is not changed.
"""
import bpy
import sys
from collections import defaultdict
from mathutils import Vector
from pathlib import Path


def smooth(value):
    x = max(0.0, min(1.0, value))
    return x*x*(3-2*x)


def repair(name, amount):
    obj = bpy.data.objects[name]
    mesh = obj.data
    if not mesh.uv_layers.active:
        raise ValueError(f'Missing UV map: {name}')
    uv = mesh.uv_layers.active.data
    per_vertex = defaultdict(set)
    for face in mesh.polygons:
        for loop in face.loop_indices:
            point = uv[loop].uv
            per_vertex[mesh.loops[loop].vertex_index].add((round(point.x, 5), round(point.y, 5)))
    left_groups = {g.index for g in obj.vertex_groups if g.name in ('UpperArm.L', 'Forearm.L')}
    right_groups = {g.index for g in obj.vertex_groups if g.name in ('UpperArm.R', 'Forearm.R')}
    left, right = [], []
    for vertex in mesh.vertices:
        point = obj.matrix_world @ vertex.co
        if name == 'Jacket Hands':
            if point.x < 0:
                left.append(vertex)
            elif point.x > 0:
                right.append(vertex)
        elif point.x < -.12 and sum(w.weight for w in vertex.groups if w.group in left_groups) > .15:
            left.append(vertex)
        elif point.x > .12 and sum(w.weight for w in vertex.groups if w.group in right_groups) > .15:
            right.append(vertex)
    lookup = defaultdict(list)
    for vertex in right:
        for key in per_vertex[vertex.index]:
            lookup[key].append(vertex.index)
    group_map = {}
    for group in obj.vertex_groups:
        if group.name.endswith('.R'):
            other = obj.vertex_groups.get(group.name[:-2]+'.L')
        elif group.name.endswith('.L'):
            other = obj.vertex_groups.get(group.name[:-2]+'.R')
        else:
            other = None
        group_map[group.index] = other.index if other else group.index
    points = [obj.matrix_world @ vertex.co for vertex in mesh.vertices]
    original_weights = [{g.group: g.weight for g in vertex.groups} for vertex in mesh.vertices]
    inverse = obj.matrix_world.inverted()
    moves = []
    for vertex in left:
        candidates = {index for key in per_vertex[vertex.index] for index in lookup[key]}
        if not candidates:
            continue
        point = points[vertex.index]
        mirror = Vector((-point.x, point.y, point.z))
        index = min(candidates, key=lambda i: (points[i]-mirror).length)
        target = points[index].copy()
        target.x = -target.x
        if name == 'Jacket Hands':
            alpha = amount
        else:
            arm = sum(w.weight for w in vertex.groups if w.group in left_groups)
            alpha = amount*smooth((arm-.15)/.35)*smooth((-point.x-.12)/.16)
        if alpha < 1e-6:
            continue
        vertex.co = inverse @ point.lerp(target, alpha)
        moves.append((point-target).length*alpha)
        old = original_weights[vertex.index]
        new = {group_map[k]: weight for k, weight in original_weights[index].items()}
        for group_index in old.keys() | new.keys():
            weight = (1-alpha)*old.get(group_index, 0)+alpha*new.get(group_index, 0)
            obj.vertex_groups[group_index].add([vertex.index], max(0.0, min(1.0, weight)), 'REPLACE')
    if not moves:
        raise ValueError(f'No matched sleeve vertices: {name}')
    print('MATCHED_SLEEVE', name, 'count', len(moves), 'maxmove', max(moves), flush=True)


def main():
    args = sys.argv[sys.argv.index('--')+1:]
    if len(args) != 2:
        raise ValueError('Expected INPUT.blend OUTPUT.blend')
    source, output = (Path(p).resolve() for p in args)
    if source == output or output.exists():
        raise ValueError('Output must be a new file')
    bpy.ops.wm.open_mainfile(filepath=str(source))
    for name in ('Cloak', 'Jacket Hands'):
        repair(name, .7)
    bpy.ops.wm.save_as_mainfile(filepath=str(output))

if __name__ == '__main__':
    main()
