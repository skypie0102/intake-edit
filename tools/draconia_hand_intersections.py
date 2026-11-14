"""Helpers for sampled triangle self-intersection diagnostics."""
import bpy
from mathutils.bvhtree import BVHTree
from mathutils.geometry import intersect_ray_tri


def intersection_segments(obj):
    evaluated = obj.evaluated_get(bpy.context.evaluated_depsgraph_get())
    mesh = evaluated.to_mesh()
    mesh.calc_loop_triangles()
    vertices = [vertex.co.copy() for vertex in mesh.vertices]
    triangles = [tuple(triangle.vertices) for triangle in mesh.loop_triangles]
    tree = BVHTree.FromPolygons(vertices, triangles, all_triangles=True, epsilon=0)
    pairs = [(a, b) for a, b in tree.overlap(tree)
             if a < b and not set(triangles[a]) & set(triangles[b])]
    lengths = []
    for first, second in pairs:
        triangle_a = [vertices[i] for i in triangles[first]]
        triangle_b = [vertices[i] for i in triangles[second]]
        hits = []
        for edges, face in ((triangle_a, triangle_b), (triangle_b, triangle_a)):
            for i in range(3):
                start = edges[i]
                direction = edges[(i + 1) % 3] - start
                if direction.length_squared < 1e-20:
                    continue
                point = intersect_ray_tri(*face, direction, start, True)
                if point is None:
                    continue
                distance = (point - start).dot(direction) / direction.length_squared
                if -1e-6 <= distance <= 1 + 1e-6 and not any((point - old).length < 1e-7 for old in hits):
                    hits.append(point)
        if len(hits) >= 2:
            lengths.append(max((a - b).length for a in hits for b in hits))
    evaluated.to_mesh_clear()
    return lengths
