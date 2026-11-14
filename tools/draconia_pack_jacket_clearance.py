"""Replace the three full dance views and add a full clothing close-up.

python tools/draconia_pack_jacket_clearance.py INPUT.json FRAME_DIR OUTPUT.json
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
clips = []
for view in ('front', 'side', 'rear', 'closeup'):
    frames = []
    for frame in range(1, 193):
        with Image.open(frame_dir / view / f'{frame:03}.png') as image:
            if image.size != (256, 256):
                raise ValueError(f'Unexpected dimensions in {view} frame {frame}')
            data = io.BytesIO()
            image.convert('RGBA').save(data, format='WEBP', quality=88, method=3)
            frames.append(base64.b64encode(data.getvalue()).decode('ascii'))
    clip_id = 'jacket_clearance_closeup' if view == 'closeup' else 'hair_clearance_dance_' + view
    label = 'Clothing fix — close-up' if view == 'closeup' else 'Clothing fix — ' + view
    clips.append({'id': clip_id, 'label': label, 'frame_ms': 42, 'fps': 24, 'frames': frames})
ids = {clip['id'] for clip in clips}
old = [clip for clip in pack['clips'] if clip['id'] not in ids]
index = next((i for i, clip in enumerate(old) if clip['id'] == 'clearance_front'), len(old))
pack['clips'] = old[:index] + clips + old[index:]
pack['revision'] = '2026-11-14-jacket-clearance-1'
pack['description'] = 'Clothing clearance fix, full dance, neutral stance and earlier studies.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('PACKED', len(pack['clips']), 'clips and', sum(len(clip['frames']) for clip in clips), 'dance frames')
