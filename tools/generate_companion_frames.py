"""Render the companion's independent vector parts into lightweight widget frames.

Requires Inkscape and ImageMagick with WebP support. The checked-in SVG remains
the editable source of truth.
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
FRAMES = 24


def frame(mood, index):
    source = ET.parse(SOURCE).getroot()
    card = source[MOODS[mood]]
    root = ET.Element(N + "svg", {"width": "320", "height": "320", "viewBox": "97 92 310 310"})
    root.append(deepcopy(source[0]))
    original = card[2]
    body = ET.SubElement(root, N + "g", {"transform": original.get("transform", "")})
    action, step = divmod(index, 6)
    phase = 2 * pi * step / 6
    action_amount = (0, .35, .8, 1, .65, .2)[step]
    strength = {"happy": 1.25, "waiting": 1.0, "hungry": .55, "sad": .45}[mood]
    head = ET.SubElement(body, N + "g", {"transform":
        f"translate(0 {(-2.8 * strength * sin(phase)):.2f}) rotate({(4 * strength * sin(phase)):.2f} 252 270)"})
    children = list(original)
    blink_indices = {"happy": {2, 3, 4, 5}, "waiting": {2, 3, 4, 5}, "sad": {3, 4, 5, 6}}
    blinking = index == 4 and mood in blink_indices
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
        if action == 1 and offset == 2:
            attrs["opacity"] = f"{1 - action_amount:.2f}"
        ET.SubElement(body, N + "path", attrs)
    if action == 1:  # Hand raised to the chin while thinking.
        ET.SubElement(body, N + "path", {"d": "M303 270 C291 265 283 259 278 249 C275 243 270 240 265 242 C259 245 261 251 264 257 C270 268 281 276 294 281 Z", "fill": "#dfd2f1", "stroke": "#705c87", "stroke-width": "4", "stroke-linejoin": "round", "opacity": f"{action_amount:.2f}"})
    # The book is stationary; the mood mark floats independently above it.
    for child in list(card)[3:9]:
        root.append(deepcopy(child))
    if action == 2 and action_amount > 0:  # A raised page folds towards the spine.
        tip_x = 389 - 120 * action_amount
        tip_y = 282 - 65 * sin(pi * .75 * action_amount)
        ET.SubElement(root, N + "path", {"d": f"M253 300 Q{tip_x - 18:.1f} {tip_y + 25:.1f} {tip_x:.1f} {tip_y:.1f} Q{tip_x + 48:.1f} {tip_y - 8:.1f} 389 282 Q320 261 253 300 Z", "fill": "#fff5df", "stroke": "#ac8f87", "stroke-width": "3", "stroke-linejoin": "round"})
        ET.SubElement(root, N + "path", {"d": f"M253 300 Q{(tip_x + 253) / 2:.1f} {tip_y + 12:.1f} {tip_x:.1f} {tip_y:.1f}", "fill": "none", "stroke": "#cdb9aa", "stroke-width": "2"})
    if action == 3 and action_amount > 0:  # A small hand guides a pen across the page.
        x = 205 + 24 * action_amount
        y = 321 - 5 * action_amount
        ET.SubElement(root, N + "path", {"d": f"M{x:.1f} {y:.1f} L{x + 19:.1f} {y - 29:.1f}", "fill": "none", "stroke": "#70506a", "stroke-width": "4", "stroke-linecap": "round", "opacity": f"{min(1, action_amount * 3):.2f}"})
        ET.SubElement(root, N + "path", {"d": f"M184 271 Q187 286 {x + 6:.1f} {y - 13:.1f} Q{x + 13:.1f} {y - 7:.1f} {x + 20:.1f} {y - 15:.1f} Q{x + 20:.1f} {y - 24:.1f} {x + 12:.1f} {y - 24:.1f} Q197 280 191 270 Z", "fill": "#dfd2f1", "stroke": "#705c87", "stroke-width": "3", "stroke-linejoin": "round", "opacity": f"{min(1, action_amount * 3):.2f}"})
    symbol = ET.SubElement(root, N + "g", {"transform":
        f"translate(0 {(-4 * sin(phase + 1)):.2f}) rotate({(5 * sin(phase + 1)):.2f} 356 183)"})
    for child in list(card)[9:]:
        if child.tag == N + "rect":
            break
        symbol.append(deepcopy(child))
    if action == 1:
        for x, y, radius in [(335, 205, 2.1), (341, 199, 2.8), (348, 192, 3.6)]:
            ET.SubElement(symbol, N + "circle", {"cx": str(x), "cy": str(y), "r": str(radius), "fill": "#c8aed5", "opacity": f"{action_amount:.2f}"})
    return ET.tostring(root, encoding="unicode")


def render(item):
    mood, index = item
    with tempfile.TemporaryDirectory() as temp:
        svg = Path(temp) / "frame.svg"
        svg.write_text(frame(mood, index), encoding="utf-8")
        png = Path(temp) / "frame.png"
        webp = OUTPUT / f"book_spirit_{mood}_idle_{index:02d}.webp"
        subprocess.run(["inkscape", str(svg), "--export-type=png", f"--export-filename={png}", "--export-width=320", "--export-height=320"], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        subprocess.run(["convert", str(png), "-quality", "90", str(webp)], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


if __name__ == "__main__":
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(render, ((mood, i) for mood in MOODS for i in range(FRAMES))))
    print(f"Rendered {len(MOODS) * FRAMES} companion frames.")
