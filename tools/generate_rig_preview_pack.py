"""Build the downloadable preview pack from the editable vector rig.

Publish the resulting rig-preview/animation-pack.json on the preview branch.
The preview app accepts this data format without an APK update.
"""
import json
from pathlib import Path
import generate_companion_rig as rig

ROOT = Path(__file__).resolve().parents[1]
FRAME_COUNT = 96


def interpolate(values, t):
    x = t * len(values)
    i = int(x) % len(values)
    return values[i] + (values[(i + 1) % len(values)] - values[i]) * (x - int(x))


def generate():
    moods = {}
    for mood in rig.MOODS:
        frames = []
        count = FRAME_COUNT
        for frame in range(count):
            t = frame / count
            # Leave time to read before and after the thinking gesture.
            phase = (max(0, min(1, (t - .30) / .58)) if mood == 'waiting'
                     else max(0, min(1, (t - .20) / .65)) if mood == 'new'
                     else max(0, min(1, (t - .12) / .76)) if mood in ('happy', 'hungry')
                     else max(0, min(1, (t - .10) / .80)) if mood == 'sad'
                     else (t if t < .94 else 0))
            amount = (interpolate(rig.POSES, phase) if mood == 'waiting'
                      else interpolate(rig.GREETING_POSES, phase) if mood == 'new' else 0)
            layers = (['head_group', 'left_arm', 'book', 'ink', 'pen', 'right_arm', 'symbol']
                      if mood == 'happy' else ['head_group', 'left_arm', 'right_arm', 'book', 'symbol'])
            frames.append(rig.svg_for(mood, layers,
                progress=amount, blinking=frame in ((12, 13, 70, 71, 75, 76) if mood == 'waiting' else (25, 26, 77, 78) if mood == 'new' else (15, 16, 84, 85) if mood == 'happy' else (10, 11, 80, 81) if mood == 'hungry' else (15, 16, 84, 85)),
                phase=phase, idle_phase=t))
        moods[mood] = frames
    result = json.dumps({'format': 1, 'frame_ms': 83, 'moods': moods}, separators=(',', ':')) + '\n'
    for path in (ROOT / 'rig-preview/animation-pack.json', ROOT / 'app/src/debug/assets/rig-preview-pack.json'):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(result, encoding='utf-8')
    print(f'Generated {FRAME_COUNT} frames per mood ({len(result)} bytes).')

if __name__ == '__main__':
    generate()

