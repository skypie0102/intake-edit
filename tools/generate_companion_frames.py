"""Render the companion's independent vector parts into lightweight widget frames.

Requires Inkscape. The checked-in SVG remains the editable source of truth.
"""
from concurrent.futures import ThreadPoolExecutor
from copy import deepcopy
from math import sin, pi
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/assets/companion/book_spirit_moods.svg"
OUTPUT = ROOT / "app/src/main/res/drawable-nodpi"
SVG = "http://www.w3.org/2000/svg"
ET.register_namespace("", SVG)
N = "{" + SVG + "}"
MOODS = {"happy": 6, "waiting": 7, "hungry": 8, "sad": 9}
FRAMES = 12


def frame(mood, index):
    source = ET.parse(SOURCE).getroot()
    card = source[MOODS[mood]]
    root = ET.Element(N + "svg", {"width": "320", "height": "320", "viewBox": "97 92 310 310"})
    root.append(deepcopy(source[0]))
    original = card[2]
    body = ET.SubElement(root, N + "g", {"transform": original.get("transform", "")})
    phase = 2 * pi * index / FRAMES
    strength = {"happy": 1.25, "waiting": 1.0, "hungry": .55, "sad": .45}[mood]
    head = ET.SubElement(body, N + "g", {"transform":
        f"translate(0 {(-2.8 * strength * sin(phase)):.2f}) rotate({(4 * strength * sin(phase)):.2f} 252 270)"})
    children = list(original)
    blink_indices = {"happy": {2, 3, 4, 5}, "waiting": {2, 3, 4, 5}, "sad": {3, 4, 5, 6}}
    blinking = index == 9 and mood in blink_indices
    for i, child in enumerate(children[:-1]):
        if blinking and i in blink_indices[mood]:
            if i == min(blink_indices[mood]):
                ET.SubElement(head, N + "path", {"d": "M214 219 Q224 228 234 219 M271 219 Q281 228 291 219", "fill": "none", "stroke": "#493552", "stroke-width": "5", "stroke-linecap": "round"})
            continue
        head.append(deepcopy(child))
    arm = children[-1]
    left, right = arm.get("d").split(" M", 1)
    for path, anchor, offset in [(left, (184, 269), 0), ("M" + right, (318, 267), 2)]:
        attrs = dict(arm.attrib)
        attrs["d"] = path
        rotation = 8 * strength * sin(phase + offset)
        attrs["transform"] = f"rotate({rotation:.2f} {anchor[0]} {anchor[1]})"
        ET.SubElement(body, N + "path", attrs)
    # The book is stationary; the mood mark floats independently above it.
    for child in list(card)[3:9]:
        root.append(deepcopy(child))
    symbol = ET.SubElement(root, N + "g", {"transform":
        f"translate(0 {(-4 * sin(phase + 1)):.2f}) rotate({(5 * sin(phase + 1)):.2f} 356 183)"})
    for child in list(card)[9:]:
        if child.tag == N + "rect":
            break
        symbol.append(deepcopy(child))
    return ET.tostring(root, encoding="unicode")


def render(item):
    mood, index = item
    with tempfile.TemporaryDirectory() as temp:
        svg = Path(temp) / "frame.svg"
        svg.write_text(frame(mood, index), encoding="utf-8")
        png = OUTPUT / f"book_spirit_{mood}_idle_{index:02d}.png"
        subprocess.run(["inkscape", str(svg), "--export-type=png", f"--export-filename={png}", "--export-width=320", "--export-height=320"], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


if __name__ == "__main__":
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(render, ((mood, i) for mood in MOODS for i in range(FRAMES))))
    print(f"Rendered {len(MOODS) * FRAMES} companion frames.")
