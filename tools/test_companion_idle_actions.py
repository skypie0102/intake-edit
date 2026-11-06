import json
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
import generate_companion_idle_actions as actions

class CompanionIdleActionsTest(unittest.TestCase):
    def test_pack_has_the_full_authored_sequence_for_each_mood(self):
        folder=Path(__file__).resolve().parents[1]/'app/src/main/assets/companion/idle-actions'
        self.assertEqual(len(list(folder.glob('*.json'))),10)
        for mood,group in actions.ACTIONS.items():
            for name,source in group.items():
                data=json.loads((folder/f'{mood}-{name}.json').read_text())
                self.assertEqual(data['frame_ms'],source.FRAME_MS)
                self.assertEqual(len(data['frames']),source.FRAMES)
                self.assertEqual(data['frames'][0],source.make_frame(0))
                self.assertEqual(data['frames'][-1],source.make_frame(1))
                for frame in data['frames']:
                    self.assertEqual(ET.fromstring(frame).tag, '{http://www.w3.org/2000/svg}svg')
if __name__=='__main__': unittest.main()
