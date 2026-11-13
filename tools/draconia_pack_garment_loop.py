"""Add the rendered garment tests to an existing Draconia preview pack.

Run: python tools/draconia_pack_garment_loop.py INPUT_PACK.json FRAMES_DIR OUTPUT_PACK.json
"""
import base64
import io
import json
import re
import sys
from pathlib import Path
from PIL import Image

source, frames_dir, output = map(Path, sys.argv[1:])
pack = json.loads(source.read_text())
assert pack['format'] == 1
new = []
for view, label in (('front', 'Leg and garment follow — front'), ('angle', 'Leg and garment follow — angle')):
    frames = []
    for number in range(24):
        image = Image.open(frames_dir / view / f'{number:02}.png').convert('RGBA')
        assert 1 <= image.width <= 256 and 1 <= image.height <= 256
        out = io.BytesIO()
        image.save(out, format='WEBP', quality=88, method=3)
        frames.append(base64.b64encode(out.getvalue()).decode('ascii'))
    new.append({'id': 'garment_' + view, 'label': label, 'frame_ms': 125, 'frames': frames})
pack['clips'] = [c for c in pack['clips'] if c['id'] not in {v['id'] for v in new}] + new
pack['revision'] = '2026-11-13-garment-follow-2'
pack['description'] = 'Experimental automatic cloak and skirt follow; larger motions and sleeve deformation need further work.'
assert 1 <= len(pack['clips']) <= 12
assert sum(len(c['frames']) for c in pack['clips']) <= 240
ids = set()
for clip in pack['clips']:
    assert re.fullmatch('[a-z0-9_-]{1,40}', clip['id']) and clip['id'] not in ids
    ids.add(clip['id'])
    assert 1 <= len(clip['label']) <= 80 and 40 <= clip['frame_ms'] <= 1000
    assert 1 <= len(clip['frames']) <= 96
    for frame in clip['frames']:
        assert len(frame) <= 180_000
        image = Image.open(io.BytesIO(base64.b64decode(frame, validate=True)))
        assert image.format == 'WEBP' and 1 <= image.width <= 256 and 1 <= image.height <= 256
        image.load()
encoded = json.dumps(pack, ensure_ascii=True, separators=(',', ':'))
assert len(encoded.encode('utf-8')) <= 8_000_000
output.write_text(encoded)
print(f"{pack['revision']}: {len(pack['clips'])} clips, {sum(len(c['frames']) for c in pack['clips'])} frames, {len(encoded)} bytes")
