"""Experimental Phase 1B eye-layer study from the SHA-pinned source portrait.

Art PREPRODUCTION only. Two actual original-pixel eye cutouts and a temporarily
inpainted flat base; this is NOT a finished face/rig, and is never consumed by
the production app. Requires Pillow, numpy and opencv-python-headless.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from io import BytesIO
from pathlib import Path
from xml.etree import ElementTree as ET
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile

import cv2
import numpy as np
from PIL import Image, ImageChops, ImageDraw

SOURCE_SHA256 = "57b9a2f264769656eaf39e58e0169cdb41d7c700ea93b535272b4e9efa2c0ce1"
# Hand-positioned *study* masks in original 667x1000 coordinates.
# These are provisional, not approved eyelid/skin contours.
EYES = {
    "eye_left": [(181,274),(191,273),(213,278),(231,289),(233,303),
                 (223,315),(205,320),(191,318),(182,305),(179,287)],
    "eye_right": [(250,274),(264,262),(288,253),(322,250),(349,246),
                  (354,253),(351,276),(342,294),(329,300),(299,303),
                  (271,299),(251,289)],
}


def isolated_art(portrait: Path) -> Image.Image:
    if hashlib.sha256(portrait.read_bytes()).hexdigest() != SOURCE_SHA256:
        raise ValueError("Source does not match the approved original portrait SHA-256")
    with Image.open(portrait) as src:
        img = src.convert("RGBA")
    if img.size != (667, 1000):
        raise ValueError("Wrong portrait dimensions")
    pixels = np.array(img)
    alpha = pixels[:, :, 3]
    _, component, _, _ = cv2.connectedComponentsWithStats((alpha > 2).astype("uint8"), 8)
    label = component[450, 300]
    if label == 0:
        raise ValueError("Portrait source seed is not on the character")
    pixels[:, :, 3] = np.where(component == label, alpha, 0).astype("uint8")
    return Image.fromarray(pixels, "RGBA")


def framed_art(art: Image.Image) -> tuple[Image.Image, dict]:
    bbox = art.getbbox()
    x0, y0, x1, y1 = bbox
    factor = min(604 / (x1 - x0), 604 / (y1 - y0))
    width, height = round((x1 - x0) * factor), round((y1 - y0) * factor)
    cx, cy = (640 - width) // 2, (640 - height) // 2
    out = Image.new("RGBA", (640, 640))
    out.alpha_composite(art.crop(bbox).resize((width, height), Image.Resampling.LANCZOS), (cx, cy))
    return out, {"bbox": bbox, "factor": factor, "origin": (cx, cy)}


def save_openraster(source: Image.Image, base: Image.Image,
                    eyes: dict[str, Image.Image], output: Path) -> None:
    root = ET.Element("image", {"version": "0.0.3", "w": "640", "h": "640",
                                "name": "Mascot V2 experimental eye split (unapproved)"})
    stack = ET.SubElement(root, "stack")
    merged = base.copy()
    for eye in eyes.values():
        merged.alpha_composite(eye)
    # OpenRaster stack order is top-to-bottom.
    ordered = [
        ("Right eye / original pixels", eyes["eye_right"], True),
        ("Left eye / original pixels", eyes["eye_left"], True),
        ("Base / algorithmic underpaint, needs artist", base, True),
        ("Unaltered art reference (hidden)", source, False),
    ]
    with ZipFile(output, "w") as archive:
        archive.writestr("mimetype", "image/openraster", compress_type=ZIP_STORED)
        for i, (name, image, visible) in enumerate(ordered):
            filename = f"data/layer-{i}.png"
            ET.SubElement(stack, "layer", {
                "name": name, "src": filename, "x": "0", "y": "0", "opacity": "1.0",
                "visibility": "visible" if visible else "hidden",
                "composite-op": "svg:src-over",
            })
            mem = BytesIO()
            image.save(mem, "PNG")
            archive.writestr(filename, mem.getvalue(), compress_type=ZIP_DEFLATED)
        archive.writestr("stack.xml", ET.tostring(root, encoding="utf-8",
                                                  xml_declaration=True))
        mem = BytesIO()
        merged.save(mem, "PNG")
        archive.writestr("mergedimage.png", mem.getvalue(), compress_type=ZIP_DEFLATED)


def generate(portrait: Path, destination: Path) -> dict:
    art, transform = framed_art(isolated_art(portrait))
    destination.mkdir(parents=True, exist_ok=True)
    eye_images = {}
    union_mask = Image.new("L", (640, 640))
    masks = {}
    for name, points in EYES.items():
        x0, y0, _, _ = transform["bbox"]
        factor = transform["factor"]
        cx, cy = transform["origin"]
        polygon = [(round(cx + (x - x0) * factor), round(cy + (y - y0) * factor))
                   for x, y in points]
        mask = Image.new("L", art.size)
        ImageDraw.Draw(mask).polygon(polygon, fill=255)
        masks[name] = mask
        union_mask = ImageChops.lighter(union_mask, mask)
        rgba = np.array(art)
        rgba[:, :, 3] = (rgba[:, :, 3].astype("uint16") *
                         np.array(mask).astype("uint16") // 255).astype("uint8")
        rgba[rgba[:, :, 3] == 0, :3] = 0  # transparent pixels compress cheaply
        eye_images[name] = Image.fromarray(rgba, "RGBA")
    original = np.array(art)
    bgr = cv2.cvtColor(original[:, :, :3], cv2.COLOR_RGB2BGR)
    # Deliberately provisional: Telea does not paint anatomically correct eyelids.
    patched = cv2.cvtColor(cv2.inpaint(bgr, np.array(union_mask), 8,
                                      cv2.INPAINT_TELEA), cv2.COLOR_BGR2RGB)
    underneath = original.copy()
    covered = np.array(union_mask) > 0
    underneath[covered, :3] = patched[covered]
    base = Image.fromarray(underneath, "RGBA")
    composed = base.copy()
    for eye in eye_images.values():
        composed.alpha_composite(eye)
    if not np.array_equal(np.asarray(art), np.asarray(composed)):
        raise AssertionError("Neutral artwork must recompose pixel-exactly")
    art.save(destination / "neutral-reference.png", optimize=True)
    base.save(destination / "face-underpaint.png", optimize=True)
    for name, image in eye_images.items():
        image.save(destination / f"{name}.png", optimize=True)
    composed.save(destination / "neutral-recomposed.png", optimize=True)
    save_openraster(art, base, eye_images, destination / "eye-pilot.ora")
    metadata = {
        "format": 2, "phase": "1B-eye-only-experiment",
        "source_sha256": SOURCE_SHA256, "canvas": [320, 320],
        "pixels_per_unit": 2,
        "neutral_rgba_recomposition_exact": True,
        "status": "unapproved", "rig_ready": False,
        "animation_ready": False,
        "layers": ["face-underpaint.png", "eye_left.png", "eye_right.png"],
        "limitations": [
            "Only two eyes independent; remaining body remains flat",
            "Underpaint algorithmic, not artist-painted",
            "Motion reveals cut seams; no approved blink",
            "No crest replacement or production app/widget integration",
        ],
    }
    (destination / "pilot-manifest.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    return metadata


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--portrait", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    generate(args.portrait, args.output)
