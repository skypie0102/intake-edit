"""Pack actual separately illustrated RGBA character layers into one lossless WebP atlas.

This tool DOES NOT split a flat image into fake puppet parts. Supply real
artist-authored transparent PNG/WebP layers; each entry defines a draw position
and joint pivot in the 320x320 logical canvas. Pillow required.
"""
from __future__ import annotations
import argparse
import json
import re
from pathlib import Path
from PIL import Image

ID = re.compile(r"^[a-z][a-z0-9_]*$")

def pack(manifest_path: Path, output: Path, width: int = 1024,
         max_height: int = 2048, max_bytes: int = 5_000_000) -> dict:
    if width < 64 or width > 2048 or width & (width - 1):
        raise ValueError("Atlas width must be a power of two from 64..2048")
    source = json.loads(manifest_path.read_text("utf-8"))
    if source.get("format") != 2 or source.get("canvas") != [320, 320]:
        raise ValueError("Input must be raster rig manifest v2 on 320x320")
    entries = source.get("layers", [])
    if len(entries) < 2:
        raise ValueError("Rig pack requires at least two painted layers; one flat image is reference only")
    if len(entries) > 128:
        raise ValueError("Too many layers")
    layers = []
    seen = set()
    for entry in entries:
        part_id = entry["id"]
        if not ID.fullmatch(part_id) or part_id in seen:
            raise ValueError(f"Invalid/duplicate layer ID: {part_id}")
        seen.add(part_id)
        filename = Path(entry["file"])
        if filename.is_absolute() or ".." in filename.parts:
            raise ValueError("Layer filename must be safe and relative")
        with Image.open(manifest_path.parent / filename) as raw:
            image = raw.convert("RGBA")
        if not (1 <= image.width <= 2048 and 1 <= image.height <= 2048):
            raise ValueError("Invalid layer dimensions")
        bounding = image.getchannel("A").getbbox()
        if bounding is None:
            raise ValueError(f"Empty layer: {part_id}")
        position, pivot = entry["position"], entry["pivot"]
        if len(position) != 2 or len(pivot) != 2:
            raise ValueError("Position/pivot must be 2D")
        if not all(isinstance(v, (int, float)) and -4096 < v < 4096 for v in [*position, *pivot]):
            raise ValueError("Invalid position/pivot")
        layers.append({
            "id": part_id, "image": image.crop(bounding),
            "offset": [float(position[0] + bounding[0]), float(position[1] + bounding[1])],
            "pivot": [float(pivot[0]), float(pivot[1])],
            "z": int(entry["z"]), "parent": entry.get("parent")
        })
    if len({x["z"] for x in layers}) != len(layers):
        raise ValueError("Layer z indices must be unique")
    for layer in layers:
        if layer["parent"] is not None and layer["parent"] not in seen:
            raise ValueError(f"Unknown layer parent {layer['parent']}")
    layers.sort(key=lambda layer: layer["z"])
    x = y = row_height = 0
    placements = []
    gap = 4
    for layer in layers:
        w, h = layer["image"].size
        if w + gap * 2 > width:
            raise ValueError(f"Layer too wide: {layer['id']}")
        if x + w + gap > width:
            x = 0
            y += row_height
            row_height = 0
        if y + h + 2 * gap > max_height:
            raise ValueError("Atlas would exceed maximum height")
        placements.append((x + gap, y + gap))
        x += w + 2 * gap
        row_height = max(row_height, h + 2 * gap)
    used_height = y + row_height
    height = 64
    while height < used_height:
        height *= 2
    if height > max_height:
        raise ValueError("Atlas height exceeds budget")
    atlas = Image.new("RGBA", (width, height))
    exports = []
    for layer, (px, py) in zip(layers, placements):
        image = layer["image"]
        atlas.alpha_composite(image, (px, py))
        exports.append({
            "id": layer["id"], "atlas": 0,
            "src": [px, py, image.width, image.height],
            "position": layer["offset"], "pivot": layer["pivot"],
            "parent": layer["parent"], "z": layer["z"]
        })
    output.mkdir(parents=True, exist_ok=True)
    texture = output / "texture-0.webp"
    atlas.save(texture, "WEBP", lossless=True, method=6, exact=True)
    if texture.stat().st_size > max_bytes:
        texture.unlink()
        raise ValueError(f"Atlas exceeds {max_bytes} byte budget")
    result = {
        "format": 2, "kind": "raster-rig", "canvas": [320, 320],
        "atlases": [{"file": texture.name, "width": width, "height": height,
                     "bytes": texture.stat().st_size}],
        "sprites": exports, "animated": False,
        "note": "Lossless illustrated layers; pose/animation keyframes supplied separately after approval."
    }
    (output / "atlas-manifest.json").write_text(json.dumps(result, indent=2) + "\n")
    return result

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--layers", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--width", type=int, default=1024)
    a = parser.parse_args()
    result = pack(a.layers, a.output, a.width)
    print(json.dumps({"count": len(result["sprites"]), "atlas": result["atlases"][0]}, indent=2))

if __name__ == "__main__":
    main()
