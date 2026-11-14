"""Use volume-preserving skinning for the chibi hands' wrist bridges.

blender -b CHIBI.blend --python-exit-code 1 --python tools/draconia_preserve_chibi_hand_volume.py -- OUTPUT.blend
The change is limited to the alternate hand meshes; animation is unchanged.
"""
import sys
from pathlib import Path

import bpy

args = sys.argv[sys.argv.index('--') + 1:]
if len(args) != 1:
    raise ValueError('Expected OUTPUT.blend')
output = Path(args[0]).resolve()
if output.exists() or output == Path(bpy.data.filepath).resolve():
    raise ValueError('Output must be a new file')
rig = bpy.data.objects['Draconia Body Rig - experimental']
for side in ('L', 'R'):
    obj = bpy.data.objects['Articulated hand study ' + side]
    modifiers = [m for m in obj.modifiers if m.type == 'ARMATURE' and m.object == rig]
    if len(modifiers) != 1 or modifiers[0].use_deform_preserve_volume:
        raise ValueError('Expected one ordinary armature modifier on each hand')
    modifiers[0].use_deform_preserve_volume = True
bpy.ops.wm.save_as_mainfile(filepath=str(output))
print('CHIBI_HAND_VOLUME_PRESERVATION_ENABLED', flush=True)
