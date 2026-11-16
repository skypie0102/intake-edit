"""Replace the four complete dance views with the refined chibi hair motion.

python tools/draconia_pack_chibi_dance_hair2.py INPUT.json DANCE_DIR CLOSEUP_DIR OUTPUT.json
"""
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image

source, dance, closeup, output = map(Path, sys.argv[1:])
pack = json.loads(source.read_text())
if pack['format'] != 1 or pack['revision'] != '2026-11-15-soft-hand-shading-1':
    raise ValueError('Expected the current softly shaded chibi pack')


def encode(path):
    with Image.open(path) as image:
        if image.size != (256, 256):
            raise ValueError(f'Unexpected dimensions: {path}')
        buffer = io.BytesIO()
        image.convert('RGBA').save(buffer, format='WEBP', quality=88, method=3)
        return base64.b64encode(buffer.getvalue()).decode('ascii')


directories = {f'hair_clearance_dance_{view}': dance / view for view in ('front', 'side', 'rear')}
directories['jacket_clearance_closeup'] = closeup
found = set()
for clip in pack['clips']:
    if clip['id'] not in directories:
        continue
    if len(clip['frames']) != 192 or clip.get('fps') != 24:
        raise ValueError(f'Expected a complete 192-frame dance: {clip["id"]}')
    clip['frames'] = [encode(directories[clip['id']] / f'{frame:03}.png') for frame in range(1, 193)]
    found.add(clip['id'])
if found != directories.keys():
    raise ValueError(f'Missing views: {directories.keys() - found}')
pack['revision'] = '2026-11-16-dance-hair-clearance-2'
pack['description'] = 'Chibi rig with refined dance hair, neutral, hand, wrist, reach, and blink studies.'
output.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('REFINED_DANCE_VIEWS', len(found), 'frames', sum(len(clip['frames']) for clip in pack['clips'] if clip['id'] in found))
