import unittest
import xml.etree.ElementTree as ET
import preview_new_curious_peek as p
class PeekTests(unittest.TestCase):
    def test_loop_returns_to_start(self): self.assertEqual(p.make_frame(0),p.make_frame(1))
    def test_head_peeks_and_tilts(self):
        a,b,c=(ET.fromstring(p.make_frame(t)) for t in (0,.35,.55))
        self.assertNotEqual(a[1].get('transform'),b[1].get('transform'))
        self.assertNotEqual(b[1].get('transform'),c[1].get('transform'))
    def test_book_and_hands_stay_put(self):
        a,b=(ET.fromstring(p.make_frame(t)) for t in (0,.5))
        self.assertEqual([ET.tostring(n) for n in a[2:]],[ET.tostring(n) for n in b[2:]])
    def test_no_greeting_symbol(self):
        a=ET.fromstring(p.make_frame(.5))
        self.assertFalse(any(n.get('d')=='M364 139 L364 171' for n in a.iter()))
if __name__=='__main__':unittest.main()
