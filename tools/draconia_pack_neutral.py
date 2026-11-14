"""Put the neutral stance and wand-free dance first in the preview pack.

python tools/draconia_pack_neutral.py INPUT.json NEUTRAL_DIR DANCE_DIR OUTPUT.json
The previous studies are retained and marked as earlier references.
"""
import base64
import io
import json
import sys
from pathlib import Path
from PIL import Image

source, neutral_dir, dance_dir, output = map(Path, sys.argv[1:])
pack = json.loads(source.read_text())
if pack['format'] != 1:
    raise ValueError('Unsupported pack')


def encoded_frame(path, resize=False):
    with Image.open(path) as image:
        image = image.convert('RGBA')
        if resize:
            image = image.resize((256, 256), Image.Resampling.LANCZOS)
        if image.size != (256, 256):
            raise ValueError(f'Unexpected frame dimensions: {path}')
        data = io.BytesIO()
        image.save(data, format='WEBP', quality=88, method=3)
        content = data.getvalue()
        with Image.open(io.BytesIO(content)) as check:
            check.load()
        return base64.b64encode(content).decode('ascii')

new = []
for view in ('front', 'angle', 'side', 'rear'):
    new.append({'id': 'neutral_' + view, 'label': 'Neutral — ' + view,
                'frame_ms': 125, 'frames': [encoded_frame(neutral_dir / f'{view}.png', resize=True)]})
for view in ('front', 'side', 'rear'):
    frames = [encoded_frame(dance_dir / view / f'{number:03}.png') for number in range(1, 193)]
    new.append({'id': 'clearance_' + view, 'label': 'Dance without wand — ' + view,
                'frame_ms': 42, 'fps': 24, 'frames': frames})
replace = {c['id'] for c in new}
old = [clip for clip in pack['clips'] if clip['id'] not in replace]
for clip in old:
    if not clip['label'].startswith('Earlier'):
        clip['label'] = 'Earlier — ' + clip['label']
    if len(clip['label']) > 80:
        raise ValueError(f"Label too long: {clip['id']}")
pack['clips'] = new + old
pack['revision'] = '2026-11-14-neutral-rest-1'
pack['description'] = 'Neutral arms-down stance and full dance without the wand; earlier studies are preserved.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('Wrote', output, len(new), 'new views and', len(pack['clips']), 'total clips')
