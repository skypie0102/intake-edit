"""Update the chibi hand views in the curated current preview pack.

python tools/draconia_pack_smoothed_hands.py INPUT.json HAND_DIR WRIST_DIR STANCE.png OUTPUT.json
"""
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image

source, hand_dir, wrist_dir, stance, output = map(Path, sys.argv[1:])
pack = json.loads(source.read_text())
if pack['format'] != 1 or pack['revision'] != '2026-11-15-current-studies-1':
    raise ValueError('Expected the curated current studies pack')


def encode(path, resize=False):
    with Image.open(path) as image:
        if resize:
            image = image.resize((256, 256), Image.Resampling.LANCZOS)
        if image.size != (256, 256):
            raise ValueError(f'Unexpected dimensions: {path}')
        buffer = io.BytesIO()
        image.convert('RGBA').save(buffer, format='WEBP', quality=88, method=3)
        return base64.b64encode(buffer.getvalue()).decode('ascii')


replacements = {'joined_hand_stance': [encode(stance, True)]}
for view in ('front', 'angle', 'dance'):
    count = 192 if view == 'dance' else 96
    replacements['joined_hand_' + view] = [encode(hand_dir / view / f'{n:03}.png') for n in range(1, count + 1)]
for view in ('front', 'angle'):
    replacements['wrist_clearance_' + view] = [encode(wrist_dir / view / f'{n:03}.png') for n in range(1, 97)]
found = set()
for clip in pack['clips']:
    if clip['id'] in replacements:
        clip['frames'] = replacements[clip['id']]
        found.add(clip['id'])
if found != replacements.keys():
    raise ValueError(f'Missing clips: {replacements.keys() - found}')
pack['revision'] = '2026-11-15-current-hands-1'
pack['description'] = 'Current neutral, refined chibi hands, wrist, reach, blink, and full dance studies.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('PACKED', len(pack['clips']), 'clips;', len(found), 'hand and wrist views updated')
