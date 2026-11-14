"""Update the complete chibi hand, wrist, and dance motion previews.

python tools/draconia_pack_chibi_volume.py INPUT.json HAND_DIR WRIST_DIR OUTPUT.json
"""
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image

source, hand_dir, wrist_dir, output = map(Path, sys.argv[1:])
pack = json.loads(source.read_text())
if pack['format'] != 1 or pack['revision'] != '2026-11-14-chibi-hands-1':
    raise ValueError('Expected the chibi hand preview pack')
replacements = {}
for view in ('front', 'angle', 'dance'):
    replacements['joined_hand_' + view] = (hand_dir / view, 192 if view == 'dance' else 96)
for view in ('front', 'angle'):
    replacements['wrist_clearance_' + view] = (wrist_dir / view, 96)
found = set()
for clip in pack['clips']:
    if clip['id'] not in replacements:
        continue
    directory, count = replacements[clip['id']]
    frames = []
    for frame in range(1, count + 1):
        with Image.open(directory / f'{frame:03}.png') as image:
            if image.size != (256, 256):
                raise ValueError(f'Unexpected dimensions: {directory} frame {frame}')
            buf = io.BytesIO()
            image.convert('RGBA').save(buf, format='WEBP', quality=88, method=3)
            frames.append(base64.b64encode(buf.getvalue()).decode('ascii'))
    clip.update(frames=frames, frame_ms=42, fps=24)
    found.add(clip['id'])
if found != replacements.keys():
    raise ValueError(f'Missing clips: {replacements.keys() - found}')
pack['revision'] = '2026-11-14-chibi-hand-volume-1'
pack['description'] = 'Neutral stance, refined chibi hand and wrist controls, blink, and full dance.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('PACKED', len(found), 'updated clips and', sum(count for _, count in replacements.values()), 'frames')
