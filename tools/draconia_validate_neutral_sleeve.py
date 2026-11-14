"""Check that sleeve reweighting preserves the arms-down rest shape.

blender -b --python-exit-code 1 --python tools/draconia_validate_neutral_sleeve.py -- BEFORE.blend AFTER.blend
"""
import bpy
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from draconia_validate_sleeve_edges import RIG, pose, geometry, check_equal

paths = sys.argv[sys.argv.index('--')+1:]
if len(paths) != 2:
    raise ValueError('Expected BEFORE.blend AFTER.blend')
samples = []
for path in paths:
    bpy.ops.wm.open_mainfile(filepath=path)
    rig = bpy.data.objects[RIG]
    pose(rig, {})
    samples.append(geometry())
print('NEUTRAL_SHAPE_MAX_DELTA', check_equal(samples[0], samples[1]), flush=True)
print('NEUTRAL_SLEEVE_CHECKS_OK', flush=True)
