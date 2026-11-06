import unittest
import xml.etree.ElementTree as ET
import preview_new_absorbed_reading as p
class AbsorbedReadingTests(unittest.TestCase):
    def test_loop_returns_to_start(self):self.assertEqual(p.make_frame(0),p.make_frame(1))
    def test_reading_scans_both_directions_and_pauses(self):
        self.assertLess(p.gaze(.06),0);self.assertGreater(p.gaze(.23),0)
        self.assertEqual(p.gaze(.23),p.gaze(.29))
        self.assertLess(p.gaze(.34),0);self.assertGreater(p.gaze(.52),0)
    def test_lean_and_eye_layer_change(self):
        a,b=(ET.fromstring(p.make_frame(t)) for t in (0,.52))
        self.assertNotEqual(a[1].get('transform'),b[1].get('transform'))
        self.assertNotEqual(a[1][1][0].get('transform'),b[1][1][0].get('transform'))
    def test_book_and_hands_move_together(self):
        a,b=(ET.fromstring(p.make_frame(t)) for t in (0,.52))
        self.assertNotEqual(a[2].get('transform'),b[2].get('transform'))
        self.assertEqual([ET.tostring(n) for n in a[2]],[ET.tostring(n) for n in b[2]])
if __name__=='__main__':unittest.main()
