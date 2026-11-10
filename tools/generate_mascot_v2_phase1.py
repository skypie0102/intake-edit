"""Generate independent SVG expression variants from the editable mascot v2 master.

No original Book Spirit resources are modified. Python standard library only;
optional CairoSVG preview rendering lives outside the app build pipeline.
"""
from __future__ import annotations
import argparse
from copy import deepcopy
import json
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "artwork/mascot-v2"
SVG = "http://www.w3.org/2000/svg"
N = "{" + SVG + "}"
ET.register_namespace("", SVG)

LEFT_BLINK = 'M110 123Q129 134 147 118'
RIGHT_BLINK = 'M165 118Q190 131 211 112'
EYE_PATHS = {
    "tired": ("M109 124Q126 131 146 120", "M166 119Q189 130 211 116"),
    "satisfied": (LEFT_BLINK, RIGHT_BLINK),
    "embarrassed": ("M108 121Q127 125 146 118", "M165 115Q189 125 210 112"),
}
BLUSH_PATH = "M120 146L133 139M189 142L205 134"

def parts(root: ET.Element) -> dict[str, ET.Element]:
    return {node.get("data-rig-part"): node for node in root.iter(N + "g")
            if node.get("data-rig-part")}

def replace_group(group: ET.Element, nodes: list[ET.Element]) -> None:
    for node in list(group):
        group.remove(node)
    for node in nodes:
        group.append(node)

def path(d: str, **attributes: str) -> ET.Element:
    return ET.Element(N + "path", {"d": d, **attributes})

def expression_svg(name: str) -> str:
    definitions = json.loads((ART / "expressions.json").read_text(encoding="utf-8"))
    specification = definitions["expressions"][name]
    root = ET.parse(ART / "master-rig.svg").getroot()
    groups = parts(root)
    expected = set(json.loads((ART / "rig-contract.json").read_text())["rig_layers"][i]["id"]
                   for i in range(len(json.loads((ART / "rig-contract.json").read_text())["rig_layers"])))
    if set(groups) != expected:
        raise ValueError("Master rig part IDs differ from rig contract")
    for side in ("left", "right"):
        replace_group(groups["eyebrow_" + side], [path(
            specification["brows"][side], fill="none", stroke="#423d38",
            **{"stroke-width": "3.5", "stroke-linecap": "round"})])
    replace_group(groups["mouth"], [path(specification["mouth"],
        fill=specification["mouth_fill"], stroke="#503b36",
        **{"stroke-width": "1.5", "stroke-linecap": "round"})])
    eyes = specification["eyes"]
    if eyes in EYE_PATHS:
        for side, d in zip(("left", "right"), EYE_PATHS[eyes]):
            replace_group(groups["eye_" + side], [path(d,
                fill="none", stroke="#232424",
                **{"stroke-width": "3.5", "stroke-linecap": "round"})])
    if specification["blush"] == "strong":
        groups["face_base"].append(path(BLUSH_PATH, fill="none",
            stroke="#e99390", opacity=".66", **{"stroke-width": "3"}))
    root.set("aria-label", f"Blonde Slytherin chibi witch - {name}")
    return ET.tostring(root, encoding="unicode") + "\n"

def validate() -> list[str]:
    config = json.loads((ART / "expressions.json").read_text(encoding="utf-8"))
    if config["schema_version"] != 1:
        raise ValueError("Unexpected expression format")
    names = list(config["expressions"])
    if len(names) != 12 or len(set(names)) != 12:
        raise ValueError("Expected 12 unique expressions")
    previous = set()
    for name in names:
        data = expression_svg(name)
        tree = ET.fromstring(data)
        if tree.attrib.get("viewBox") != "0 0 320 320":
            raise ValueError("Wrong viewport")
        if len(parts(tree)) != 31:
            raise ValueError("Wrong rig layer count")
        face = tuple(ET.tostring(parts(tree)[x]) for x in
                     ("eyebrow_left", "eyebrow_right", "mouth", "eye_left", "eye_right"))
        if face in previous:
            raise ValueError(f"Repeated face: {name}")
        previous.add(face)
    return names

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--validate", action="store_true")
    parser.add_argument("--output", type=Path, default=ART / "generated")
    arguments = parser.parse_args()
    names = validate()
    if not arguments.validate:
        arguments.output.mkdir(parents=True, exist_ok=True)
        for name in names:
            (arguments.output / (name + ".svg")).write_text(
                expression_svg(name), encoding="utf-8")
    print(f"Validated {len(names)} expressions; source master unchanged")

if __name__ == "__main__":
    main()
