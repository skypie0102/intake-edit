"""Add front and rear clearance/blink reach views to the preview pack.

python tools/draconia_pack_reach_clearance.py INPUT.json FRAME_DIR OUTPUT.json
"""
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image

source, frame_dir, output = map(Path, sys.argv[1:])
pack = json.loads(source.read_text())
if pack['format'] != 1:
    raise ValueError('Unsupported pack format')
new = []
for view in ('front', 'rear'):
    frames = []
    for frame in range(1, 49):
        with Image.open(frame_dir / view / f'{frame:03}.png') as image:
            if image.size != (256, 256):
                raise ValueError('Unexpected frame dimensions')
            encoded = io.BytesIO()
            image.convert('RGBA').save(encoded, format='WEBP', quality=88, method=3)
            frames.append(base64.b64encode(encoded.getvalue()).decode('ascii'))
    new.append({'id': 'reach_clearance_' + view,
                'label': 'Reach clearance — ' + view,
                'frame_ms': 42, 'fps': 24, 'frames': frames})
old = [clip for clip in pack['clips'] if clip['id'] not in ('reach_clearance_front', 'reach_clearance_rear')]
index = next((i + 1 for i, clip in enumerate(old) if clip['id'] == 'hair_reach_rear'), len(old))
pack['clips'] = old[:index] + new + old[index:]
pack['revision'] = '2026-11-14-reach-clearance-1'
pack['description'] = 'Neutral stance, refined hair clearance reach, blink study, and full dance.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('PACKED', len(pack['clips']), 'clips and', sum(len(clip['frames']) for clip in new), 'new frames')
