import unittest
import xml.etree.ElementTree as ET
import preview_hungry_food_thought as p
class FoodTests(unittest.TestCase):
    def test_loop(self): self.assertEqual(p.make_frame(0),p.make_frame(1))
    def test_meat_daydream_appears_then_disappears(self):
        def meat(t): return any(n.get('fill')=='#b95d47' for n in ET.fromstring(p.make_frame(t)).iter())
        self.assertFalse(meat(.1));self.assertTrue(meat(.5));self.assertFalse(meat(.9))
    def test_head_and_gaze_move(self):
        a,b=(ET.fromstring(p.make_frame(t)) for t in (0,.5))
        self.assertNotEqual(a[1].get('transform'),b[1].get('transform'))
        self.assertNotEqual(a[1][1][0].get('transform'),b[1][1][0].get('transform'))
    def test_book_remains_steady(self):
        a,b=(ET.fromstring(p.make_frame(t)) for t in (0,.5))
        self.assertNotEqual(a[2].get('transform'),b[2].get('transform'))
        self.assertEqual([ET.tostring(x) for x in a[2]],[ET.tostring(x) for x in b[2]])
if __name__=='__main__': unittest.main()
