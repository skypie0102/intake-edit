"""Use the current chibi rig in the neutral, reach and full dance previews.

python tools/draconia_pack_integrated_chibi.py INPUT.json NEUTRAL_DIR DANCE_DIR REACH_DIR OUTPUT.json
The existing current-hand close-up replaces the older clothing close-up, and
duplicate chibi neutral/dance clips are removed from the active list.
"""
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image

source, neutral_dir, dance_dir, reach_dir, output = map(Path, sys.argv[1:])
pack = json.loads(source.read_text())
if pack['format'] != 1 or pack['revision'] != '2026-11-15-current-hands-1':
    raise ValueError('Expected the current chibi hand pack')
by_id = {clip['id']: clip for clip in pack['clips']}
if len(by_id) != len(pack['clips']) or 'joined_hand_dance' not in by_id:
    raise ValueError('Missing or duplicate source clips')


def encode(path, resize=False):
    with Image.open(path) as image:
        if resize:
            image = image.resize((256, 256), Image.Resampling.LANCZOS)
        if image.size != (256, 256):
            raise ValueError(f'Unexpected dimensions: {path}')
        buffer = io.BytesIO()
        image.convert('RGBA').save(buffer, format='WEBP', quality=88, method=3)
        return base64.b64encode(buffer.getvalue()).decode('ascii')


replacements = {}
for view in ('front', 'angle', 'side', 'rear'):
    replacements['neutral_' + view] = [encode(neutral_dir / f'{view}.png', True)]
for view in ('front', 'side', 'rear'):
    replacements['hair_clearance_dance_' + view] = [encode(dance_dir / view / f'{i:03}.png') for i in range(1, 193)]
for view in ('front', 'rear'):
    replacements['reach_clearance_' + view] = [encode(reach_dir / view / f'{i:03}.png') for i in range(1, 49)]
replacements['jacket_clearance_closeup'] = by_id['joined_hand_dance']['frames']
found = set()
pack['clips'] = [clip for clip in pack['clips'] if clip['id'] not in ('joined_hand_stance', 'joined_hand_dance')]
for clip in pack['clips']:
    if clip['id'] in replacements:
        if len(clip['frames']) != len(replacements[clip['id']]):
            raise ValueError(f'Frame count changed for {clip["id"]}')
        clip['frames'] = replacements[clip['id']]
        found.add(clip['id'])
    if clip['id'] == 'jacket_clearance_closeup':
        clip['label'] = 'Dance — clothing and hands close-up'
if found != replacements.keys():
    raise ValueError(f'Missing clips: {replacements.keys() - found}')
pack['revision'] = '2026-11-15-integrated-chibi-1'
pack['description'] = 'Current chibi rig: neutral, hand and wrist, reach, blink, and full dance studies.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('INTEGRATED_PREVIEWS', len(pack['clips']), 'clips,', sum(len(c['frames']) for c in pack['clips']), 'frames')
