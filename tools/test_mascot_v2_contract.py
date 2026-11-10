"""Validate the standalone v2 mascot authoring contract.

This validator intentionally leaves the existing Book Spirit resources untouched.
Artwork checks activate once the manifest moves from 'specification' to 'rig_ready'.
"""
import hashlib
import json
import re
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / "artwork/mascot-v2/rig-contract.json"
EXPECTED_MOODS = {"new", "waiting", "happy", "hungry", "sad"}
EXPECTED_ACTIONS = {"reading", "writing", "page_turn"}
EXPECTED_EXPRESSIONS = {
    "neutral", "hmph", "annoyed", "surprised", "thinking", "suspicious",
    "flustered", "angry", "embarrassed", "tired", "satisfied", "sulky",
}
REQUIRED_LAYERS = {
    "hair_back", "bangs", "hair_side_left", "hair_side_right",
    "face_base", "eye_left", "eye_right", "eyebrow_left", "eyebrow_right",
    "mouth", "neck", "torso", "shirt", "tie", "knit", "crest", "robe_back",
    "robe_front", "skirt", "leg_left", "leg_right", "shoe_left", "shoe_right",
    "upper_arm_left", "upper_arm_right", "forearm_left", "forearm_right",
    "hand_left", "hand_right",
}
EXPECTED_IDLE_ACTIONS = {
    "new": {"curious_peek", "absorbed"},
    "waiting": {"find_place", "thinking"},
    "happy": {"humming", "delighted"},
    "hungry": {"food", "rumble"},
    "sad": {"losing_focus", "book_comfort"},
}


def contract():
    return json.loads(CONTRACT.read_text(encoding="utf-8"))


