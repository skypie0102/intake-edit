import json
import unittest
from pathlib import Path
import generate_companion_rig as rig

ROOT = Path(__file__).resolve().parents[1]

class PreviewPackTest(unittest.TestCase):
    def test_waiting_follow_through_loops_and_keeps_book_attached(self):
        for first, last in zip(rig.waiting_follow_through(0), rig.waiting_follow_through(1)):
            self.assertAlmostEqual(first, last, places=6)
        tuft, ribbon = rig.waiting_follow_through(.125)
        self.assertAlmostEqual(tuft, 12)
        self.assertGreater(ribbon, 20)
        self.assertLess(ribbon, 26)
        frames = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())['moods']['waiting']
        self.assertIn('rotate(0.00 313 341)', frames[0])
        self.assertIn(f'rotate({ribbon:.2f} 313 341)', frames[12])
        self.assertIn(f'C228 146 {231+tuft:.2f} 123 {230+tuft:.2f} 113', frames[12])
        for i in (0, 12, 24, 48, 72):
            self.assertIn('M102 288 Q182 267 253 305', frames[i])

    def test_waiting_breath_and_glance_return_smoothly_to_rest(self):
        for t in (0, 1):
            breath, glance = rig.waiting_idle(t)
            self.assertAlmostEqual(breath, 0, places=6)
            self.assertAlmostEqual(glance, 0, places=6)
        self.assertGreater(rig.waiting_idle(.2)[1], .9)
        self.assertEqual(rig.waiting_idle(.5)[1], 0)
        # The gaze takes close to a second to lower at the 83 ms frame rate.
        self.assertLess(rig.waiting_idle(.08)[1], .3)
        self.assertGreater(rig.waiting_idle(.16)[1], .99)
        self.assertGreater(rig.waiting_idle(.25)[1], .9)
        frames = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())['moods']['waiting']
        self.assertIn('translate(0 3.00)', frames[20])
        self.assertIn('M352 161', frames[58])

    def test_pack_is_bundled_and_contains_safe_sized_vector_sequences(self):
        published = (ROOT / 'rig-preview/animation-pack.json').read_bytes()
        bundled = (ROOT / 'app/src/debug/assets/rig-preview-pack.json').read_bytes()
        self.assertEqual(published, bundled)
        self.assertLess(len(published), 4_000_000)
        pack = json.loads(published)
        self.assertEqual(pack['format'], 1)
        self.assertEqual(set(pack['moods']), {'new', 'happy', 'waiting', 'hungry', 'sad'})
        for mood, frames in pack['moods'].items():
            self.assertEqual(len(frames), 96 if mood == 'waiting' else 48)
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

    def test_pen_tip_follows_longer_written_mark(self):
        x0, y0 = rig.ink_point(0)
        x1, y1 = rig.ink_point(1)
        self.assertGreaterEqual(x1 - x0, 24)
        for t in (0, .25, .5, .75, 1):
            self.assertEqual(rig.pen_position(1, t), rig.ink_point(t))
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        writing = pack['moods']['happy'][24]
        # No additional skin-coloured finger shape is introduced.
        self.assertNotIn('fill="#dfd2f1" stroke="#705c87" stroke-width="2"', writing)

    def test_happy_looks_down_and_changes_expression_while_writing(self):
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        frames = pack['moods']['happy']
        rest, focused, finished = frames[0], frames[24], frames[40]
        self.assertIn('translate(0.00 0.00)', rest)
        self.assertIn('translate(3.00 7.00)', focused)
        self.assertIn('M242.00 245.00 Q252.00 250.00 262.00 245.00', focused)
        self.assertNotIn('translate(3.00 7.00)', finished)
        self.assertIn('stroke="#b9905e"', focused)

    def test_happy_sparkles_twinkle_only_after_writing(self):
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        frames = pack['moods']['happy']
        self.assertNotIn('scale(', frames[20])
        twinkles = [frame for frame in frames[31:45] if 'scale(' in frame]
        self.assertGreater(len(twinkles), 5)
        self.assertGreater(len(set(twinkles)), 5)
        self.assertNotIn('scale(', frames[-1])

    def test_hungry_looks_up_then_sighs_and_returns_to_rest(self):
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        frames = pack['moods']['hungry']
        rest, looking_up, sighing, returned = frames[0], frames[20], frames[37], frames[-1]
        self.assertIn('M242 250.00 Q252 240.00 262 250.00', rest)
        self.assertIn('M242 249.00 Q252 243.00 262 249.00', looking_up)
        self.assertIn('cy="221.00"', looking_up)
        self.assertIn('M343 171 C340 159', frames[24])
        self.assertIn('M351 174 Q354 187', frames[24])
        self.assertNotIn('M343 171 C340 159', rest)
        self.assertNotIn('M343 171 C340 159', returned)
        self.assertNotIn('M146 226 q-8', rest)
        self.assertIn('M146 226 q-8', sighing)
        self.assertNotIn('M146 226 q-8', returned)
        self.assertIn('stroke="#b9905e"', looking_up)
        self.assertGreater(len(set(frames)), 15)

    def test_sad_glance_and_single_timed_cheek_tear(self):
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        frames = pack['moods']['sad']
        rest, glance, tear, returned = frames[0], frames[18], frames[33], frames[-1]
        self.assertIn('cy="223.00"', rest)
        self.assertIn('cy="220.00"', glance)
        self.assertIn('opacity="0.000"', rest)
        self.assertIn('opacity="1.000"', tear)
        self.assertIn('opacity="0.000"', returned)
        self.assertNotIn('M293 230 C284 246', tear)
        self.assertIn('stroke="#b9905e"', glance)
        self.assertGreater(len(set(frames)), 15)

    def test_animator_has_intermediate_new_and_waiting_frames(self):
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        for mood in ('new', 'waiting'):
            self.assertGreater(len(set(pack['moods'][mood])), 10)
