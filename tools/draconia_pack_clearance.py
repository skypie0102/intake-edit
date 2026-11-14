"""Add three full dance views of the updated rig to a format-1 preview pack.

python tools/draconia_pack_clearance.py INPUT.json FRAME_DIR OUTPUT.json
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
for view in ('front', 'side', 'rear'):
    frames = []
    for number in range(1, 193):
        with Image.open(frame_dir / view / f'{number:03}.png') as image:
            if image.size != (256, 256):
                raise ValueError(f'Wrong dimensions: {view} {number}')
            buffer = io.BytesIO()
            image.convert('RGBA').save(buffer, format='WEBP', quality=88, method=3)
            encoded = buffer.getvalue()
            with Image.open(io.BytesIO(encoded)) as check:
                check.load()
            frames.append(base64.b64encode(encoded).decode('ascii'))
    new.append({'id': 'clearance_' + view, 'label': 'Updated rig — ' + view,
                'frame_ms': 42, 'fps': 24, 'frames': frames})
old = [clip for clip in pack['clips'] if clip['id'] not in ('clearance_front', 'clearance_side', 'clearance_rear')]
for clip in old:
    if clip['id'] == 'dance_four_bar':
        clip['label'] = 'Earlier dance — front'
pack['clips'] = new + old
pack['revision'] = '2026-11-14-upper-body-clearance-1'
pack['description'] = 'Updated rig dance from front and rear; earlier studies are retained for comparison.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('Wrote', output, 'with', len(new), 'new 192-frame dance views and', len(pack['clips']), 'total clips')
