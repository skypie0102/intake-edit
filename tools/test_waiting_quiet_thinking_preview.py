import unittest
import xml.etree.ElementTree as ET
import preview_waiting_quiet_thinking as preview
import generate_companion_rig as rig

class QuietThinkingTests(unittest.TestCase):
    def test_loop_returns_to_start(self):
        self.assertEqual(preview.make_frame(0), preview.make_frame(1))

    def test_thought_and_arm_settle_at_ends(self):
        self.assertEqual(preview.thinking_amount(0), 0)
        self.assertEqual(preview.thinking_amount(1), 0)
        self.assertGreater(preview.thinking_amount(.5), .99)
        start, middle = (ET.fromstring(preview.make_frame(t)) for t in (0, .5))
        self.assertNotEqual(start[3][0][0].get('d'), middle[3][0][0].get('d'))

    def test_question_appears_after_dots_and_disappears(self):
        early, middle, late = (ET.fromstring(preview.make_frame(t)) for t in (.31, .62, .9))
        def marks(root):
            return [n for n in root.iter() if n.get('d') == rig.QUESTION_CURVE]
        self.assertFalse(marks(early))
        self.assertTrue(marks(middle))
        self.assertFalse(marks(late))
        self.assertTrue(any(n.tag == rig.N+'circle' and n.get('cx') == str(rig.DOTS[0][0]) for n in early.iter()))

    def test_book_stays_fixed(self):
        start, middle = (ET.fromstring(preview.make_frame(t)) for t in (0, .5))
        for a, b in zip(list(start)[4:10], list(middle)[4:10]):
            self.assertEqual(ET.tostring(a), ET.tostring(b))

if __name__ == '__main__':
    unittest.main()
