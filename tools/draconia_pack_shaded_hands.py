"""Refresh current rig previews with the softly shaded chibi hands.

python tools/draconia_pack_shaded_hands.py INPUT.json NEUTRAL_DIR HAND_DIR WRIST_DIR REACH_DIR DANCE_DIR CLOSEUP_DIR OUTPUT.json
"""
import base64
import io
import json
import sys
from pathlib import Path

source, neutral, hands, wrist, reach, dance, closeup, output = map(Path, sys.argv[1:])
pack = json.loads(source.read_text())
if pack['format'] != 1 or pack['revision'] != '2026-11-15-integrated-chibi-1':
    raise ValueError('Expected the integrated chibi preview pack')

from PIL import Image


def encode(path, resize=False):
    with Image.open(path) as image:
        if resize:
            image = image.resize((256, 256), Image.Resampling.LANCZOS)
        if image.size != (256, 256):
            raise ValueError(f'Unexpected dimensions: {path}')
        buffer = io.BytesIO()
        image.convert('RGBA').save(buffer, format='WEBP', quality=88, method=3)
        return base64.b64encode(buffer.getvalue()).decode('ascii')


replacements = {}
for view in ('front', 'angle', 'side', 'rear'):
    replacements['neutral_' + view] = [encode(neutral / f'{view}.png', True)]
for view in ('front', 'angle'):
    replacements['joined_hand_' + view] = [encode(hands / view / f'{i:03}.png') for i in range(1, 97)]
    replacements['wrist_clearance_' + view] = [encode(wrist / view / f'{i:03}.png') for i in range(1, 97)]
for view in ('front', 'rear'):
    replacements['reach_clearance_' + view] = [encode(reach / view / f'{i:03}.png') for i in range(1, 49)]
for view in ('front', 'side', 'rear'):
    replacements['hair_clearance_dance_' + view] = [encode(dance / view / f'{i:03}.png') for i in range(1, 193)]
replacements['jacket_clearance_closeup'] = [encode(closeup / f'{i:03}.png') for i in range(1, 193)]
found = set()
for clip in pack['clips']:
    if clip['id'] in replacements:
        if len(clip['frames']) != len(replacements[clip['id']]):
            raise ValueError(f'Frame count changed for {clip["id"]}')
        clip['frames'] = replacements[clip['id']]
        found.add(clip['id'])
if found != replacements.keys():
    raise ValueError(f'Missing clips: {replacements.keys() - found}')
pack['revision'] = '2026-11-15-soft-hand-shading-1'
pack['description'] = 'Current chibi rig with softly shaded hands, reach, blink, and full dance studies.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('SHADED_PREVIEWS', len(pack['clips']), 'clips;', len(found), 'views updated')
