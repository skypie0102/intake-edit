"""Add the CC0 dance to the existing Draconia preview pack.

python tools/draconia_pack_cc0_dance.py INPUT.json DANCE_FRAMES_DIR OUTPUT.json
The installed app accepts at most 240 frames and 12 clips. To keep existing
studies, the two identical-timing facial tests are resampled from 32 to 24 frames
and their frame duration adjusted from 100 to 133 ms. The dance has 20 frames at
50 ms, preserving its one-second duration. Output is validated before writing.
"""
import base64
import io
import json
import re
import sys
from pathlib import Path
from PIL import Image

args = sys.argv[1:]
if len(args) != 3:
    raise ValueError('Expected INPUT.json DANCE_FRAMES_DIR OUTPUT.json')
source, frame_dir, output = map(Path, args)
pack = json.loads(source.read_text())
if pack['format'] != 1:
    raise ValueError('Unsupported input pack')
for clip in pack['clips']:
    if clip['id'] in ('face_face', 'face_widget'):
        if len(clip['frames']) == 32 and clip['frame_ms'] == 100:
            old = clip['frames']
            clip['frames'] = [old[(i*32)//24] for i in range(24)]
            clip['frame_ms'] = 133
        elif len(clip['frames']) != 24 or clip['frame_ms'] != 133:
            raise ValueError(f"Unexpected facial clip timing: {clip['id']}")
frames = []
for index in range(20):
    image = Image.open(frame_dir / f'{index:02}.png').convert('RGBA')
    if not (1 <= image.width <= 256 and 1 <= image.height <= 256):
        raise ValueError(f'Invalid frame dimensions: {index}')
    buffer = io.BytesIO()
    image.save(buffer, format='WEBP', quality=88, method=3)
    frames.append(base64.b64encode(buffer.getvalue()).decode('ascii'))
pack['clips'] = [c for c in pack['clips'] if c['id'] != 'dance_cc0']
pack['clips'].append({'id': 'dance_cc0', 'label': 'CC0 dance test — front', 'frame_ms': 50, 'frames': frames})
pack['revision'] = '2026-11-14-cc0-dance-test-1'
pack['description'] = 'CC0 dance retarget and clothing studies; these are diagnostic motion tests.'
if not 1 <= len(pack['clips']) <= 12 or sum(len(c['frames']) for c in pack['clips']) > 240:
    raise ValueError('Preview pack exceeds the installed app limits')
ids = set()
for clip in pack['clips']:
    if not re.fullmatch('[a-z0-9_-]{1,40}', clip['id']) or clip['id'] in ids:
        raise ValueError('Invalid or duplicate clip ID')
    ids.add(clip['id'])
    if not 1 <= len(clip['label']) <= 80 or not 40 <= clip['frame_ms'] <= 1000:
        raise ValueError('Invalid clip label or timing')
    if not 1 <= len(clip['frames']) <= 96:
        raise ValueError('Invalid frame count')
    for frame in clip['frames']:
        if len(frame) > 180_000:
            raise ValueError('Frame too large')
        image = Image.open(io.BytesIO(base64.b64decode(frame, validate=True)))
        if image.format != 'WEBP' or not (1 <= image.width <= 256 and 1 <= image.height <= 256):
            raise ValueError('Unsupported frame image')
        image.load()
encoded = json.dumps(pack, ensure_ascii=True, separators=(',', ':'))
if len(encoded.encode('utf-8')) > 8_000_000:
    raise ValueError('Preview pack too large')
output.write_text(encoded)
print(f"{pack['revision']}: {len(pack['clips'])} clips, {sum(len(c['frames']) for c in pack['clips'])} frames, {len(encoded)} bytes")
