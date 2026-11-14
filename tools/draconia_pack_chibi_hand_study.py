"""Replace the previous hand/wrist studies with chibi hand previews.

python tools/draconia_pack_chibi_hand_study.py INPUT.json HAND_DIR WRIST_DIR STANCE.png OUTPUT.json
"""
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image

source, hand_dir, wrist_dir, stance, output = map(Path, sys.argv[1:])
pack = json.loads(source.read_text())
if pack['format'] != 1:
    raise ValueError('Unsupported preview pack')


def encode(path, resize=False):
    with Image.open(path) as image:
        if resize:
            image = image.resize((256, 256), Image.Resampling.LANCZOS)
        if image.size != (256, 256):
            raise ValueError(f'Unexpected dimensions: {path}')
        buffer = io.BytesIO()
        image.convert('RGBA').save(buffer, format='WEBP', quality=88, method=3)
        return base64.b64encode(buffer.getvalue()).decode('ascii')


replacements = {'joined_hand_stance': ('Chibi hands — neutral', [encode(stance, True)], 1000)}
for view in ('front', 'angle', 'dance'):
    count = 192 if view == 'dance' else 96
    label = 'Chibi hands — dance close-up' if view == 'dance' else 'Chibi hands — ' + view
    replacements['joined_hand_' + view] = (label, [encode(hand_dir / view / f'{i:03}.png') for i in range(1, count + 1)], 42)
for view in ('front', 'angle'):
    replacements['wrist_clearance_' + view] = ('Chibi wrist — ' + view, [encode(wrist_dir / view / f'{i:03}.png') for i in range(1, 97)], 42)
found = set()
for clip in pack['clips']:
    if clip['id'] in replacements:
        label, frames, interval = replacements[clip['id']]
        clip.update(label=label, frames=frames, frame_ms=interval)
        if interval == 42:
            clip['fps'] = 24
        found.add(clip['id'])
    elif clip['id'].startswith('hand_study_'):
        clip['label'] = clip['label'].replace('Experimental hands', 'Earlier separate fingers')
if found != replacements.keys():
    raise ValueError(f'Missing clips: {replacements.keys() - found}')
pack['revision'] = '2026-11-14-chibi-hands-1'
pack['description'] = 'Neutral stance, chibi hand and wrist controls, blink, and full dance.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('PACKED', len(pack['clips']), 'clips; replaced', len(found), 'hand and wrist studies')
