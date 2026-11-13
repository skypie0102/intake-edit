"""Check the sleeve-edge refinement against the previous garment scene.

Run: blender -b --python-exit-code 1 --python tools/draconia_validate_sleeve_edges.py -- BEFORE.blend AFTER.blend
The checks cover a few static poses; they do not certify all motion or clipping.
"""
import bpy
import math
import sys
from mathutils import Vector

RIG = 'Draconia Body Rig - experimental'


def pose(rig, values):
    for bone in rig.pose.bones:
        bone.rotation_mode = 'XYZ'
        bone.rotation_euler = (0, 0, 0)
        bone.location = (0, 0, 0)
    for name, angles in values.items():
        rig.pose.bones[name].rotation_euler = [math.radians(a) for a in angles]
    bpy.context.view_layer.update()


def geometry():
    graph = bpy.context.evaluated_depsgraph_get()
    result = {}
    for obj in bpy.data.objects:
        if obj.type != 'MESH':
            continue
        evaluated = obj.evaluated_get(graph)
        mesh = evaluated.to_mesh()
        result[obj.name] = ([evaluated.matrix_world @ v.co for v in mesh.vertices],
                            [tuple(p.vertices) for p in mesh.polygons])
        evaluated.to_mesh_clear()
    return result


def check_equal(a, b, exclude=(), tolerance=1e-5):
    assert a.keys() == b.keys()
    worst = 0.0
    for name in a:
        if name in exclude:
            continue
        va, pa = a[name]
        vb, pb = b[name]
        assert len(va) == len(vb) and pa == pb, name
        worst = max(worst, max(((x-y).length for x, y in zip(va, vb)), default=0.0))
    assert worst < tolerance, worst
    return worst


def main():
    args = sys.argv[sys.argv.index('--') + 1:]
    if len(args) != 2:
        raise ValueError('Expected BEFORE.blend AFTER.blend')
    poses = ({}, {'Torso': (0, 0, 20)},
             {'UpperArm.L': (0, 0, -40), 'Forearm.L': (45, 0, 0)},
             {'UpperArm.R': (0, 0, -40), 'Forearm.R': (60, 0, 0)},
             {'Thigh.R': (45, 0, 0), 'Shin.R': (-20, 0, 0)})
    samples = []
    for path in args:
        bpy.ops.wm.open_mainfile(filepath=path)
        rig = bpy.data.objects[RIG]
        deform = {b.name for b in rig.data.bones if b.use_deform}
        for obj in bpy.data.objects:
            if obj.type != 'MESH':
                continue
            ids = {g.index for g in obj.vertex_groups if g.name in deform}
            for vertex in obj.data.vertices:
                total = sum(g.weight for g in vertex.groups if g.group in ids)
                assert abs(total - 1.0) < 1e-4, (obj.name, vertex.index, total)
        scene_samples = []
        for values in poses:
            pose(rig, values)
            scene_samples.append(geometry())
        samples.append(scene_samples)
    for i, (a, b) in enumerate(zip(*samples)):
        print('MAX_UNCHANGED', i, check_equal(a, b, exclude=('Cloak', 'Jacket Hands')))
        if i == 0:
            print('MAX_NEUTRAL', check_equal(a, b))
        if i in (1, 4):
            # Replacing stray hip influence changes the cuff during torso
            # movement. Bound and report that intentional difference.
            print('MAX_BODY_CHANGE', i, check_equal(a, b, tolerance=0.04))
    # Rigid wand attachment: after each pose, the wand's evaluated centroid in
    # the hand bone's local coordinates should remain unchanged.
    rig = bpy.data.objects[RIG]
    points = []
    for values in poses:
        pose(rig, values)
        vertices = geometry()['Wand'][0]
        centre = sum(vertices, Vector()) / len(vertices)
        hand_world = rig.matrix_world @ rig.pose.bones['Hand.L'].matrix
        points.append(hand_world.inverted() @ centre)
    drift = max((p - points[0]).length for p in points)
    assert drift < 1e-5, drift
    print('WAND_LOCAL_DRIFT', drift)
    print('SLEEVE_EDGE_CHECKS_OK')


if __name__ == '__main__':
    main()
