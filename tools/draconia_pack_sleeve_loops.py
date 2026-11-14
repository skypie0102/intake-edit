"""Update sleeve and leg studies in a Draconia preview pack.

python tools/draconia_pack_sleeve_loops.py INPUT.json SLEEVE_FRAMES LEG_FRAMES CROSS_FRAMES OUTPUT.json
Each frames directory must be rendered from the same current Blender scene.
"""
import base64
import io
import json
import re
import sys
from pathlib import Path
from PIL import Image

args = sys.argv[1:]
if len(args) != 5:
    raise ValueError('Expected INPUT.json SLEEVE_FRAMES LEG_FRAMES CROSS_FRAMES OUTPUT.json')
source, sleeves, legs, cross, output = map(Path, args)
pack = json.loads(source.read_text())
assert pack['format'] == 1
replacements = {}
for clip_id, label, directory in (
    ('wand_front', 'Wand sleeve — front', sleeves / 'wand_front'),
    ('wand_angle', 'Wand sleeve — angle', sleeves / 'wand_angle'),
    ('other_front', 'Other sleeve — front', sleeves / 'other_front'),
    ('other_angle', 'Other sleeve — angle', sleeves / 'other_angle'),
    ('garment_front', 'Leg and garment follow — front', legs / 'front'),
    ('garment_angle', 'Leg and garment follow — angle', legs / 'angle'),
    ('cross_step', 'Mixed leg and clothing test — angle', cross),
):
    frames = []
    for number in range(24):
        image = Image.open(directory / f'{number:02}.png').convert('RGBA')
        if not (1 <= image.width <= 256 and 1 <= image.height <= 256):
            raise ValueError(f'Invalid frame dimensions: {directory}/{number:02}.png')
        out = io.BytesIO()
        image.save(out, format='WEBP', quality=88, method=3)
        frames.append(base64.b64encode(out.getvalue()).decode('ascii'))
    replacements[clip_id] = {'id': clip_id, 'label': label, 'frame_ms': 125, 'frames': frames}
old_ids = {c['id'] for c in pack['clips']}
if not (replacements.keys() - {'cross_step'}) <= old_ids:
    raise ValueError('Input pack is missing one or more expected study clips')
pack['clips'] = [replacements.get(c['id'], c) for c in pack['clips']]
if 'cross_step' not in old_ids:
    pack['clips'].append(replacements['cross_step'])
pack['revision'] = '2026-11-14-clothing-follow-1'
pack['description'] = 'Refined rear cloak and independently following skirt panels, with sleeve and mixed-leg tests.'
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
