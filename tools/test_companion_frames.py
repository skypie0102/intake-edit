import unittest
import xml.etree.ElementTree as ET

from generate_companion_frames import ANDROID, FRAMES, MOODS, frame, vector_frame


class CompanionFrameTest(unittest.TestCase):
    def test_all_moods_render_complete_vector_sequences(self):
        self.assertEqual(FRAMES, 24)
        for mood in MOODS:
            for index in range(FRAMES):
                with self.subTest(mood=mood, frame=index):
                    root = ET.fromstring(frame(mood, index))
                    self.assertEqual(root.get("viewBox"), "97 92 310 310")
                    self.assertTrue(any(e.get("stroke") == "#b9905e" for e in root.iter()))

    def test_actions_have_distinct_visual_elements(self):
        idle = ET.fromstring(frame("waiting", 3))
        thinking = ET.fromstring(frame("waiting", 9))
        page = ET.fromstring(frame("waiting", 15))
        writing = ET.fromstring(frame("waiting", 21))
        self.assertFalse(any(e.get("stroke") == "#70506a" for e in idle.iter()))
        self.assertTrue(any(e.get("stroke") == "#70506a" for e in writing.iter()))
        self.assertTrue(any(e.get("fill") == "#fff5df" for e in page.iter()))
        self.assertGreater(sum(e.get("fill") == "#c8aed5" for e in thinking.iter()), 0)

    def test_android_vectors_keep_paths_gradients_and_viewport(self):
        for mood in MOODS:
            for index in range(FRAMES):
                with self.subTest(mood=mood, frame=index):
                    vector = ET.fromstring(vector_frame(mood, index))
                    self.assertEqual(vector.get("{" + ANDROID + "}viewportWidth"), "310")
                    self.assertGreater(len(vector.findall(".//path")), 15)
                    self.assertEqual(len(vector.findall(".//gradient")), 2)


if __name__ == "__main__":
    unittest.main()
