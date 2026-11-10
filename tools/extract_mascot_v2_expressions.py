"""Extract the 12 approved chibi Slytherin expression references exactly.

These are source art studies, not riggable faces, transparency masks or
production animation sprites. Crops contain parts of their original panels.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw

EXPECTED_SHA256 = "f89e7d8ea51ad8131994bf3a40660329e01d6839977a54c3157536f206b9e5f6"
NAMES = (
    "neutral", "hmph", "annoyed", "surprised",
    "thinking", "suspicious", "flustered", "angry",
    "embarrassed", "tired", "satisfied", "sulky",
)
COLUMNS = ((990, 1106), (1147, 1263), (1275, 1387), (1397, 1508))
ROWS = ((119, 237), (268, 387), (421, 538))


def extract(sheet: Path, destination: Path) -> dict:
    digest = hashlib.sha256(sheet.read_bytes()).hexdigest()
    if digest != EXPECTED_SHA256:
        raise ValueError("Reference sheet checksum mismatch; use the original approved master")
    with Image.open(sheet) as source:
        if source.size != (1536, 1024):
            raise ValueError("Unexpected sheet dimensions")
        original = source.convert("RGB")
    destination.mkdir(parents=True, exist_ok=True)
    atlas = Image.new("RGB", (4 * 128, 3 * 128), (250, 247, 240))
    board = Image.new("RGB", (4 * 180, 3 * 210), (248, 246, 241))
    text = ImageDraw.Draw(board)
    data = []
    for i, name in enumerate(NAMES):
        row, col = divmod(i, 4)
        box = (*COLUMNS[col][:1], ROWS[row][0], COLUMNS[col][1], ROWS[row][1])
        tile = original.crop(box)
        image_file = f"{name}.webp"
        tile.save(destination / image_file, "WEBP", lossless=True, method=6)
        with Image.open(destination / image_file) as exported:
            if exported.convert("RGB").tobytes() != tile.tobytes():
                raise AssertionError(f"Lossless artwork comparison failed: {name}")
        atlas_tile = tile.resize((120, 120), Image.Resampling.LANCZOS)
        atlas.paste(atlas_tile, (col * 128 + 4, row * 128 + 4))
        large = tile.resize((168, 168), Image.Resampling.LANCZOS)
        board.paste(large, (col * 180 + 6, row * 210 + 8))
        text.text((col * 180 + 7, row * 210 + 184), name, fill=(35, 50, 42))
        data.append({
            "name": name,
            "crop": list(box),
            "image": image_file,
            "source_dimensions": list(tile.size),
            "source": "approved_slytherin_reference_sheet",
            "rig_layer": False,
            "approved_for_animation": False,
            "atlas_cell": [col * 128 + 4, row * 128 + 4, 120, 120],
        })
    atlas.save(destination / "expression-reference-atlas.webp", "WEBP", lossless=True, method=6)
    board.save(destination / "expression-contact.png", optimize=True)
    metadata = {
        "format": 1, "type": "expression-reference-only", "source_sha256": EXPECTED_SHA256,
        "pixel_exact_unscaled_crops": True, "atlas_preview_resampled": True,
        "count": len(data), "entries": data,
        "rig_ready": False, "animation_ready": False,
        "note": "12 approved reference drawings, not transparent eye/brow or mouth layers.",
    }
    (destination / "reference-manifest.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    return metadata


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sheet", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = extract(args.sheet, args.output)
    print(json.dumps({"count": report["count"], "master_sha256": report["source_sha256"],
                      "status": "reference-only"}, indent=2))
