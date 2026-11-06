import unittest
import xml.etree.ElementTree as ET
import preview_happy_delighted as preview


class HappyDelightedPreviewTest(unittest.TestCase):
    def test_rest_pose_loops_exactly(self):
        self.assertEqual(preview.make_frame(0), preview.make_frame(1))

    def test_book_and_both_hands_move_together(self):
        root = ET.fromstring(preview.make_frame(.64))
        wrappers = [child for child in list(root)[2:] if child.get('transform', '').startswith('translate(0 -4.')]
        self.assertGreaterEqual(len(wrappers), 8)
        self.assertEqual(len({child.get('transform') for child in wrappers}), 1)
        arms = [node for wrapper in wrappers for node in wrapper.iter()
                if node.get('fill') == '#dfd2f1' and node.get('stroke-width') == '4']
        self.assertEqual(len(arms), 2)

    def test_stars_appear_and_twinkling_changes_their_size(self):
        def stars(t):
            root = ET.fromstring(preview.make_frame(t))
            return [child.get('transform') for child in root if any(
                node.get('fill') == '#efcce4' for node in child.iter())]
        self.assertEqual(stars(0), [])
        self.assertTrue(stars(.60))
        self.assertNotEqual(stars(.60), stars(.65))
        self.assertEqual(stars(1), [])

    def test_monocle_and_face_remain_attached(self):
        for t in (0, .2, .5, .65, .85, 1):
            head = ET.fromstring(preview.make_frame(t))[1]
            self.assertEqual(sum(node.get('stroke') == '#b9905e' for node in head.iter()), 1)
            self.assertEqual(sum(node.get('fill') == '#57364f' for node in head.iter()), 1)


if __name__ == '__main__':
    unittest.main()
