"""Add the complete experimental hand study without changing the default rig.

python tools/draconia_pack_hand_study.py INPUT.json FRAME_DIR STANCE.png OUTPUT.json
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


new = [{'id': 'hand_study_stance', 'label': 'Experimental hands — neutral',
        'frame_ms': 1000, 'frames': [encode(stance, resize=True)]}]
for view in ('front', 'angle'):
    frames = [encode(frame_dir / view / f'{frame:03}.png') for frame in range(1, 97)]
    new.append({'id': 'hand_study_' + view, 'label': 'Experimental hands — ' + view,
                'frame_ms': 42, 'fps': 24, 'frames': frames})
ids = {clip['id'] for clip in new}
old = [clip for clip in pack['clips'] if clip['id'] not in ids]
index = next((i + 1 for i, clip in enumerate(old) if clip['id'] == 'neutral_rear'), len(old))
pack['clips'] = old[:index] + new + old[index:]
pack['revision'] = '2026-11-14-hand-study-1'
pack['description'] = 'Neutral stance, experimental articulated hands, clothing fix, and full dance.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('PACKED', len(pack['clips']), 'clips and', sum(len(clip['frames']) for clip in new), 'new frames')
