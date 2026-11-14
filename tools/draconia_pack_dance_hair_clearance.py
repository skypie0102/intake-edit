"""Add all three full eight-second dance views to the preview pack.

python tools/draconia_pack_dance_hair_clearance.py INPUT.json FRAME_DIR OUTPUT.json
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
for view in ('front', 'side', 'rear'):
    frames = []
    for frame in range(1, 193):
        with Image.open(frame_dir / view / f'{frame:03}.png') as image:
            if image.size != (256, 256):
                raise ValueError(f'Unexpected dimensions in {view} frame {frame}')
            buffer = io.BytesIO()
            image.convert('RGBA').save(buffer, format='WEBP', quality=88, method=3)
            frames.append(base64.b64encode(buffer.getvalue()).decode('ascii'))
    new.append({'id': 'hair_clearance_dance_' + view,
                'label': 'Refined dance — ' + view,
                'frame_ms': 42, 'fps': 24, 'frames': frames})
ids = {clip['id'] for clip in new}
old = [clip for clip in pack['clips'] if clip['id'] not in ids]
index = next((i for i, clip in enumerate(old) if clip['id'] == 'clearance_front'), len(old))
pack['clips'] = old[:index] + new + old[index:]
pack['revision'] = '2026-11-14-dance-hair-clearance-1'
pack['description'] = 'Neutral stance, full dance with refined hair clearance and blinking, and earlier studies.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('PACKED', len(pack['clips']), 'clips and', sum(len(clip['frames']) for clip in new), 'new frames')
