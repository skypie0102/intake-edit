"""Convert the editable companion SVG into native Android vector frames."""
from copy import deepcopy
from math import sin, pi
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/assets/companion/book_spirit_moods.svg"
OUTPUT = ROOT / "app/src/main/res/drawable"
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


ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)
A = "{" + ANDROID + "}"


def vector_frame(mood, index):
    """Convert the deliberately small SVG subset used by the companion artwork."""
    source = ET.fromstring(frame(mood, index))
    root = ET.Element("vector", {A + "width": "320dp", A + "height": "320dp",
                                 A + "viewportWidth": "310", A + "viewportHeight": "310"})
    canvas = ET.SubElement(root, "group", {A + "translateX": "-97", A + "translateY": "-92"})

    def color(value):
        if value == "white":
            return "#ffffff"
        if not re.fullmatch(r"#[0-9a-fA-F]{6}", value):
            raise ValueError(f"Unsupported SVG color: {value}")
        return value

    def transforms(parent, spec):
        rest = spec.strip()
        while rest:
            match = re.match(r"(translate|rotate)\(([^)]*)\)\s*", rest)
            if not match:
                raise ValueError(f"Unsupported SVG transform: {rest}")
            values = [float(x) for x in re.split(r"[ ,]+", match.group(2).strip())]
            if match.group(1) == "translate" and len(values) in (1, 2):
                attrs = {A + "translateX": str(values[0]), A + "translateY": str(values[1] if len(values) == 2 else 0)}
            elif match.group(1) == "rotate" and len(values) == 3:
                attrs = {A + "rotation": str(values[0]), A + "pivotX": str(values[1]), A + "pivotY": str(values[2])}
            else:
                raise ValueError(f"Unsupported SVG transform: {match.group(0)}")
            parent = ET.SubElement(parent, "group", attrs)
            rest = rest[match.end():]
        return parent

    def append_element(element, parent, inherited_alpha=1.0):
        name = element.tag.removeprefix(N)
        if name in ("defs", "style"):
            return
        parent = transforms(parent, element.get("transform", ""))
        opacity = inherited_alpha * float(element.get("opacity", "1"))
        if name == "g":
            for child in element:
                append_element(child, parent, opacity)
            return
        if name == "path":
            path_data = element.get("d")
        elif name in ("circle", "ellipse"):
            x, y = float(element.get("cx")), float(element.get("cy"))
            rx = float(element.get("r") if name == "circle" else element.get("rx"))
            ry = float(element.get("r") if name == "circle" else element.get("ry"))
            path_data = f"M{x-rx},{y} a{rx},{ry} 0 1,0 {rx*2},0 a{rx},{ry} 0 1,0 {-rx*2},0 Z"
        else:
            raise ValueError(f"Unsupported SVG element: {name}")
        line = element.get("class") == "line"
        fill = element.get("fill", "none" if line else "#000000")
        attrs = {A + "pathData": path_data}
        fill_alpha = opacity * float(element.get("fill-opacity", "1"))
        if fill not in ("none", "url(#paper)"):
            attrs[A + "fillColor"] = color(fill)
        if fill != "none" and fill_alpha != 1:
            attrs[A + "fillAlpha"] = str(fill_alpha)
        stroke = element.get("stroke")
        if stroke and stroke != "none":
            attrs[A + "strokeColor"] = color(stroke)
            attrs[A + "strokeWidth"] = element.get("stroke-width", "1")
            if opacity != 1:
                attrs[A + "strokeAlpha"] = str(opacity)
            if line or element.get("stroke-linecap") == "round":
                attrs[A + "strokeLineCap"] = "round"
            if line or element.get("stroke-linejoin") == "round":
                attrs[A + "strokeLineJoin"] = "round"
        path = ET.SubElement(parent, "path", attrs)
        if fill == "url(#paper)":
            attr = ET.SubElement(path, "{" + AAPT + "}attr", {"name": "android:fillColor"})
            ET.SubElement(attr, "gradient", {A + "type": "linear", A + "startX": "0", A + "startY": "275",
                                           A + "endX": "0", A + "endY": "369",
                                           A + "startColor": "#fff6df", A + "endColor": "#d8c5ad"})

    for element in source:
        append_element(element, canvas)
    return ET.tostring(root, encoding="unicode")


def render(item):
    mood, index = item
    destination = OUTPUT / f"book_spirit_{mood}_idle_{index:02d}.xml"
    destination.write_text(vector_frame(mood, index), encoding="utf-8")


if __name__ == "__main__":
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for mood in MOODS:
        for index in range(FRAMES):
            render((mood, index))
    print(f"Rendered {len(MOODS) * FRAMES} companion frames.")
