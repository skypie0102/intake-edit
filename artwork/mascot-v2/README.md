# Mascot v2 artwork staging area

The [design bible](../../docs/MASCOT_V2_DESIGN.md) is the creative direction and [rig-contract.json](rig-contract.json) is the normative file/part/state manifest. The current production mascot remains the Book Spirit. A **first-pass SVG master with 31 editable rig parts** and **12 expression presets** are now in this directory; this prototype is not yet art-approved or ready for production integration.

## Reference import

The **unmodified original files** from the 2026-11-10 conversation are tracked by SHA-256 but have **not** been committed yet. Importing a smaller screenshot or recompressing the originals will fail the integrity check.

From the repository root:

```sh
python3 tools/import_mascot_v2_references.py \
  --sheet "/path/to/1002471086.png" \
  --pose "/path/to/Chibi_Malfoid_1000.png" \
  --dry-run

python3 tools/import_mascot_v2_references.py \
  --sheet "/path/to/1002471086.png" \
  --pose "/path/to/Chibi_Malfoid_1000.png"
```

The second command copies files to `artwork/mascot-v2/reference/`, then marks both `checked_in` fields in the contract. Commit both images and the updated manifest in one change. The user-provided sheet has a `.png` filename but may be stored as JPEG bytes in chat exports; preserve the original bytes and let the editor sniff content type. These source photos are for manual drawing only, never direct widget output.

## Master design

The new `master-rig.svg` has a `viewBox="0 0 320 320"`, all 31 IDs from `rig_layers`, normalized pivots, and independently rendered brows/eyes/mouth/hair/limbs. The chibi serpent crest is its own SVG group, rather than being baked into the arm. Generate and validate all expression files with `python3 tools/generate_mascot_v2_phase1.py --validate` or run the script without the flag to write individual SVGs. The manifest uses `stage=artwork_ready` for this vector prototype. Complete the visual 160-pixel checks, full joint-overlap stress tests, and original reference import before any production animation `rig_ready` gate.

`tools/test_mascot_v2_contract.py` and `tools/test_mascot_v2_artwork.py` enforce master layer IDs, the separate reference studies and all twelve facial variants. Full reaction/shared-action frame counts will be enforced once the animation export pack exists. It deliberately skips those checks at specification stage, never silently treats a missing rig as finished.

Do not replace `app/src/main/assets/companion/animation-pack.json` or the widget icon resources until final production cutover. The current original mascot generator and assets are kept intact.
