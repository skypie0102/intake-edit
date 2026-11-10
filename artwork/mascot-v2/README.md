# Mascot v2 artwork staging area

The [design bible](../../docs/MASCOT_V2_DESIGN.md) is the creative direction and [rig-contract.json](rig-contract.json) is the normative file/part/state manifest. The current production mascot remains the Book Spirit.

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

Create `master-rig.svg` in this directory with a `viewBox="0 0 320 320"`, all IDs declared in the `rig_layers` array, and independent brows/eyes/mouth/hair/limbs. Complete the expression and 160-pixel checks in the roadmap **before** setting `stage` to `rig_ready`.

When the source and exported packs exist, `tools/test_mascot_v2_contract.py` enforces master layer IDs, reactions, shared actions, and frame counts. It deliberately skips those checks at specification stage, never silently treats a missing rig as finished.

Do not replace `app/src/main/assets/companion/animation-pack.json` or the widget icon resources until final production cutover. The current original mascot generator and assets are kept intact.
