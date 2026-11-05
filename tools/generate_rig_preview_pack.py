"""Build the downloadable preview pack from the editable vector rig.

Publish the resulting rig-preview/animation-pack.json on the preview branch.
The preview app accepts this data format without an APK update.
"""
import json
from pathlib import Path
import generate_companion_rig as rig

ROOT = Path(__file__).resolve().parents[1]
FRAME_COUNT = 48


def interpolate(values, t):
    x = t * len(values)
    i = int(x) % len(values)
    return values[i] + (values[(i + 1) % len(values)] - values[i]) * (x - int(x))


def generate():
    moods = {}
    for mood in rig.MOODS:
        frames = []
        for frame in range(FRAME_COUNT):
            t = frame / FRAME_COUNT
            phase = t if mood == 'waiting' or t < .94 else 0
            amount = interpolate(rig.POSES if mood == 'waiting' else rig.GREETING_POSES, t) if mood in ('waiting', 'new') else 0
            layers = (['head_group', 'left_arm', 'book', 'ink', 'pen', 'right_arm', 'symbol']
                      if mood == 'happy' else ['head_group', 'left_arm', 'right_arm', 'book', 'symbol'])
            frames.append(rig.svg_for(mood, layers,
                progress=amount, blinking=frame in (9,), phase=phase if mood in ('waiting', 'new', 'happy', 'hungry', 'sad') else None))
        moods[mood] = frames
    result = json.dumps({'format': 1, 'frame_ms': 83, 'moods': moods}, separators=(',', ':')) + '\n'
    for path in (ROOT / 'rig-preview/animation-pack.json', ROOT / 'app/src/debug/assets/rig-preview-pack.json'):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(result, encoding='utf-8')
    print(f'Generated {FRAME_COUNT} SVG frames per mood ({len(result)} bytes).')

if __name__ == '__main__':
    generate()
