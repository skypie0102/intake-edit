"""Add the full 8-second CC0 dance without trimming any other clips.

python tools/draconia_pack_four_bar.py INPUT.json FRAME_DIR OUTPUT.json
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
frames = []
for number in range(1, 193):
    with Image.open(frame_dir / f'{number:03}.png') as image:
        if image.size != (256, 256):
            raise ValueError(f'Wrong dimensions for frame {number}')
        buffer = io.BytesIO()
        image.convert('RGBA').save(buffer, format='WEBP', quality=88, method=3)
        encoded = buffer.getvalue()
        with Image.open(io.BytesIO(encoded)) as check:
            check.load()
        frames.append(base64.b64encode(encoded).decode('ascii'))
pack['clips'] = [clip for clip in pack['clips'] if clip['id'] not in ('dance_cc0', 'dance_four_bar')]
pack['clips'].insert(0, {'id': 'dance_four_bar', 'label': 'Full dance — front', 'frame_ms': 42, 'fps': 24, 'frames': frames})
pack['revision'] = '2026-11-14-full-four-bar-dance'
pack['description'] = 'Full eight-second CC0 dance retarget and clothing motion studies.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('Wrote', output, 'with', len(frames), 'dance frames and', len(pack['clips']), 'clips')
