"""Convert the supported SVG subset used by the companion rig to Android vectors."""
import re
import xml.etree.ElementTree as ET
SVG = "http://www.w3.org/2000/svg"
N = "{" + SVG + "}"
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)
A = "{" + ANDROID + "}"


def svg_to_vector(svg_text):
    """Convert the deliberately small SVG subset used by the companion artwork."""
    source = ET.fromstring(svg_text)
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
        if element.get("clip-path"):
            if element.get("clip-path") != "url(#behind-book)":
                raise ValueError("Unsupported clip path")
            parent = ET.SubElement(parent, "group")
            ET.SubElement(parent, "clip-path", {A + "pathData": "M0,0 H504 V320 H0 Z"})
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


