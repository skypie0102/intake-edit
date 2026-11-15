"""Keep current Draconia studies and remove superseded preview clips.

python tools/draconia_prune_preview.py INPUT.json OUTPUT.json
The frame data is retained verbatim for each selected clip.
"""
import json
import sys
from pathlib import Path

source, destination = map(Path, sys.argv[1:])
if source.resolve() == destination.resolve():
    raise ValueError('Output must be a separate file')
pack = json.loads(source.read_text())
if pack['format'] != 1 or pack['revision'] != '2026-11-14-chibi-hand-volume-1':
    raise ValueError('Expected the latest chibi hand preview')
labels = {
    'neutral_front': 'Neutral — front',
    'neutral_angle': 'Neutral — angle',
    'neutral_side': 'Neutral — side',
    'neutral_rear': 'Neutral — rear',
    'joined_hand_stance': 'Chibi hands — neutral',
    'joined_hand_front': 'Chibi hands — front',
    'joined_hand_angle': 'Chibi hands — angle',
    'wrist_clearance_front': 'Chibi wrist — front',
    'wrist_clearance_angle': 'Chibi wrist — angle',
    'joined_hand_dance': 'Chibi hands — dance close-up',
    'reach_clearance_front': 'Reach — front',
    'reach_clearance_rear': 'Reach — rear',
    'blink_study_front': 'Blink — front',
    'blink_study_angle': 'Blink — angle',
    'hair_clearance_dance_front': 'Dance — front',
    'hair_clearance_dance_side': 'Dance — side',
    'hair_clearance_dance_rear': 'Dance — rear',
    'jacket_clearance_closeup': 'Dance — clothing close-up',
}
clips = {clip['id']: clip for clip in pack['clips']}
if len(clips) != len(pack['clips']) or not labels.keys() <= clips.keys():
    raise ValueError('Missing or duplicate preview clips')
pack['clips'] = []
for clip_id, label in labels.items():
    clip = clips[clip_id]
    clip['label'] = label
    pack['clips'].append(clip)
pack['revision'] = '2026-11-15-current-studies-1'
pack['description'] = 'Current neutral, chibi hands, wrist, reach, blink, and full dance studies.'
destination.write_text(json.dumps(pack, ensure_ascii=True, separators=(',', ':')))
print('CURRENT_PREVIEWS', len(pack['clips']), 'clips,', sum(len(c['frames']) for c in pack['clips']), 'frames')
