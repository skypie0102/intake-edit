"""Tests for importing the pristine mascot reference images without recompression."""
import hashlib
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SOURCE = Path(__file__).resolve().parent / "import_mascot_v2_references.py"
spec = importlib.util.spec_from_file_location("import_mascot_v2_references", SOURCE)
importer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(importer)


class ReferenceImportTest(unittest.TestCase):
    def test_sha_validation_precedes_any_copy_and_originals_stay_unmodified(self):
        with tempfile.TemporaryDirectory() as root:
            base = Path(root)
            originals = base / "input"
            originals.mkdir()
            sheet, pose = originals / "sheet.png", originals / "pose.png"
            sheet.write_bytes(b"sheet-example-image-bytes")
            pose.write_bytes(b"portrait-example-image-bytes")
            reference = [
                {"role": role, "filename": filename,
                 "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "checked_in": False}
                for role, filename, path in (
                    ("primary_character_sheet", "sheet.png", sheet),
                    ("original_character_pose", "pose.png", pose))
            ]
            rig = base / "artwork/mascot-v2"
            rig.mkdir(parents=True)
            manifest = rig / "rig-contract.json"
            manifest.write_text(json.dumps({"reference_files": reference}), encoding="utf-8")
            with patch.object(importer, "BASE", rig), \
                 patch.object(importer, "CONTRACT", manifest), \
                 patch.object(importer, "DESTINATION", rig / "reference"):
                self.assertEqual(len(importer.import_references(sheet, pose, dry_run=True)), 2)
                self.assertFalse((rig / "reference").exists())
                pose.write_bytes(b"changed")
                with self.assertRaisesRegex(ValueError, "wrong SHA-256"):
                    importer.import_references(sheet, pose)
                self.assertFalse((rig / "reference").exists(),
                                 "should not partially copy before both inputs pass")
                pose.write_bytes(b"portrait-example-image-bytes")
                outputs = importer.import_references(sheet, pose)
                self.assertEqual([p.read_bytes() for p in outputs],
                                 [sheet.read_bytes(), pose.read_bytes()])
                self.assertTrue(all(x["checked_in"]
                                    for x in json.loads(manifest.read_text())["reference_files"]))
                self.assertEqual(sheet.read_bytes(), b"sheet-example-image-bytes")
                self.assertEqual(pose.read_bytes(), b"portrait-example-image-bytes")
                self.assertEqual(importer.import_references(sheet, pose), outputs)


if __name__ == "__main__":
    unittest.main()
