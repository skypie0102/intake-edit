"""Prepare faithful raster preproduction assets from the approved portrait.

No synthetic expressions, rig parts or animation frames are created. Keep the
original pixels and alpha; isolate the character from the detached speech bubble.
Requires Pillow.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import math
from io import BytesIO
from zipfile import ZipFile, ZIP_STORED, ZIP_DEFLATED
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw, ImageStat

SOURCE_SHA256 = "57b9a2f264769656eaf39e58e0169cdb41d7c700ea93b535272b4e9efa2c0ce1"
FRAME_TYPES = ("full", "bust")
CANVAS_SIZES = (160, 320)

def isolate_character(source: Image.Image, seed: tuple[int, int] = (300, 450)) -> Image.Image:
    """Keep the alpha-connected character; preserve all original visible RGB."""
    if source.mode != "RGBA":
        raise ValueError("Approved source must contain its original RGBA transparency")
    alpha = source.getchannel("A")
    binary = alpha.point(lambda value: 255 if value > 2 else 0)
    if binary.getpixel(seed) != 255:
        raise ValueError("Character seed must be on opaque character artwork")
    ImageDraw.floodfill(binary, seed, value=128, thresh=0)
    subject = binary.point(lambda value: 255 if value == 128 else 0)
    clean = source.copy()
    clean.putalpha(ImageChops.multiply(alpha, subject))
    if not clean.getbbox():
        raise ValueError("Character alpha mask is empty")
    return clean

def render_square(isolated: Image.Image, style: str, edge: int) -> Image.Image:
    if style not in FRAME_TYPES or edge not in CANVAS_SIZES:
        raise ValueError("Unsupported static reference view")
    bbox = isolated.getbbox()
    if bbox is None:
        raise ValueError("Empty character")
    x0, y0, x1, y1 = bbox
    if style == "full":
        crop = (max(x0 - 6, 0), max(y0 - 6, 0),
                min(x1 + 6, isolated.width), min(y1 + 6, isolated.height))
    else:
        # Non-destructive framing: a crop of the unchanged original illustration.
        crop = (max(x0 - 9, 0), max(y0 - 8, 0),
                min(x1 + 9, isolated.width), min(y0 + 545, isolated.height))
    framed = isolated.crop(crop)
    maximum = edge - 12
    ratio = min(maximum / framed.width, maximum / framed.height)
    reduced = framed.resize(
        (round(framed.width * ratio), round(framed.height * ratio)),
        Image.Resampling.LANCZOS
    )
    canvas = Image.new("RGBA", (edge, edge))
    canvas.alpha_composite(reduced, ((edge - reduced.width) // 2,
                                     (edge - reduced.height) // 2))
    return canvas

def opaque_psnr(reference: Image.Image, result: Image.Image) -> float:
    """Measure visible RGB, ignoring meaningless colors under transparent pixels."""
    if reference.size != result.size:
        raise ValueError("Different image dimensions")
    mask = reference.getchannel("A").point(lambda v: 255 if v >= 128 else 0)
    error = ImageChops.difference(reference.convert("RGB"), result.convert("RGB"))
    stat = ImageStat.Stat(error, mask=mask)
    mse = sum(rms * rms for rms in stat.rms) / 3
    return float("inf") if mse == 0 else 10 * math.log10(255 * 255 / mse)

def save_openraster(image: Image.Image, path: Path) -> None:
    """Save an exact one-layer editable art starting point, NOT a finished rig."""
    xml = (
        f'<image version="0.0.3" w="{image.width}" h="{image.height}" '
        'name="Mascot V2 - faithful neutral source">'
        '<stack><layer name="Original illustrated character (not rigged)" '
        'src="data/neutral.png" x="0" y="0" opacity="1.0" '
        'visibility="visible" composite-op="svg:src-over"/></stack></image>'
    )
    buffer = BytesIO()
    image.save(buffer, "PNG")
    with ZipFile(path, "w") as archive:
        archive.writestr("mimetype", "image/openraster", compress_type=ZIP_STORED)
        archive.writestr("stack.xml", xml, compress_type=ZIP_DEFLATED)
        archive.writestr("data/neutral.png", buffer.getvalue(), compress_type=ZIP_DEFLATED)
        archive.writestr("mergedimage.png", buffer.getvalue(), compress_type=ZIP_DEFLATED)


def build(source: Path, output: Path, quality: int = 95) -> dict:
    if not 85 <= quality <= 100:
        raise ValueError("Character artwork requires quality in [85, 100]")
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    if digest != SOURCE_SHA256:
        raise ValueError("Source hash mismatch: use the untouched original portrait")
    with Image.open(source) as raw:
        isolated = isolate_character(raw)
    output.mkdir(parents=True, exist_ok=True)
    isolated.save(output / "neutral-isolated-lossless.png", optimize=True)
    save_openraster(isolated, output / "neutral-master.ora")
    manifest = {
        "format": 2, "id": "mascot-v2", "kind": "raster-preproduction",
        "status": "reference-only", "source_sha256": digest,
        "source_size": list(isolated.size), "subject_bbox": list(isolated.getbbox()),
        "rig_ready": False, "animation_ready": False, "layers": [], "frames": [],
        "note": "Original full-color image component only; no articulated layers or animation yet."
    }
    for frame in FRAME_TYPES:
        for edge in CANVAS_SIZES:
            image = render_square(isolated, frame, edge)
            name = f"neutral-{frame}-{edge}.webp"
            dest = output / name
            image.save(dest, "WEBP", quality=quality, method=6, exact=True)
            decoded = Image.open(dest).convert("RGBA")
            psnr = opaque_psnr(image, decoded)
            alpha_diff = ImageChops.difference(image.getchannel("A"), decoded.getchannel("A")).getbbox()
            if psnr < 37 or alpha_diff is not None:
                raise ValueError(f"Fidelity validation failed: {name}, PSNR {psnr:.1f}")
            manifest["frames"].append({
                "name": frame, "width": edge, "height": edge,
                "file": name, "bytes": dest.stat().st_size,
                "visible_rgb_psnr_db": round(psnr, 2),
                "alpha_exact": True, "static_reference_only": True
            })
    (output / "preview-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return manifest

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--portrait", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--quality", type=int, default=95)
    args = parser.parse_args()
    print(json.dumps(build(args.portrait, args.output, args.quality), indent=2))

if __name__ == "__main__":
    main()
