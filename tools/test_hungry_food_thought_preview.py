import unittest
import xml.etree.ElementTree as ET
import preview_hungry_food_thought as preview
import generate_companion_rig as rig

class HungryFoodThoughtTests(unittest.TestCase):
    def test_loop_returns_to_start(self):
        self.assertEqual(preview.make_frame(0), preview.make_frame(1))

    def test_food_appears_then_vanishes(self):
        def has_food(t):
            root=ET.fromstring(preview.make_frame(t))
            return any(n.get('fill') == '#d78b7d' for n in root.iter())
        self.assertFalse(has_food(.1))
        self.assertTrue(has_food(.5))
        self.assertFalse(has_food(.85))

    def test_eye_moves_toward_food_and_mouth_stays_downturned(self):
        start, middle = (ET.fromstring(preview.make_frame(t)) for t in (0, .45))
        def pupil(root):
            return next(n for n in root[1][1][0] if n.tag == rig.N+'ellipse')
        self.assertGreater(float(pupil(middle).get('cx')),float(pupil(start).get('cx')))
        self.assertTrue(any(n.get('d','').startswith('M242 249') for n in middle[1][0][0]))

    def test_book_is_steady(self):
        first, middle = (ET.fromstring(preview.make_frame(t)) for t in (0, .5))
        for a,b in zip(first[4:10],middle[4:10]):
            self.assertEqual(ET.tostring(a),ET.tostring(b))

if __name__ == '__main__':
    unittest.main()
