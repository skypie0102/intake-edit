"""Verify rendered loop geometry is unchanged by a follow-only rig update.

blender -b --python-exit-code 1 --python tools/draconia_validate_preview_equivalence.py -- RENDERED.blend UPDATED.blend [--sleeves-only]
This verifies evaluated mesh positions and topology in the sampled frames only.
"""
import bpy
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from draconia_validate_sleeve_edges import RIG, pose, geometry, check_equal


def loop_pose(kind, frame):
    t = 2 * math.pi * frame / 24
    if kind == 'wand':
        return {'Forearm.L': (45*math.sin(t), 0, 0), 'UpperArm.L': (0, 0, 8*math.sin(t))}
    if kind == 'other':
        return {'Forearm.R': (50*math.sin(t), 0, 0), 'UpperArm.R': (0, 0, -22*(1-math.cos(t))/2)}
    return {'Thigh.R': (50*math.sin(t), 0, 0), 'Shin.R': (-15*max(0, math.sin(t)), 0, 0)}


def main():
    paths = sys.argv[sys.argv.index('--') + 1:]
    sleeves_only = '--sleeves-only' in paths
    paths = [p for p in paths if p != '--sleeves-only']
    if len(paths) != 2:
        raise ValueError('Expected RENDERED.blend UPDATED.blend')
    samples = []
    for path in paths:
        bpy.ops.wm.open_mainfile(filepath=path)
        rig = bpy.data.objects[RIG]
        frames = []
        for kind in (('wand', 'other') if sleeves_only else ('wand', 'other', 'leg')):
            for frame in range(24):
                pose(rig, loop_pose(kind, frame))
                frames.append(geometry())
        samples.append(frames)
    maximum = max(check_equal(a, b) for a, b in zip(*samples))
    print('PREVIEW_SAMPLED_FRAMES', len(samples[0]), 'MAX_GEOMETRY_DIFFERENCE', maximum)
    print('PREVIEW_GEOMETRY_EQUIVALENT')


if __name__ == '__main__':
    main()
