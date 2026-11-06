import unittest
import xml.etree.ElementTree as ET
import preview_waiting_quiet_thinking as p

class ThinkingTests(unittest.TestCase):
    def test_loop(self): self.assertEqual(p.make_frame(0),p.make_frame(1))
    def test_gaze_shifts_while_hands_remain_on_book(self):
        left,right=(ET.fromstring(p.make_frame(t)) for t in (.30,.55))
        self.assertNotEqual(left[1][1][0].get('transform'),right[1][1][0].get('transform'))
        self.assertEqual(ET.tostring(left[2][0]),ET.tostring(right[2][0]))
        self.assertNotEqual(left[1].get('transform'),right[1].get('transform'))
    def test_no_standard_thought_symbols(self):
        root=ET.fromstring(p.make_frame(.5))
        self.assertFalse(any(n.get('d')==p.rig.QUESTION_CURVE for n in root.iter()))
    def test_book_remains_steady(self):
        a,b=(ET.fromstring(p.make_frame(t)) for t in (0,.5))
        self.assertNotEqual(a[2].get('transform'), b[2].get('transform'))
        self.assertEqual([ET.tostring(x) for x in a[2]], [ET.tostring(x) for x in b[2]])
if __name__=='__main__': unittest.main()
