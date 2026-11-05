import unittest
import re
import xml.etree.ElementTree as ET
import preview_happy_humming as humming


class HappyHummingPreviewTest(unittest.TestCase):
    def test_loop_returns_to_the_same_pose(self):
        self.assertEqual(humming.make_frame(0), humming.make_frame(1))

    def test_notes_are_timed_and_do_not_exist_at_rest(self):
        def note_count(t):
            root = ET.fromstring(humming.make_frame(t))
            return sum(1 for node in root.iter() if node.get('stroke') == '#c6a5df')
        self.assertEqual(note_count(0), 0)
        self.assertGreater(note_count(.40), 0)
        self.assertGreater(note_count(.65), 0)
        self.assertEqual(note_count(1), 0)

    def test_head_face_and_monocle_stay_in_the_same_group(self):
        root = ET.fromstring(humming.make_frame(.45))
        head = root[1]
        monocles = [node for node in head.iter() if node.get('stroke') == '#b9905e']
        mouths = [node for node in head.iter() if node.get('fill') == '#57364f']
        self.assertEqual(len(monocles), 1)
        self.assertEqual(len(mouths), 1)

    def test_humming_has_visible_tilt_in_both_directions(self):
        angles = []
        for frame in range(humming.FRAMES):
            head = ET.fromstring(humming.make_frame(frame / (humming.FRAMES - 1)))[1]
            angles.append(float(re.search(r"rotate\(([-0-9.]+)", head.get('transform')).group(1)))
        self.assertGreater(max(angles), 3.5)
        self.assertLess(min(angles), -3.5)

    def test_humming_changes_mouth_and_enlarges_notes(self):
        def mouth(t):
            root = ET.fromstring(humming.make_frame(t))
            return next(node.get('d') for node in root[1].iter() if node.get('fill') == '#57364f')
        self.assertGreater(len({mouth(i / 100) for i in range(30, 80)}), 15)
        notes = [node for node in ET.fromstring(humming.make_frame(.40)).iter() if 'scale(1.90)' in node.get('transform', '')]
        self.assertEqual(len(notes), 1)

    def test_humming_o_is_round_and_tongue_is_hidden(self):
        head = ET.fromstring(humming.make_frame(.3625))[1]
        mouth = next(node for node in head.iter() if node.get('fill') == '#57364f')
        tongue = next(node for node in head.iter() if node.get('fill') == '#ef9eaa')
        self.assertEqual(mouth.get('d').count('Q'), 4)
        self.assertEqual(tongue.get('opacity'), '0.000')
        self.assertIn('M244.00 247.00', mouth.get('d'))

    def test_o_is_held_through_the_humming_phrase(self):
        for t in (.35, .42, .5, .58, .66, .74):
            head = ET.fromstring(humming.make_frame(t))[1]
            mouth = next(node for node in head.iter() if node.get('fill') == '#57364f')
            coords = [float(x) for x in re.findall(r'-?\d+\.\d+', mouth.get('d'))]
            self.assertGreater(coords[0], 243)

    def test_preview_has_no_extra_arms(self):
        for t in (0, .3, .5, .7, 1):
            root = ET.fromstring(humming.make_frame(t))
            arms = [node for node in root.iter() if node.get('fill') == '#dfd2f1' and node.get('stroke-width') == '4']
            self.assertEqual(len(arms), 2)


if __name__ == '__main__':
    unittest.main()
