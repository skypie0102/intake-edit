"""Add full joined-hand motion and dance studies to the preview pack.

python tools/draconia_pack_joined_hand_study.py INPUT.json FRAME_DIR STANCE.png OUTPUT.json
"""
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image

source, frame_dir, stance, output = map(Path, sys.argv[1:])
pack = json.loads(source.read_text())
if pack['format'] != 1:
    raise ValueError('Unsupported preview pack')


def encode(path, resize=False):
    with Image.open(path) as image:
        if resize:
            image = image.resize((256, 256), Image.Resampling.LANCZOS)
        if image.size != (256, 256):
            raise ValueError(f'Unexpected dimensions: {path}')
        buffer = io.BytesIO()
        image.convert('RGBA').save(buffer, format='WEBP', quality=88, method=3)
        return base64.b64encode(buffer.getvalue()).decode('ascii')


new = [{'id': 'joined_hand_stance', 'label': 'Joined hands — neutral',
        'frame_ms': 1000, 'frames': [encode(stance, resize=True)]}]
for view in ('front', 'angle', 'dance'):
    count = 192 if view == 'dance' else 96
    label = 'Joined hands — dance close-up' if view == 'dance' else 'Joined hands — ' + view
    frames = [encode(frame_dir / view / f'{frame:03}.png') for frame in range(1, count + 1)]
    new.append({'id': 'joined_hand_' + view, 'label': label,
                'frame_ms': 42, 'fps': 24, 'frames': frames})
ids = {clip['id'] for clip in new}
old = [clip for clip in pack['clips'] if clip['id'] not in ids]
index = next((i + 1 for i, clip in enumerate(old) if clip['id'] == 'hand_study_angle'), len(old))
pack['clips'] = old[:index] + new + old[index:]
pack['revision'] = '2026-11-14-joined-hand-study-1'
pack['description'] = 'Neutral stance, joined-hand study, clothing fix, and full dance.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('PACKED', len(pack['clips']), 'clips and', sum(len(clip['frames']) for clip in new), 'new frames')
