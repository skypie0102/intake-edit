"""Add the experimental blink study to the existing preview pack.

python tools/draconia_pack_blink_study.py INPUT.json FRAME_DIR OUTPUT.json
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
    raise ValueError('Unsupported pack')
new = []
for view in ('front', 'angle'):
    frames = []
    for frame in range(1, 49):
        with Image.open(frame_dir / view / f'{frame:03}.png') as image:
            if image.size != (256, 256):
                raise ValueError('Unexpected frame dimensions')
            data = io.BytesIO()
            image.convert('RGBA').save(data, format='WEBP', quality=88, method=3)
            frames.append(base64.b64encode(data.getvalue()).decode('ascii'))
    new.append({'id': 'blink_study_'+view, 'label': 'Experimental blink — '+view,
                'frame_ms': 42, 'fps': 24, 'frames': frames})
old = [c for c in pack['clips'] if c['id'] not in ('blink_study_front', 'blink_study_angle')]
index = next((i for i, c in enumerate(old) if c['id'] == 'clearance_front'), len(old))
pack['clips'] = old[:index]+new+old[index:]
pack['revision'] = '2026-11-14-blink-study-3'
pack['description'] = 'Neutral stance, reach and hair controls, fuller-lid blink study, and full dance.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('PACKED', len(pack['clips']), 'clips and', len(new)*48, 'new frames')
