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

    def test_animator_has_intermediate_new_and_waiting_frames(self):
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        for mood in ('new', 'waiting'):
            self.assertGreater(len(set(pack['moods'][mood])), 10)
