"""Verify the joined-hand preview and its individual pulses."""
import math

import bpy

scene = bpy.context.scene
rig = bpy.data.objects['Draconia Body Rig - experimental']
assert (scene.frame_start, scene.frame_end, scene.render.fps) == (1, 96, 24)
assert bpy.data.objects['Wand'].hide_render and bpy.data.objects['Arms'].hide_render
for side in ('L', 'R'):
    for frame, label in ((13, 'Index'), (26, 'Middle'), (39, 'Ring'), (52, 'Pinky'), (65, 'Thumb')):
        scene.frame_set(frame)
        bpy.context.view_layer.update()
        angle = rig.pose.bones[f'{label}.01.{side}'].rotation_euler.x
        expected = -35 if label == 'Thumb' else 48
        assert abs(angle - math.radians(expected)) < 1e-4, (frame, label, angle)
        for other in ('Index', 'Middle', 'Ring', 'Pinky', 'Thumb'):
            if other != label:
                assert abs(rig.pose.bones[f'{other}.01.{side}'].rotation_euler.x) < 1e-5, (frame, other)
    for frame in (1, 96):
        scene.frame_set(frame)
        for label in ('Index', 'Middle', 'Ring', 'Pinky', 'Thumb'):
            for segment in (1, 2):
                assert abs(rig.pose.bones[f'{label}.{segment:02d}.{side}'].rotation_euler.x) < 1e-5
print('JOINED_HAND_MOTION_CHECKS_OK', flush=True)
