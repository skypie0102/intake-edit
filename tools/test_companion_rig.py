import unittest
import xml.etree.ElementTree as ET
import re

import generate_companion_rig as rig
from svg_to_android import ANDROID, svg_to_vector


class RigTest(unittest.TestCase):
    def test_moods_keep_two_single_arms_and_attached_monocle(self):
        for mood in rig.MOODS:
            with self.subTest(mood=mood):
                _, _, layers = rig.parts(mood)
                self.assertEqual(len(layers['left_arm'][0]), 1)
                self.assertEqual(len(layers['right_arm'][0]), 1)
                head = ET.tostring(layers['head'], encoding='unicode')
                self.assertIn('#b9905e', head)
                self.assertIn('url(#behind-book)', head)
                self.assertIn('L315 335 L187 335', head)

    def test_approved_book_and_mood_symbols_remain_distinct(self):
        _, _, new = rig.parts('new')
        _, _, waiting = rig.parts('waiting')
        self.assertEqual(ET.tostring(new['book'][0]), ET.tostring(waiting['book'][0]))
        self.assertIn('M307 340', ET.tostring(new['book'][0], encoding='unicode'))
        self.assertIn('M364 139', ET.tostring(new['symbol'][0], encoding='unicode'))
        self.assertIn('M352 161', ET.tostring(waiting['symbol'][0], encoding='unicode'))

    def test_single_limb_moves_and_returns_to_rest(self):
        self.assertEqual(rig.POSES[0], 0)
        self.assertEqual(rig.POSES[-1], 0)
        self.assertLessEqual(max(abs(b-a) for a,b in zip(rig.POSES, rig.POSES[1:])), .3 + 1e-6)
        rest = rig.arm_path(0)
        middle = rig.arm_path(.5)
        self.assertNotEqual(rest, middle)
        self.assertEqual(rest.count('M'), 1)
        self.assertEqual(middle.count('M'), 1)

    def test_app_and_widget_share_arm_pose_coordinates(self):
        source = (rig.ROOT / 'app/src/main/java/cloud/shadowmonarchbooks/intakeedit/CompanionRig.kt').read_text()
        for name, expected in [('armRest', rig.ARM_REST), ('armThink', rig.ARM_THINK)]:
            match = re.search(r'private val ' + name + r' = floatArrayOf\(([^)]*)\)', source)
            self.assertIsNotNone(match)
            actual = tuple(float(part.strip().removesuffix('f')) for part in match.group(1).split(','))
            self.assertEqual(actual, tuple(float(x) for x in expected))

    def test_native_vector_preserves_body_clip_and_book_gradient(self):
        svg = rig.svg_for('waiting',['head_group','left_arm','right_arm','book','symbol'])
        vector = ET.fromstring(svg_to_vector(svg))
        self.assertGreaterEqual(len(vector.findall('.//clip-path')), 3)
        self.assertEqual(len(vector.findall('.//gradient')), 2)
        self.assertEqual(vector.get('{'+ANDROID+'}viewportWidth'), '310')

    def test_widget_has_no_whole_image_fades_or_visible_text(self):
        layout = ET.parse(rig.ROOT / 'app/src/main/res/layout/translation_pet_widget.xml').getroot()
        flipper = layout.find('ViewFlipper')
        self.assertEqual(len(flipper), len(rig.POSES))
        self.assertIsNone(flipper.get('{'+ANDROID+'}inAnimation'))
        self.assertIsNone(flipper.get('{'+ANDROID+'}outAnimation'))
        self.assertEqual(len(layout.findall('.//TextView')), 0)


if __name__ == '__main__':
    unittest.main()
