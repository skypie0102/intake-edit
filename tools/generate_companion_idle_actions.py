"""Generate full-length, mood-specific idle sequences from the reviewed rigs."""
import json
from pathlib import Path
import preview_happy_humming as humming
import preview_happy_delighted as delighted
import preview_waiting_find_place as find_place
import preview_waiting_quiet_thinking as thinking
import preview_hungry_food_thought as food
import preview_hungry_stomach_rumble as rumble
import preview_sad_losing_focus as focus
import preview_sad_book_comfort as comfort
import preview_new_curious_peek as peek
import preview_new_absorbed_reading as absorbed

ROOT = Path(__file__).resolve().parents[1]
ACTIONS = {
    'happy': {'humming': humming, 'delighted': delighted},
    'waiting': {'find_place': find_place, 'thinking': thinking},
    'hungry': {'food': food, 'rumble': rumble},
    'sad': {'losing_focus': focus, 'book_comfort': comfort},
    'new': {'curious_peek': peek, 'absorbed': absorbed},
}

def generate():
    moods = {}
    for mood, actions in ACTIONS.items():
        moods[mood] = {}
        for name, module in actions.items():
            moods[mood][name] = {'frame_ms': module.FRAME_MS,
                'frames': [module.make_frame(i/(module.FRAMES-1)) for i in range(module.FRAMES)]}
    return {'format': 1, 'moods': moods}

def main():
    folder = ROOT / 'app/src/main/assets/companion/idle-actions'
    folder.mkdir(parents=True, exist_ok=True)
    for mood, actions in generate()['moods'].items():
        for name, action in actions.items():
            path = folder / f'{mood}-{name}.json'
            path.write_text(json.dumps(action, separators=(',', ':'))+'\n', encoding='utf-8')
            print(f'Wrote {path.name}: {path.stat().st_size} bytes')

if __name__ == '__main__': main()
