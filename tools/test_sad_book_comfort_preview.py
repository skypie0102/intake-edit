import unittest
import xml.etree.ElementTree as ET
import preview_sad_book_comfort as p
class ComfortTests(unittest.TestCase):
    def test_loop_returns_to_rest(self):
        self.assertEqual(p.make_frame(0), p.make_frame(1))
    def test_eyes_close_and_reopen(self):
        a,b,c=(ET.fromstring(p.make_frame(t)) for t in (0,.5,.99))
        self.assertEqual(float(a[1][1].get('opacity')),1)
        self.assertEqual(float(b[1][1].get('opacity')),0)
        self.assertEqual(float(b[1][2].get('opacity')),1)
        self.assertGreater(float(c[1][1].get('opacity')),.99)
    def test_book_and_hands_move_as_one(self):
        a,b=(ET.fromstring(p.make_frame(t)) for t in (0,.5))
        self.assertNotEqual(a[2].get('transform'),b[2].get('transform'))
        self.assertEqual(ET.tostring(a[2][0]),ET.tostring(b[2][0]))
        self.assertEqual(ET.tostring(a[2][1]),ET.tostring(b[2][1]))
    def test_head_nestles_and_no_tears(self):
        a,b=(ET.fromstring(p.make_frame(t)) for t in (0,.5))
        self.assertNotEqual(a[1].get('transform'),b[1].get('transform'))
        self.assertTrue(all(float(n.get('opacity','1')) == 0 for n in b.iter() if n.get('fill')=='#84c5e9'))
if __name__=='__main__': unittest.main()
