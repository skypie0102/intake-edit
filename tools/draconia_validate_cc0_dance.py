"""Check the retargeted CC0 dance, loop, floor and garment interaction.

blender -b DANCE.blend --python-exit-code 1 --python tools/draconia_validate_cc0_dance.py
The intersection and floor checks sample specific frames, not every pose or view.
"""
import bpy
import math
import sys
from pathlib import Path
from mathutils import Vector
from mathutils.bvhtree import BVHTree

sys.path.insert(0, str(Path(__file__).resolve().parent))
from draconia_retarget_cc0_dance import MAPPING


def mesh(name):
    obj = bpy.data.objects[name].evaluated_get(bpy.context.evaluated_depsgraph_get())
    evaluated = obj.to_mesh()
    vertices = [obj.matrix_world @ v.co for v in evaluated.vertices]
    polygons = [tuple(p.vertices) for p in evaluated.polygons]
    obj.to_mesh_clear()
    return vertices, polygons


def tree(data):
    return BVHTree.FromPolygons(*data, all_triangles=False, epsilon=1e-6)


def set_frame(value):
    frame = math.floor(value)
    bpy.context.scene.frame_set(frame, subframe=value-frame)
    bpy.context.view_layer.update()


def main():
    scene = bpy.context.scene
    rig = bpy.data.objects['Draconia Body Rig - experimental']
    source = bpy.data.objects['CC0 Dance Source']
    assert scene.render.fps == 30
    assert tuple(rig.animation_data.action.frame_range) == (1.0, 31.0)
    assert all(image.packed_file for image in bpy.data.images if image.source == 'FILE')
    set_frame(1)
    source_reference = {n: (source.matrix_world @ source.pose.bones[n].matrix).to_quaternion()
                        for n, _ in MAPPING}
    target_reference = {n: (rig.matrix_world @ rig.pose.bones[n].matrix).to_quaternion()
                        for _, n in MAPPING}
    initial = {n: mesh(n)[0] for n in ('Cloak', 'Skirt', 'Legs', 'Head', 'Arms', 'Wand')}
    floor = min(v.z for v in initial['Legs'])
    max_motion = 0.0
    max_transfer_error = 0.0
    for frame in (1, 6, 11, 16, 21, 25):
        set_frame(frame)
        for source_name, target_name in MAPPING:
            source_current = (source.matrix_world @ source.pose.bones[source_name].matrix).to_quaternion()
            target_current = (rig.matrix_world @ rig.pose.bones[target_name].matrix).to_quaternion()
            source_delta = source_current @ source_reference[source_name].inverted()
            target_delta = target_current @ target_reference[target_name].inverted()
            max_motion = max(max_motion, target_delta.angle)
            max_transfer_error = max(max_transfer_error, source_delta.rotation_difference(target_delta).angle)
    assert max_motion > .1, max_motion
    assert max_transfer_error < math.radians(.1), math.degrees(max_transfer_error)
    print('MAX_TRANSFER_ERROR_DEGREES', math.degrees(max_transfer_error))
    print('MAX_SOURCE_DRIVEN_ROTATION_DEGREES', math.degrees(max_motion))
    wand_positions = []
    floor_min = float('inf')
    pairs = [0, 0]
    for i in range(20):
        set_frame(1 + 1.5*i)
        legs, cloak, skirt, wand = (mesh(n) for n in ('Legs', 'Cloak', 'Skirt', 'Wand'))
        floor_min = min(floor_min, min(v.z for v in legs[0]))
        leg_tree = tree(legs)
        pairs[0] += len(tree(cloak).overlap(leg_tree))
        pairs[1] += len(tree(skirt).overlap(leg_tree))
        centre = sum(wand[0], Vector()) / len(wand[0])
        hand = rig.matrix_world @ rig.pose.bones['Hand.L'].matrix
        wand_positions.append(hand.inverted() @ centre)
    drift = max((p - wand_positions[0]).length for p in wand_positions)
    assert drift < 1e-5, drift
    assert floor_min >= floor - .002, (floor, floor_min)
    print('PREVIEW_SAMPLES', 20, 'CLOAK_LEG_PAIRS', pairs[0], 'SKIRT_LEG_PAIRS', pairs[1])
    print('LOWEST_BOOT_DELTA', floor_min-floor, 'WAND_LOCAL_DRIFT', drift)
    set_frame(31)
    max_loop = 0.0
    for name, vertices in initial.items():
        end = mesh(name)[0]
        assert len(vertices) == len(end)
        max_loop = max(max_loop, max(((a-b).length for a, b in zip(vertices, end)), default=0))
    assert max_loop < 1e-4, max_loop
    print('LOOP_MAX_VERTEX_DIFFERENCE', max_loop)
    print('DANCE_CHECKS_OK')


if __name__ == '__main__':
    main()
