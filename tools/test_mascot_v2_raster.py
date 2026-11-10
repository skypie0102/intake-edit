"""Pillow tests for transparent-reference extraction and real layered atlas packing."""
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from PIL import Image, ImageChops, ImageDraw
import prepare_mascot_raster as prototype
import pack_mascot_raster_layers as atlas_packer

class RasterAssetTest(unittest.TestCase):
    def test_detached_bubble_removed_without_redrawing_subject(self):
        image = Image.new("RGBA", (667, 1000))
        drawing = ImageDraw.Draw(image)
        drawing.ellipse((35,310,160,435), fill=(5,5,5,255))
        drawing.rectangle((210,100,480,940), fill=(226,213,191,255))
        drawing.ellipse((260,240,300,270), fill=(40,40,40,255))
        clean = prototype.isolate_character(image)
        self.assertEqual(clean.getpixel((65,350))[3], 0)
        self.assertEqual(clean.getpixel((275,255)), image.getpixel((275,255)))
        self.assertEqual(clean.getpixel((215,105)), image.getpixel((215,105)))
        self.assertEqual(clean.size, image.size)

    def test_manifest_does_not_falsely_claim_rig(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            image = Image.new("RGBA", (667,1000))
            ImageDraw.Draw(image).rectangle((200,80,450,800), fill=(13,66,33,255))
            ImageDraw.Draw(image).ellipse((25,305,160,440), fill=(0,0,0,255))
            src = root / "portrait.png"
            image.save(src)
            with patch.object(prototype, "SOURCE_SHA256",
                              hashlib.sha256(src.read_bytes()).hexdigest()):
                result = prototype.build(src, root / "out", 95)
            self.assertFalse(result["rig_ready"])
            self.assertFalse(result["animation_ready"])
            self.assertEqual(result["layers"], [])
            self.assertEqual(len(result["frames"]), 4)
            for frame in result["frames"]:
                self.assertTrue(frame["alpha_exact"])
                self.assertGreater(frame["visible_rgb_psnr_db"], 37)
                self.assertTrue(frame["static_reference_only"])
                self.assertLess((root / "out" / frame["file"]).stat().st_size, 100000)

    def test_flat_image_cannot_be_mislabeled_as_rig(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            path = root / "source.json"
            path.write_text(json.dumps({"format": 2, "canvas": [320, 320], "layers": []}))
            with self.assertRaisesRegex(ValueError, "at least two painted layers"):
                atlas_packer.pack(path, root / "out")

    def test_single_flattened_image_is_not_an_articulated_rig(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            Image.new("RGBA", (320, 320), (80, 90, 60, 255)).save(root / "flat.png")
            manifest = {"format": 2, "canvas": [320, 320], "layers": [
                {"id": "flat_character", "file": "flat.png", "position": [0, 0],
                 "pivot": [160, 160], "parent": None, "z": 0}
            ]}
            path = root / "layers.json"
            path.write_text(json.dumps(manifest))
            with self.assertRaisesRegex(ValueError, "at least two"):
                atlas_packer.pack(path, root / "out")

    def test_atlas_crops_real_layers_without_alpha_loss_or_overlap(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            hair = Image.new("RGBA", (120,160))
            ImageDraw.Draw(hair).ellipse((15,20,108,129), fill=(210,180,120,255))
            robe = Image.new("RGBA", (120,120))
            ImageDraw.Draw(robe).rectangle((30,30,100,100), fill=(25,80,60,200))
            hair.save(root / "hair.png")
            robe.save(root / "robe.png")
            manifest = {"format":2, "canvas":[320,320], "pixels_per_unit": 2, "layers":[
                {"id":"hair_back","file":"hair.png","position":[70,30],"pivot":[120,90],"parent":None,"z":0},
                {"id":"robe","file":"robe.png","position":[90,120],"pivot":[140,170],"parent":"hair_back","z":1}
            ]}
            p = root / "layers.json"
            p.write_text(json.dumps(manifest))
            packed = atlas_packer.pack(p, root / "out", width=256)
            self.assertEqual(len(packed["sprites"]),2)
            self.assertEqual(packed["pixels_per_unit"], 2)
            self.assertEqual(packed["sprites"][0]["position"], [77.5, 40.0])
            self.assertEqual(packed["sprites"][0]["size"], [47.0, 55.0])
            texture = Image.open(root / "out" / "texture-0.webp").convert("RGBA")
            bounds=[]
            for sprite in packed["sprites"]:
                x,y,w,h=sprite["src"]
                bounds.append((x,y,x+w,y+h))
                original = hair if sprite["id"] == "hair_back" else robe
                cropped = original.crop(original.getchannel("A").getbbox())
                actual = texture.crop((x,y,x+w,y+h))
                self.assertIsNone(ImageChops.difference(
                    cropped.getchannel("A"), actual.getchannel("A")).getbbox())
            a,b=bounds
            self.assertTrue(a[2]<=b[0] or b[2]<=a[0] or a[3]<=b[1] or b[3]<=a[1])

if __name__ == "__main__":
    unittest.main()
