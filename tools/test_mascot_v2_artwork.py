"""Validate editable Phase-1 mascot art without installing a graphics renderer."""
import json
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET
import generate_mascot_v2_phase1 as art

ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/"artwork/mascot-v2"
N="{http://www.w3.org/2000/svg}"

class MascotArtworkTest(unittest.TestCase):
    def test_31_true_named_vector_layers(self):
        master=ET.parse(BASE/"master-rig.svg").getroot()
        groups=art.parts(master)
        self.assertEqual(len(groups),31)
        self.assertEqual(set(groups),{x["id"] for x in
            json.loads((BASE/"rig-contract.json").read_text())["rig_layers"]})
        for group in groups.values():
            self.assertTrue(len(group)>0)
            self.assertIn("data-pivot-x",group.attrib)
            self.assertIn("data-pivot-y",group.attrib)
        self.assertIsNone(master.find(".//"+N+"image"))
        self.assertIsNone(master.find(".//"+N+"foreignObject"))
        # The crest has its own moving group and cannot be baked into a sleeve.
        self.assertTrue(len(groups["crest"])>0)
        self.assertIn("220",ET.tostring(groups["crest"],encoding="unicode"))

    def test_twelve_expressions_retain_same_rig_parts(self):
        names=art.validate()
        self.assertEqual(len(names),12)
        self.assertIn("satisfied",names)
        self.assertIn("hmph",names)
        self.assertIn("surprised",names)
        expected=set(art.parts(ET.parse(BASE/"master-rig.svg").getroot()))
        for name in names:
            root=ET.fromstring(art.expression_svg(name))
            self.assertEqual(set(art.parts(root)),expected)
            self.assertEqual(root.attrib["viewBox"],"0 0 320 320")
        self.assertNotEqual(art.expression_svg("tired"),
                            art.expression_svg("satisfied"))
        self.assertNotEqual(art.expression_svg("hmph"),
                            art.expression_svg("surprised"))

    def test_turnaround_references_are_vector_and_distinct(self):
        for view in ("side","back"):
            root=ET.parse(BASE/f"turn-{view}.svg").getroot()
            self.assertEqual(root.attrib["viewBox"],"0 0 320 320")
            self.assertTrue(list(root.iter(N+"path")))
            self.assertFalse(list(root.iter(N+"image")))
        self.assertNotEqual((BASE/"turn-side.svg").read_bytes(),
                            (BASE/"turn-back.svg").read_bytes())

    def test_no_production_companion_resource_is_changed(self):
        # Runtime is deliberately unchanged until Phase 4.
        pack=ROOT/"app/src/main/assets/companion/animation-pack.json"
        self.assertTrue(pack.is_file())
        data=json.loads(pack.read_text(encoding="utf-8"))
        self.assertEqual(data["format"],1)
        self.assertEqual(set(data["moods"]),{"new","waiting","happy","hungry","sad"})

if __name__=="__main__":unittest.main()
