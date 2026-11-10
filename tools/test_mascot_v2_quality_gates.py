"""Quality gates for raster sprite atlas compositing and approved expressions."""
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image, ImageDraw

import qa_mascot_raster_atlas as qa
import extract_mascot_v2_expressions as expressions


class MascotLayerQualityTest(unittest.TestCase):
    def build_fixture(self, root: Path):
        background = Image.new("RGBA", (48, 48))
        ImageDraw.Draw(background).ellipse((2, 2, 34, 34), fill=(235, 220, 196, 255))
        eye = Image.new("RGBA", (16, 16))
        ImageDraw.Draw(eye).ellipse((2, 4, 11, 12), fill=(79, 80, 82, 255))
        ImageDraw.Draw(eye).point((5, 5), fill=(255, 255, 255, 220))
        neutral = Image.new("RGBA", (640, 640))
        neutral.alpha_composite(background, (120, 100))
        neutral.alpha_composite(eye, (133, 113))
        source = root / "neutral.png"
        neutral.save(source)
        atlas = Image.new("RGBA", (128, 64))
        atlas.alpha_composite(background, (4, 4))
        atlas.alpha_composite(eye, (68, 4))
        atlas.save(root / "texture-0.webp", "WEBP", lossless=True, exact=True, method=6)
        manifest = {
            "format": 2, "kind": "raster-rig", "canvas": [320, 320],
            "pixels_per_unit": 2,
            "atlases": [{"file": "texture-0.webp", "width": 128, "height": 64}],
            "sprites": [
                {"id": "face", "atlas": 0, "src": [4, 4, 48, 48], "position": [60, 50],
                 "size": [24, 24], "pivot": [75, 62], "parent": None, "z": 0},
                {"id": "eye", "atlas": 0, "src": [68, 4, 16, 16], "position": [66.5, 56.5],
                 "size": [8, 8], "pivot": [70, 61], "parent": "face", "z": 1},
            ],
        }
        record = root / "atlas-manifest.json"
        record.write_text(json.dumps(manifest))
        return record, source

    def test_neutral_reconstructs_exactly_under_light_and_dark(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest, source = self.build_fixture(root)
            report = qa.write_report(manifest, source, root / "quality", strict=True)
            self.assertTrue(report["neutral_pixel_exact_on_light_and_dark"])
            self.assertEqual(report["maximum_visible_color_delta_8bit"], 0)
            self.assertEqual(report["alpha_changed_pixels"], 0)
            self.assertTrue((root / "quality" / "quality-contact-160.png").is_file())
            self.assertTrue((root / "quality" / "difference-heatmap.png").is_file())

    def test_detects_rgb_pixel_mismatch_when_alpha_unchanged(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest, source = self.build_fixture(root)
            with Image.open(source) as file:
                image = file.convert("RGBA")
            r, g, b, a = image.getpixel((135, 115))
            image.putpixel((135, 115), (r ^ 0x0f, g, b, a))
            image.save(source)
            report = qa.write_report(manifest, source, root / "quality")
            self.assertFalse(report["neutral_pixel_exact_on_light_and_dark"])
            self.assertEqual(report["alpha_changed_pixels"], 0)
            self.assertGreater(report["maximum_visible_color_delta_8bit"], 0)
            with self.assertRaisesRegex(ValueError, "recompose"):
                qa.write_report(manifest, source, root / "strict", strict=True)

    def test_detects_alpha_pixel_mismatch(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            manifest, source = self.build_fixture(root)
            with Image.open(source) as file:
                image = file.convert("RGBA")
            image.putpixel((130, 108), (0, 0, 0, 0))
            image.save(source)
            report = qa.write_report(manifest, source, root / "quality")
            self.assertFalse(report["neutral_pixel_exact_on_light_and_dark"])
            self.assertGreater(report["alpha_changed_pixels"], 0)


class MascotExpressionSourceTest(unittest.TestCase):
    def test_twelve_lossless_reference_tiles_not_rig_parts(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            src = Image.new("RGB", (1536, 1024), (246, 224, 209))
            pen = ImageDraw.Draw(src)
            for n in range(12):
                row, col = divmod(n, 4)
                x = expressions.COLUMNS[col][0] + 30
                y = expressions.ROWS[row][0] + 20
                pen.ellipse((x, y, x + 30, y + 40), fill=(n * 13 + 30, 46, 51))
            src_path = root / "sheet.png"
            src.save(src_path)
            expected = hashlib.sha256(src_path.read_bytes()).hexdigest()
            with patch.object(expressions, "EXPECTED_SHA256", expected):
                report = expressions.extract(src_path, root / "out")
            self.assertEqual(report["count"], 12)
            self.assertTrue(report["pixel_exact_unscaled_crops"])
            self.assertFalse(report["rig_ready"])
            self.assertFalse(report["animation_ready"])
            for entry in report["entries"]:
                self.assertFalse(entry["rig_layer"])
                with Image.open(root / "out" / entry["image"]) as im:
                    self.assertEqual(im.convert("RGB").tobytes(), src.crop(entry["crop"]).tobytes())
            with Image.open(root / "out" / "expression-reference-atlas.webp") as im:
                self.assertEqual(im.size, (512, 384))

    def test_rejects_unapproved_sheet_without_emitting_tiles(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            image = root / "fake.png"
            Image.new("RGB", (1536, 1024), (255, 255, 255)).save(image)
            with self.assertRaisesRegex(ValueError, "checksum"):
                expressions.extract(image, root / "output")
            self.assertFalse((root / "output").exists())


if __name__ == "__main__":
    unittest.main()
