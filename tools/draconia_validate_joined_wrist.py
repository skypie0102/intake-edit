"""Check the joined wrist follows both forearm and hand bones.

blender -b JOINED_HAND.blend --python-exit-code 1 --python tools/draconia_validate_joined_wrist.py
This checks skinning response, not every sleeve collision or gesture.
"""
import math

import bpy

rig = bpy.data.objects['Draconia Body Rig - experimental']


def coordinates(obj):
    evaluated = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = evaluated.to_mesh()
    vertices = [evaluated.matrix_world @ v.co for v in mesh.vertices]
    evaluated.to_mesh_clear()
    return vertices


for side in ('L', 'R'):
    obj = bpy.data.objects['Articulated hand study ' + side]
    other = bpy.data.objects['Articulated hand study ' + ('R' if side == 'L' else 'L')]
    hand_group = obj.vertex_groups['Hand.' + side].index
    forearm_group = obj.vertex_groups['Forearm.' + side].index
    bridge = [v for v in obj.data.vertices
              if any(g.group == hand_group and g.weight > .1 for g in v.groups)
              and any(g.group == forearm_group and g.weight > .1 for g in v.groups)]
    forearm_only = [v.index for v in obj.data.vertices
                    if any(g.group == forearm_group and g.weight > .9999 for g in v.groups)]
    palm = [v.index for v in obj.data.vertices
            if any(g.group == hand_group and g.weight > .99 for g in v.groups)]
    assert bridge and forearm_only and palm
    bpy.context.view_layer.update()
    base, other_base = coordinates(obj), coordinates(other)
    hand = rig.pose.bones['Hand.' + side]
    hand.rotation_mode = 'XYZ'
    hand.rotation_euler.x = math.radians(20)
    bpy.context.view_layer.update()
    posed, other_posed = coordinates(obj), coordinates(other)
    palm_motion = max((posed[i] - base[i]).length for i in palm)
    forearm_drift = max((posed[i] - base[i]).length for i in forearm_only)
    other_drift = max((a - b).length for a, b in zip(other_base, other_posed))
    assert palm_motion > .005 and forearm_drift < 1e-5 and other_drift < 1e-5
    hand.rotation_euler.x = 0
    bpy.context.view_layer.update()
    print('JOINED_WRIST', side, 'blend_vertices', len(bridge), 'palm_motion', palm_motion,
          'forearm_drift', forearm_drift, 'other_hand_drift', other_drift, flush=True)
print('JOINED_WRIST_CHECKS_OK', flush=True)
