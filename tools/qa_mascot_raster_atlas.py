"""Phase 1B: verify a layer atlas against the approved neutral raster.

Produces full-resolution / 160px comparisons on light and dark backgrounds and a
pixel-difference heatmap. No pass here substitutes for human art approval.
The script uses Pillow and the existing raster-rig format=2 manifest.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw


def composite_atlas(manifest: Path) -> Image.Image:
    doc = json.loads(manifest.read_text(encoding="utf-8"))
    if doc.get("format") != 2 or doc.get("kind") != "raster-rig":
        raise ValueError("Expected raster-rig format 2")
    if doc.get("canvas") != [320, 320]:
        raise ValueError("Expected the 320x320 logical canvas")
    scale = doc.get("pixels_per_unit", 1)
    if not isinstance(scale, (int, float)) or not 1 <= scale <= 4:
        raise ValueError("Unsupported pixel density")
    pixels = round(320 * scale)
    sprites = doc.get("sprites", [])
    if len(sprites) < 2:
        raise ValueError("This is not a multi-layer rig; at least two sprites required")
    images = []
    for atlas in doc.get("atlases", []):
        filename = atlas["file"]
        if not filename.startswith("texture-") or not filename.endswith(".webp") or "/" in filename or ".." in filename:
            raise ValueError("Unsafe atlas path")
        with Image.open(manifest.parent / filename) as img:
            rgba = img.convert("RGBA")
        if list(rgba.size) != [atlas["width"], atlas["height"]]:
            raise ValueError("Atlas dimensions mismatch")
        images.append(rgba)
    if not images:
        raise ValueError("Missing atlas bitmaps")
    output = Image.new("RGBA", (pixels, pixels))
    for item in sorted(sprites, key=lambda part: part["z"]):
        atlas = images[item["atlas"]]
        x, y, w, h = item["src"]
        if min(x, y) < 0 or min(w, h) <= 0 or x + w > atlas.width or y + h > atlas.height:
            raise ValueError("Sprite rectangle outside its atlas")
        tile = atlas.crop((x, y, x + w, y + h))
        rendered_size = tuple(round(v * scale) for v in item.get("size", [w / scale, h / scale]))
        if rendered_size[0] <= 0 or rendered_size[1] <= 0:
            raise ValueError("Non-positive logical sprite size")
        if tile.size != rendered_size:
            tile = tile.resize(rendered_size, Image.Resampling.LANCZOS)
        px, py = (round(v * scale) for v in item["position"])
        output.alpha_composite(tile, (px, py))
    return output


def composite_bg(img: Image.Image, color: tuple[int, int, int]) -> Image.Image:
    backdrop = Image.new("RGBA", img.size, (*color, 255))
    backdrop.alpha_composite(img)
    return backdrop.convert("RGB")


def metrics(reference: Image.Image, reconstructed: Image.Image) -> dict:
    if reference.size != reconstructed.size:
        raise ValueError("Reference and atlas canvas dimensions differ")
    original, exported = reference.convert("RGBA"), reconstructed.convert("RGBA")
    diff_alpha = ImageChops.difference(original.getchannel("A"), exported.getchannel("A"))
    alpha_changed = sum(count for i, count in enumerate(diff_alpha.histogram()) if i != 0)
    channel_errors = []
    for background in ((255, 255, 255), (16, 19, 17)):
        delta = ImageChops.difference(composite_bg(original, background), composite_bg(exported, background))
        channel_errors.append(max(max(channel.getextrema()) for channel in delta.split()))
    exact = alpha_changed == 0 and max(channel_errors) == 0
    return {
        "neutral_pixel_exact_on_light_and_dark": exact,
        "alpha_changed_pixels": alpha_changed,
        "maximum_visible_color_delta_8bit": max(channel_errors),
        "light_background_max_delta": channel_errors[0],
        "dark_background_max_delta": channel_errors[1],
        "reference_size": list(reference.size),
        "human_likeness_approval": False,
    }


def write_report(manifest: Path, reference_file: Path, output_dir: Path, strict: bool = False) -> dict:
    output_dir.mkdir(parents=True, exist_ok=True)
    generated = composite_atlas(manifest)
    with Image.open(reference_file) as original:
        reference = original.convert("RGBA")
    status = metrics(reference, generated)
    generated.save(output_dir / "neutral-from-atlas.png")
    diff_light = ImageChops.difference(
        composite_bg(reference, (255, 255, 255)),
        composite_bg(generated, (255, 255, 255))
    )
    diff_dark = ImageChops.difference(
        composite_bg(reference, (16, 19, 17)),
        composite_bg(generated, (16, 19, 17))
    )
    heat = ImageChops.lighter(diff_light, diff_dark).convert("L").point(lambda value: min(255, value * 8))
    overlay = Image.new("RGB", reference.size, (30, 32, 31))
    highlight = Image.new("RGB", reference.size, (255, 83, 64))
    overlay.paste(highlight, (0, 0), heat)
    overlay.save(output_dir / "difference-heatmap.png", optimize=True)
    cols = [reference, generated]
    contact = Image.new("RGB", (160 * 3, 160 * 2 + 50), (242, 239, 232))
    draw = ImageDraw.Draw(contact)
    for row, bg in enumerate([(255, 255, 255), (16, 19, 17)]):
        for col, img in enumerate(cols):
            thumb = composite_bg(img, bg).resize((160, 160), Image.Resampling.LANCZOS)
            contact.paste(thumb, (160 * col, 30 + 160 * row))
        difference = ImageChops.difference(composite_bg(reference, bg), composite_bg(generated, bg))
        difference = difference.resize((160, 160), Image.Resampling.NEAREST)
        enhanced = difference.point(lambda value: min(255, value * 12))
        contact.paste(enhanced, (320, 30 + 160 * row))
    for col, name in enumerate(("Reference", "Reassembled", "Difference x12")):
        draw.text((8 + col * 160, 8), name, fill=(34, 42, 36))
    contact.save(output_dir / "quality-contact-160.png", optimize=True)
    status.update({"manifest": str(manifest), "reference": str(reference_file), "strict": strict})
    (output_dir / "quality-report.json").write_text(json.dumps(status, indent=2) + "\n", encoding="utf-8")
    if strict and not status["neutral_pixel_exact_on_light_and_dark"]:
        raise ValueError("Atlas does not recompose neutral artwork exactly; inspect quality-contact-160.png")
    return status


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()
    print(json.dumps(write_report(args.manifest, args.reference, args.output, args.strict), indent=2))
