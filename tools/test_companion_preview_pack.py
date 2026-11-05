import json
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
import generate_companion_rig as rig

ROOT = Path(__file__).resolve().parents[1]

class PreviewPackTest(unittest.TestCase):
    def test_preview_widget_plays_sampled_frames_at_pack_duration(self):
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        ns = '{http://schemas.android.com/apk/res/android}'
        layout = ET.parse(ROOT / 'app/src/debug/res/layout/translation_pet_widget.xml').getroot()
        flipper = layout.find('ViewFlipper')
        self.assertIsNotNone(flipper)
        self.assertEqual(len(flipper), 16)
        static = layout.find('ImageView')
        self.assertIsNotNone(static)
        self.assertEqual(static.get(ns + 'id'), '@+id/pet_widget_static')
        self.assertEqual(static.get(ns + 'visibility'), 'invisible')
        for frames in pack['moods'].values():
            self.assertEqual(int(flipper.get(ns + 'flipInterval')) * len(flipper),
                             pack['frame_ms'] * len(frames))

    def test_transition_blink_keeps_the_neutral_scene_attached(self):
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        self.assertEqual(set(pack['transition_blinks']), set(rig.MOODS))
        for mood in rig.MOODS:
            neutral = ET.fromstring(pack['moods'][mood][36 if mood == 'new' else 0])
            blink = ET.fromstring(pack['transition_blinks'][mood])
            # The head group contains the same head and only substitutes eyes.
            self.assertEqual(neutral[1].get('transform'), blink[1].get('transform'))
            self.assertEqual(ET.tostring(neutral[1][0]), ET.tostring(blink[1][0]))
            self.assertNotEqual(ET.tostring(neutral[1][1]), ET.tostring(blink[1][1]))
            self.assertEqual([ET.tostring(node) for node in neutral[2:]],
                             [ET.tostring(node) for node in blink[2:]])

    def test_idle_blinks_match_the_resting_pose(self):
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        self.assertEqual(set(pack['idle_blinks']), set(rig.MOODS))
        for mood in rig.MOODS:
            rest = ET.fromstring(pack['moods'][mood][0])
            blink = ET.fromstring(pack['idle_blinks'][mood])
            self.assertEqual(ET.tostring(rest[1][0]), ET.tostring(blink[1][0]))
            self.assertNotEqual(ET.tostring(rest[1][1]), ET.tostring(blink[1][1]))
            self.assertEqual([ET.tostring(node) for node in rest[2:]],
                             [ET.tostring(node) for node in blink[2:]])

    def test_new_companion_scans_page_and_centres_gaze_to_greet(self):
        frames = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())['moods']['new']
        self.assertGreater(rig.greeting_reading_eye_x(.05, 1), 6.9)
        self.assertLess(rig.greeting_reading_eye_x(.15, 1), -6.9)
        self.assertEqual(rig.greeting_reading_eye_x(.05, 0), 0)
        self.assertIn('translate(6.99 8.00)', frames[5])
        self.assertIn('translate(-6.94 8.00)', frames[14])
        self.assertIn('translate(0.00 0.00)', frames[48])

    def test_new_greeting_has_smile_surprise_and_one_waving_arm(self):
        frames = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())['moods']['new']
        self.assertIn('M242 246 Q252 248 262 246', frames[0])
        surprise, _, _ = rig.greeting_action(.40)
        self.assertTrue(surprise)
        for frame in frames:
            surprised = '<ellipse cx="252" cy="246" rx="5" ry="7"' in frame
            exclamation = 'M364 139 L364 171' in frame
            smiling = 'M239 240 Q252 253 265 240' in frame
            reading = 'M242 246 Q252 248 262 246' in frame
            self.assertEqual(surprised, exclamation)
            self.assertEqual(sum((smiling, surprised, reading)), 1)
        self.assertEqual(rig.greeting_action(0)[1], 0)
        self.assertEqual(rig.greeting_action(1)[1], 0)
        self.assertEqual(rig.greeting_action(.48)[1], 0)
        self.assertGreater(rig.greeting_action(.67)[1], .99)
        self.assertGreater(abs(rig.greeting_action(.67)[2]), 15)
        self.assertEqual(frames[0].count('fill="#dfd2f1" stroke="#705c87" stroke-width="4"'), 2)
        self.assertEqual(frames[48].count('fill="#dfd2f1" stroke="#705c87" stroke-width="4"'), 2)
        self.assertIn(rig.greeting_arm(0), frames[0])
        self.assertIn(rig.greeting_arm(0), frames[-1])

    def test_new_greeting_reads_then_looks_up_and_returns(self):
        frames = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())['moods']['new']
        self.assertEqual(rig.greeting_reading(0), 1)
        self.assertEqual(rig.greeting_reading(.30), 0)
        self.assertTrue(rig.greeting_action(.40)[0])
        self.assertFalse(rig.greeting_action(.50)[0])
        self.assertEqual(rig.greeting_action(.50)[1], 0)
        self.assertGreater(rig.greeting_action(.70)[1], .9)
        self.assertEqual(rig.greeting_action(.87)[1], 0)
        self.assertGreater(rig.greeting_reading(.95), .5)
        def height(frame):
            return float(re.search(r'<g transform="translate\(0 (-?[0-9.]+)\)', frame).group(1))
        self.assertGreater(height(frames[0]), 19)
        self.assertLess(height(frames[48]), -6)
        self.assertGreater(height(frames[-1]), 19)
        self.assertNotIn('M364 139', frames[25])
        self.assertIn('M364 139', frames[48])
        self.assertIn('rotate(0.00 313 341)', frames[0])
        self.assertIn(f'rotate({rig.waiting_follow_through(.125)[1]:.2f} 313 341)', frames[12])

    def test_waiting_follow_through_loops_and_keeps_book_attached(self):
        for first, last in zip(rig.waiting_follow_through(0), rig.waiting_follow_through(1)):
            self.assertAlmostEqual(first, last, places=6)
        tuft, ribbon = rig.waiting_follow_through(.125)
        self.assertGreater(tuft, 10)
        self.assertGreater(ribbon, 18)
        self.assertLess(ribbon, 25)
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

    def test_posture_shifts_and_recovers_for_each_mood(self):
        for mood in rig.MOODS:
            start = rig.idle_posture(mood, 0)
            self.assertEqual(start, (0, 0))
            self.assertEqual(rig.idle_posture(mood, 1), (0, 0))
            self.assertLess(rig.idle_posture(mood, .22)[0], 0)
            self.assertGreater(rig.idle_posture(mood, .60)[0], 0)
        self.assertLess(rig.idle_posture('sad', .60)[0], rig.idle_posture('waiting', .60)[0])

    def test_pack_is_bundled_and_contains_safe_sized_vector_sequences(self):
        published = (ROOT / 'rig-preview/animation-pack.json').read_bytes()
        bundled = (ROOT / 'app/src/debug/assets/rig-preview-pack.json').read_bytes()
        self.assertEqual(published, bundled)
        self.assertLess(len(published), 4_000_000)
        pack = json.loads(published)
        self.assertEqual(pack['format'], 1)
        self.assertEqual(set(pack['moods']), {'new', 'happy', 'waiting', 'hungry', 'sad'})
        for mood, frames in pack['moods'].items():
            self.assertEqual(len(frames), 96)
            self.assertTrue(all(frame.startswith('<svg ') and len(frame) < 30_000 for frame in frames))
            self.assertTrue(all('viewBox="92 87 320 320"' in frame for frame in frames))

    def test_happy_writing_uses_one_hand_and_a_timed_pen(self):
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        frames = pack['moods']['happy']
        self.assertNotIn('#654e70', frames[0])
        self.assertIn('#654e70', frames[41])
        self.assertIn('#85617a', frames[55])
        self.assertNotIn('#654e70', frames[-1])
        # The original raised arm is replaced by a single animated arm path.
        self.assertNotIn('M305 254 C318 247 324 231', frames[41])
        self.assertEqual(frames[41].count('fill="#dfd2f1" stroke="#705c87" stroke-width="4"'), 2)
        self.assertGreater(len(set(frames)), 10)

    def test_pen_tip_follows_longer_written_mark(self):
        x0, y0 = rig.ink_point(0)
        x1, y1 = rig.ink_point(1)
        self.assertGreaterEqual(x1 - x0, 24)
        for t in (0, .25, .5, .75, 1):
            self.assertEqual(rig.pen_position(1, t), rig.ink_point(t))
        self.assertEqual(rig.writing_pose(.47)[1], rig.writing_pose(.51)[1])
        self.assertLess(rig.writing_pose(.40)[1], rig.writing_pose(.60)[1])
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        writing = pack['moods']['happy'][48]
        # No additional skin-coloured finger shape is introduced.
        self.assertNotIn('fill="#dfd2f1" stroke="#705c87" stroke-width="2"', writing)

    def test_happy_looks_down_and_changes_expression_while_writing(self):
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        frames = pack['moods']['happy']
        rest, focused, finished = frames[0], frames[48], frames[80]
        self.assertIn('translate(0.00 0.00)', rest)
        self.assertIn('translate(3.00 7.00)', focused)
        self.assertIn('M242.00 245.00 Q252.00 250.00 262.00 245.00', focused)
        self.assertNotIn('translate(3.00 7.00)', finished)
        self.assertIn('stroke="#b9905e"', focused)

    def test_happy_sparkles_twinkle_only_after_writing(self):
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        frames = pack['moods']['happy']
        self.assertNotIn('scale(', frames[41])
        twinkles = [frame for frame in frames[60:80] if 'scale(' in frame]
        self.assertGreater(len(twinkles), 5)
        self.assertGreater(len(set(twinkles)), 5)
        self.assertNotIn('scale(', frames[-1])

    def test_hungry_looks_up_then_sighs_and_returns_to_rest(self):
        pack = json.loads((ROOT / 'rig-preview/animation-pack.json').read_text())
        frames = pack['moods']['hungry']
        rest, looking_up, sighing, returned = frames[0], frames[41], frames[68], frames[-1]
        self.assertIn('M242 250.00 Q252 240.00 262 250.00', rest)
        self.assertIn('M242 249.00 Q252 243.00 262 249.00', looking_up)
        self.assertIn('cy="221.00"', looking_up)
        self.assertIn('M343 171 C340 159', frames[48])
        self.assertIn('M351 174 Q354 187', frames[48])
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
        rest, glance, tear, returned = frames[0], frames[39], frames[62], frames[-1]
        self.assertIn('cy="223.00"', rest)
        self.assertIn('cy="218.00"', glance)
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

