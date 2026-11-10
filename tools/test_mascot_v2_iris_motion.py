"""Structural tests for the experimental iris-only raster mascot pilot."""
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from zipfile import ZipFile

from PIL import Image, ImageChops, ImageDraw
import prototype_mascot_v2_iris_motion as iris


class IrisPilotTest(unittest.TestCase):
    @staticmethod
    def fixture(path: Path):
        image=Image.new("RGBA",(667,1000))
        drawing=ImageDraw.Draw(image)
        drawing.rectangle((125,55,505,955),fill=(244,225,196,255))
        drawing.ellipse((30,310,100,410),fill=(20,20,20,255))
        drawing.ellipse((201,279,231,313),fill=(118,118,119,255))
        drawing.ellipse((283,252,334,294),fill=(115,116,119,255))
        drawing.ellipse((214,287,220,295),fill=(15,15,16,255))
        drawing.ellipse((312,262,319,278),fill=(17,18,19,255))
        image.save(path)
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def test_original_pixel_layers_and_static_lashes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            portrait=root/"fixture.png"
            digest=self.fixture(portrait)
            with patch.object(iris,"SOURCE_SHA",digest):
                result=iris.build(portrait,root/"out")
            out=root/"out"
            self.assertTrue(result["neutral_pixel_exact"])
            self.assertFalse(result["approved"])
            self.assertFalse(result["rig_ready"])
            self.assertFalse(result["blink_ready"])
            self.assertEqual(result["layers"],["iris_left","iris_right"])
            neutral=Image.open(out/"neutral-reference.png").convert("RGBA")
            combined=Image.open(out/"neutral-reconstructed.png").convert("RGBA")
            self.assertEqual(neutral.tobytes(),combined.tobytes())
            self.assertEqual(neutral.getpixel((15,310))[3],0) # detached bubble removed
            with Image.open(out/"widget-160-contact.png") as preview:
                self.assertEqual(preview.size,(800,160))
            for i in range(5):
                self.assertTrue((out/f"pose-{i}.png").is_file())
            left=Image.open(out/"pose-0.png").convert("RGBA")
            right=Image.open(out/"pose-4.png").convert("RGBA")
            bounds=ImageChops.difference(left.convert("RGB"),right.convert("RGB")).getbbox()
            self.assertIsNotNone(bounds)
            self.assertGreater(bounds[0],200)  # only iris interiors affected
            self.assertLess(bounds[2],350)
            self.assertGreater(bounds[1],130)
            self.assertLess(bounds[3],220)
            with ZipFile(out/"iris-v2.ora") as z:
                self.assertEqual(z.read("mimetype"),b"image/openraster")
                self.assertEqual(z.read("stack.xml").count(b"<layer "),4)
                self.assertIn(b"NEEDS ARTIST",z.read("stack.xml").upper())

    def test_bad_hash_rejected_without_artwork_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            portrait=root/"fixture.png"
            self.fixture(portrait)
            with self.assertRaisesRegex(ValueError,"approved"):
                iris.build(portrait,root/"out")
            self.assertFalse((root/"out").exists())


if __name__=="__main__":
    unittest.main()
