import unittest
import xml.etree.ElementTree as ET
import preview_sad_losing_focus as p

class SadLosingFocusTests(unittest.TestCase):
    def test_loop_returns_to_rest(self):
        self.assertEqual(p.make_frame(0),p.make_frame(1))
    def test_head_and_eyes_follow_distinct_beats(self):
        start, look, retreat = (ET.fromstring(p.make_frame(t)) for t in (0,.45,.75))
        self.assertNotEqual(start[1].get('transform'),look[1].get('transform'))
        self.assertNotEqual(look[1].get('transform'),retreat[1].get('transform'))
        self.assertNotEqual(start[1][1][0].get('transform'),look[1][1][0].get('transform'))
    def test_book_and_hands_move_together(self):
        start,retreat=(ET.fromstring(p.make_frame(t)) for t in (0,.75))
        self.assertNotEqual(start[2].get('transform'),retreat[2].get('transform'))
        self.assertEqual(ET.tostring(start[2][0]),ET.tostring(retreat[2][0]))
        self.assertEqual(ET.tostring(start[2][1]),ET.tostring(retreat[2][1]))
    def test_no_tears_during_this_action(self):
        for t in (.1,.4,.7):
            root=ET.fromstring(p.make_frame(t))
            for n in root.iter():
                if n.get('fill')=='#84c5e9': self.assertEqual(float(n.get('opacity','1')),0)
if __name__ == '__main__': unittest.main()
