"""Verify that the hand shading leaves rig and mesh geometry intact.

blender -b --python-exit-code 1 --python tools/draconia_validate_chibi_shading.py -- BEFORE.blend AFTER.blend
"""
import sys

import bpy

paths = sys.argv[sys.argv.index('--') + 1:]
if len(paths) != 2:
    raise ValueError('Expected BEFORE.blend AFTER.blend')
hands = {'Articulated hand study L', 'Articulated hand study R'}


def snapshot():
    result = {}
    for obj in bpy.data.objects:
        if obj.type != 'MESH':
            continue
        mesh = obj.data
        result[obj.name] = {
            'vertices': [v.co.copy() for v in mesh.vertices],
            'faces': [tuple(p.vertices) for p in mesh.polygons],
            'weights': [[(obj.vertex_groups[g.group].name, g.weight) for g in v.groups] for v in mesh.vertices],
            'uv': [tuple(loop.uv) for loop in mesh.uv_layers.active.data] if mesh.uv_layers.active else [],
            'materials': [m.name for m in mesh.materials],
            'smooth': sum(p.use_smooth for p in mesh.polygons),
            'matrix': tuple(value for row in obj.matrix_world for value in row),
            'hidden': obj.hide_render,
        }
    return result


bpy.ops.wm.open_mainfile(filepath=paths[0])
before = snapshot()
bpy.ops.wm.open_mainfile(filepath=paths[1])
after = snapshot()
assert before.keys() == after.keys()
for name in before:
    a, b = before[name], after[name]
    assert a['vertices'] == b['vertices'] and a['faces'] == b['faces'], name
    assert a['weights'] == b['weights'] and a['uv'] == b['uv'], name
    assert a['matrix'] == b['matrix'] and a['hidden'] == b['hidden'], name
    if name in hands:
        assert a['materials'] == ['Main Body'] and b['materials'] == ['Chibi hand soft shading']
        assert b['smooth'] == len(b['faces'])
    else:
        assert a['materials'] == b['materials'] and a['smooth'] == b['smooth'], name
material = bpy.data.materials['Chibi hand soft shading']
level = material.node_tree.nodes['Soft hand depth']
assert level.operation == 'MULTIPLY_ADD'
assert abs(level.inputs[1].default_value - .22) < 1e-6
assert abs(level.inputs[2].default_value - .78) < 1e-6
assert bpy.data.objects['Wand'].hide_render
print('CHIBI_HAND_SHADING_GEOMETRY_AND_OTHER_MATERIALS_UNCHANGED', flush=True)
