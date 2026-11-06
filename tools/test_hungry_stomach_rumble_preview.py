import unittest
import xml.etree.ElementTree as ET
import preview_hungry_stomach_rumble as p

class RumbleTests(unittest.TestCase):
    def test_loop_returns_to_start(self):
        self.assertEqual(p.make_frame(0),p.make_frame(1))
    def test_arm_moves_as_one_continuous_shape(self):
        self.assertNotEqual(p.hand_path(0),p.hand_path(1))
        self.assertEqual(p.hand_path(.5).count('M'),1)
    def test_rumble_appears_and_fades(self):
        def marks(t):
            return [n for n in ET.fromstring(p.make_frame(t)).iter() if n.get('stroke')=='#c8aacf']
        self.assertFalse(marks(.1)); self.assertTrue(marks(.33)); self.assertFalse(marks(.6))
    def test_head_reacts_and_returns(self):
        a,b=(ET.fromstring(p.make_frame(t)) for t in (0,.33))
        self.assertNotEqual(a[1].get('transform'),b[1].get('transform'))
        self.assertEqual(p.make_frame(0),p.make_frame(1))
if __name__ == '__main__': unittest.main()