class MascotV2ContractTest(unittest.TestCase):
    def test_schema_and_staging_isolation(self):
        value = contract()
        self.assertEqual(value["schema_version"], 1)
        self.assertEqual(value["mascot_id"], "mascot-v2")
        self.assertIn(value["stage"], ("specification", "rig_ready"))
        self.assertEqual(value["canvas"]["width"], 320)
        self.assertEqual(value["canvas"]["height"], 320)
        self.assertEqual(value["canvas"]["widget_preview_px"], 160)
        self.assertTrue(value["canvas"]["transparent"])
        self.assertTrue(value["guardrails"]["do_not_modify_production_pack_until_cutover"])
        self.assertFalse(value["guardrails"]["production_cutover"])
        self.assertNotEqual(value["pack_compatibility"]["source_pack"],
                            value["pack_compatibility"]["production_pack"])
        self.assertNotEqual(value["pack_compatibility"]["preview_pack"],
                            value["pack_compatibility"]["production_pack"])
        self.assertEqual(value["pack_compatibility"]["format"], 1)
        self.assertEqual(value["pack_compatibility"]["reaction_frames"], 96)
        self.assertEqual(value["pack_compatibility"]["shared_action_frames"], 48)
        self.assertEqual(value["pack_compatibility"]["frame_duration_ms"], 83)

    def test_reference_integrity_metadata(self):
        value = contract()
        references = value["reference_files"]
        self.assertEqual({x["role"] for x in references},
                         {"primary_character_sheet", "original_character_pose"})
        for ref in references:
            self.assertRegex(ref["sha256"], r"^[0-9a-f]{64}$")
            self.assertGreaterEqual(ref["pixel_size"][0], 600)
            self.assertGreaterEqual(ref["pixel_size"][1], 800)
            file = ROOT / "artwork/mascot-v2/reference" / ref["filename"]
            if file.exists():
                digest = hashlib.sha256(file.read_bytes()).hexdigest()
                self.assertEqual(digest, ref["sha256"], f"reference modified: {file.name}")
                self.assertTrue(ref["checked_in"], f"update manifest for checked-in {file.name}")
            elif ref["checked_in"]:
                self.fail(f"Missing committed reference: {file}")

    def test_all_parts_unique_pivoted_and_parented(self):
        value = contract()
        layers = value["rig_layers"]
        ids = [layer["id"] for layer in layers]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertTrue(REQUIRED_LAYERS <= set(ids))
        self.assertEqual([layer["z"] for layer in layers], list(range(len(layers))))
        seen = set()
        for layer in layers:
            self.assertRegex(layer["id"], r"^[a-z][a-z0-9_]*$")
            self.assertEqual(len(layer["pivot"]), 2)
            x, y = layer["pivot"]
            self.assertTrue(0 <= x <= 320 and 0 <= y <= 320, layer)
            self.assertTrue(layer["parent"] is None or layer["parent"] in seen,
                            f"parent should precede child: {layer}")
            seen.add(layer["id"])
        # Arms/eyes must be attached, not animated as free-floating props.
        parents = {layer["id"]: layer["parent"] for layer in layers}
        for side in ("left", "right"):
            self.assertEqual(parents[f"forearm_{side}"], f"upper_arm_{side}")
            self.assertEqual(parents[f"hand_{side}"], f"forearm_{side}")
            self.assertEqual(parents[f"eyebrow_{side}"], "face_base")
            self.assertEqual(parents[f"eye_{side}"], "face_base")

    def test_all_expressions_and_moods_are_defined(self):
        value = contract()
        self.assertEqual(set(value["expressions"]), EXPECTED_EXPRESSIONS)
        self.assertEqual(set(value["moods"]), EXPECTED_MOODS)
        self.assertEqual(set(value["shared_actions"]), EXPECTED_ACTIONS)
        for mood, details in value["moods"].items():
            self.assertIn(details["default_expression"], value["expressions"])
            self.assertTrue(details["reaction"])
            self.assertEqual(set(details["idle_actions"]), EXPECTED_IDLE_ACTIONS[mood])
        self.assertEqual(len({v["reaction"] for v in value["moods"].values()}), 5)

    def test_guardrails_and_production_compatibility(self):
        value = contract()
        guardrails = value["guardrails"]
        for flag in ("no_text_in_widget", "transparent_widget",
                     "no_whole_character_fades", "keep_chapter_deeplink",
                     "keep_feeding_and_levels"):
            self.assertTrue(guardrails[flag], flag)
        production = ROOT / value["pack_compatibility"]["production_pack"]
        self.assertTrue(production.is_file())
        old_pack = json.loads(production.read_text(encoding="utf-8"))
        self.assertEqual(old_pack["format"], 1)
        self.assertEqual(set(old_pack["moods"]), EXPECTED_MOODS)
        self.assertEqual(set(old_pack["shared_actions"]), EXPECTED_MOODS)

    def test_svg_and_pack_when_stage_is_rig_ready(self):
        value = contract()
        if value["stage"] == "specification":
            return
        svg_file = ROOT / value["master_svg"]
        self.assertTrue(svg_file.is_file(), "rig_ready requires canonical SVG")
        root = ET.parse(svg_file).getroot()
        self.assertTrue(root.tag.endswith("svg"))
        self.assertEqual(root.attrib.get("viewBox"), "0 0 320 320")
        actual_ids = {node.attrib["id"] for node in root.iter() if "id" in node.attrib}
        self.assertTrue({p["id"] for p in value["rig_layers"]} <= actual_ids)
        # Keep the exported pack separate from the production one until cutover.
        pack_file = ROOT / value["pack_compatibility"]["source_pack"]
        self.assertTrue(pack_file.is_file())
        pack = json.loads(pack_file.read_text(encoding="utf-8"))
        self.assertEqual(pack["format"], 1)
        self.assertEqual(set(pack["moods"]), EXPECTED_MOODS)
        for mood in EXPECTED_MOODS:
            self.assertEqual(len(pack["moods"][mood]), 96)
            self.assertEqual(set(pack["shared_actions"][mood]), EXPECTED_ACTIONS)
            for action in EXPECTED_ACTIONS:
                self.assertEqual(len(pack["shared_actions"][mood][action]), 48)


if __name__ == "__main__":
    unittest.main()
