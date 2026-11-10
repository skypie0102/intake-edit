"""Import exact, unmodified user-provided mascot references into the art source tree.

Usage:
  python tools/import_mascot_v2_references.py \
    --sheet /path/to/slytherin_chibi_character_master_sheet.png \
    --pose /path/to/Chibi_Malfoid_1000.png

The tool verifies SHA-256 *before* writing either file. It does not crop, scale,
flatten, recompress or transform the images. Both references must be present.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import tempfile

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "artwork" / "mascot-v2"
CONTRACT = BASE / "rig-contract.json"
DESTINATION = BASE / "reference"


def import_references(sheet: Path, pose: Path, dry_run: bool = False) -> list[Path]:
    contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
    inputs = {"primary_character_sheet": sheet, "original_character_pose": pose}
    validated: list[tuple[Path, Path]] = []
    for entry in contract["reference_files"]:
        role = entry["role"]
        if role not in inputs:
            raise ValueError(f"Unexpected reference role: {role}")
        incoming = inputs[role].expanduser().resolve(strict=True)
        if not incoming.is_file():
            raise ValueError(f"Not a file: {incoming}")
        actual = hashlib.sha256(incoming.read_bytes()).hexdigest()
        if actual != entry["sha256"]:
            raise ValueError(
                f"{entry['filename']} has wrong SHA-256. Expected {entry['sha256']}, got {actual}. "
                "Use the untouched original supplied by the user."
            )
        target = DESTINATION / entry["filename"]
        if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() != actual:
            raise ValueError(f"Existing destination differs from source: {target}")
        validated.append((incoming, target))

    if dry_run:
        return [dst for _, dst in validated]

    DESTINATION.mkdir(parents=True, exist_ok=True)
    for src, dst in validated:
        if src == dst or dst.exists():
            continue
        with tempfile.NamedTemporaryFile(dir=DESTINATION, delete=False) as tmp:
            temporary = Path(tmp.name)
            try:
                with src.open("rb") as stream:
                    shutil.copyfileobj(stream, tmp)
            except BaseException:
                temporary.unlink(missing_ok=True)
                raise
        os.replace(temporary, dst)

    for entry in contract["reference_files"]:
        entry["checked_in"] = True
    CONTRACT.write_text(json.dumps(contract, indent=2) + "\n", encoding="utf-8")
    return [dst for _, dst in validated]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sheet", type=Path, required=True, help="Original 1536x1024 character sheet")
    parser.add_argument("--pose", type=Path, required=True, help="Original 667x1000 character portrait")
    parser.add_argument("--dry-run", action="store_true", help="Validate both files without copying or updating the contract")
    arguments = parser.parse_args()
    for path in import_references(arguments.sheet, arguments.pose, arguments.dry_run):
        print(f"{'Validated' if arguments.dry_run else 'Imported'}: {path}")
    if not arguments.dry_run:
        print("Commit both original files and the updated contract together.")


if __name__ == "__main__":
    main()
