import unittest
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

    def test_preview_has_no_extra_arms(self):
        for t in (0, .3, .5, .7, 1):
            root = ET.fromstring(humming.make_frame(t))
            arms = [node for node in root.iter() if node.get('fill') == '#dfd2f1' and node.get('stroke-width') == '4']
            self.assertEqual(len(arms), 2)


if __name__ == '__main__':
    unittest.main()
