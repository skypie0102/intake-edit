import unittest
import xml.etree.ElementTree as ET
import preview_waiting_find_place as preview

class WaitingPlacePreviewTests(unittest.TestCase):
    def test_loop_returns_to_start(self):
        self.assertEqual(preview.make_frame(0), preview.make_frame(1))

    def test_gaze_reads_retraces_and_holds(self):
        self.assertLess(preview.gaze(.08), 0)
        self.assertGreater(preview.gaze(.22), 0)
        self.assertEqual(preview.gaze(.22), preview.gaze(.30))
        self.assertEqual(preview.gaze(.42), preview.gaze(.50))
        self.assertGreater(preview.gaze(.64), 0)

    def test_book_pages_stay_fixed(self):
        start = ET.fromstring(preview.make_frame(0))
        for t in (.2, .4, .6, .75):
            frame = ET.fromstring(preview.make_frame(t))
            for a, b in zip(start[5:], frame[5:]):
                self.assertEqual(ET.tostring(a), ET.tostring(b))

    def test_eyes_move_with_head_and_return(self):
        start = ET.fromstring(preview.make_frame(0))
        searching = ET.fromstring(preview.make_frame(.42))
        self.assertNotEqual(start[1][1][0].get('transform'), searching[1][1][0].get('transform'))
        self.assertNotEqual(start[1].get('transform'), searching[1].get('transform'))
        self.assertEqual(ET.tostring(start[1][0]), ET.tostring(ET.fromstring(preview.make_frame(1))[1][0]))

if __name__ == '__main__':
    unittest.main()
