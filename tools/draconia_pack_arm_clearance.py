"""Update joined-hand previews and add the complete wrist-clearance test.

python tools/draconia_pack_arm_clearance.py INPUT.json FINGER_DIR WRIST_DIR STANCE.png OUTPUT.json
"""
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image

source, finger_dir, wrist_dir, stance, output = map(Path, sys.argv[1:])
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


replacements = {'joined_hand_stance': {'id': 'joined_hand_stance',
                                        'label': 'Joined hands — neutral',
                                        'frame_ms': 1000,
                                        'frames': [encode(stance, resize=True)]}}
for view in ('front', 'angle'):
    frames = [encode(finger_dir / view / f'{frame:03}.png') for frame in range(1, 97)]
    replacements['joined_hand_' + view] = {'id': 'joined_hand_' + view,
                                           'label': 'Joined hands — ' + view,
                                           'frame_ms': 42, 'fps': 24, 'frames': frames}
wrist = []
for view in ('front', 'angle'):
    frames = [encode(wrist_dir / view / f'{frame:03}.png') for frame in range(1, 97)]
    wrist.append({'id': 'wrist_clearance_' + view, 'label': 'Wrist clearance — ' + view,
                  'frame_ms': 42, 'fps': 24, 'frames': frames})
wrist_ids = {clip['id'] for clip in wrist}
old = [clip for clip in pack['clips'] if clip['id'] not in wrist_ids]
if not set(replacements).issubset({clip['id'] for clip in old}):
    raise ValueError('Missing existing joined-hand views')
updated = [replacements.get(clip['id'], clip) for clip in old]
index = next(i + 1 for i, clip in enumerate(updated) if clip['id'] == 'joined_hand_angle')
pack['clips'] = updated[:index] + wrist + updated[index:]
pack['revision'] = '2026-11-14-neutral-hand-clearance-1'
pack['description'] = 'Neutral stance, joined hands with neutral clearance, wrist test, and full dance.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('PACKED', len(pack['clips']), 'clips and', sum(len(clip['frames']) for clip in wrist), 'new wrist frames')
