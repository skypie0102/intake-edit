import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

class PreviewPackTest(unittest.TestCase):
    def test_pack_is_bundled_and_contains_safe_sized_vector_sequences(self):
        published = (ROOT / 'rig-preview/animation-pack.json').read_bytes()
        bundled = (ROOT / 'app/src/debug/assets/rig-preview-pack.json').read_bytes()
        self.assertEqual(published, bundled)
        self.assertLess(len(published), 4_000_000)
        pack = json.loads(published)
        self.assertEqual(pack['format'], 1)
        self.assertEqual(set(pack['moods']), {'new', 'happy', 'waiting', 'hungry', 'sad'})
        for frames in pack['moods'].values():
            self.assertEqual(len(frames), 48)
            self.assertTrue(all(frame.startswith('<svg ') and len(frame) < 30_000 for frame in frames))

    def test_happy_writing_uses_one_hand_and_a_timed_pen(self):
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        frames = pack['moods']['happy']
        self.assertNotIn('#654e70', frames[0])
        self.assertIn('#654e70', frames[20])
        self.assertIn('#85617a', frames[30])
        self.assertNotIn('#654e70', frames[-1])
        # The original raised arm is replaced by a single animated arm path.
        self.assertNotIn('M305 254 C318 247 324 231', frames[20])
        self.assertEqual(frames[20].count('fill="#dfd2f1" stroke="#705c87" stroke-width="4"'), 2)
        self.assertGreater(len(set(frames)), 10)

    def test_animator_has_intermediate_new_and_waiting_frames(self):
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        for mood in ('new', 'waiting'):
            self.assertGreater(len(set(pack['moods'][mood])), 10)
