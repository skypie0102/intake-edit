> **Historical raster study (2026-11-13):** This document describes the previous illustrated-raster approach. It is retained for reference and does not describe the active 3D companion plan. See [the current roadmap](../../ROADMAP.md).

# Mascot v2 artwork staging area

The [design bible](../../docs/MASCOT_V2_DESIGN.md) is the creative direction and [rig-contract.json](rig-contract.json) is the normative file/part/state manifest. The current production mascot remains the Book Spirit. The active new direction is [illustrated raster layers and a compact WebP atlas](../../docs/MASCOT_V2_RASTER_PIPELINE.md), not an SVG puppet.

> **Visual art reset, 2026-11-10:** The programmatically approximated SVG prototype introduced in PR #62 did not match the approved illustration and was rejected. Its master SVG, face presets, side/back art, generator and prototype tests have been removed from active source (they remain accessible in Git history for diagnosis only). This directory contains the specification, **not** an approved rig. Do not reuse the rejected paths or claim rig completion based on topology tests.

## Reference import

The **unmodified original files** from the 2026-11-10 conversation are tracked by SHA-256 but have **not** been committed yet. Importing a smaller screenshot or recompressing the originals will fail the integrity check.

From the repository root:

```sh
python3 tools/import_mascot_v2_references.py \
  --sheet "/path/to/slytherin_chibi_character_master_sheet.png" \
  --pose "/path/to/Chibi_Malfoid_1000.png" \
  --dry-run

python3 tools/import_mascot_v2_references.py \
  --sheet "/path/to/slytherin_chibi_character_master_sheet.png" \
  --pose "/path/to/Chibi_Malfoid_1000.png"
```

The second command copies files to `artwork/mascot-v2/reference/`, then marks both `checked_in` fields in the contract. Commit both images and the updated manifest in one change. The user-provided sheet has a `.png` filename but may be stored as JPEG bytes in chat exports; preserve the original bytes and let the editor sniff content type. These source photos are for manual drawing only, never direct widget output.

## Master artwork and rig design

Create a native **layered** art source such as `master-layered.kra` (or PSD-equivalent), keeping the character's original detailed linework, shading and chibi proportions. This is not a code-drawn SVG task. Paint actual hidden hair/sleeve/neck/robe surfaces before splitting the joints; export each real moving part as transparent PNG. The 31 IDs and pivot values in `rig-contract.json` are still **provisional** and must be adjusted to the accepted painting.

A completed, art-approved rig will be packaged with `tools/pack_mascot_raster_layers.py` into a transparent WebP atlas plus pose metadata. The `tools/test_mascot_v2_contract.py` checks still describe compatibility and draft topology; they do not certify character likeness. Never set `stage=rig_ready` based solely on generated assets, counts or passing tests.

Do not replace `app/src/main/assets/companion/animation-pack.json` or the widget icon resources until final production cutover. The original mascot generator and assets remain intact.

## Raster proof of concept

Run `python tools/prepare_mascot_raster.py --portrait /path/to/Chibi_Malfoid_1000.png --output ./raster-preview/` to isolate the exact original character pixels (the speech bubble is a separate alpha component) and export static 160/320px WebP full-body and bust crops. The original artwork files are **not checked into the repository** yet. The preview does not constitute an animated rig.

When a proper layered master is illustrated and approved, use `python tools/pack_mascot_raster_layers.py --layers /path/to/layers.json --output ./raster-atlas/`. The provisional 31-part specification is not an instruction to chop the current flattened PNG into fake limbs. See the separate Phase 1A/1B gates in the roadmap.

## Phase 1B experimental eye-layer pilot (NOT approved)

The next work item is deliberately limited to the eyes. With an exact original portrait input, run:

```sh
python -m pip install Pillow numpy opencv-python-headless
python tools/prototype_mascot_v2_eye_layers.py \
  --portrait /path/to/Chibi_Malfoid_1000.png \
  --output /tmp/mascot-v2-eyes
```

This produces an editable 640×640 OpenRaster file containing two independently isolated original-pixel eye illustrations, an approximate underpainted base and a hidden intact reference. Technical checks guarantee exact neutral recomposition **only**; they do not certify the underlying skin for blinking, the eyelid edge geometry, or the complete rig. In early motion stress frames a narrow seam becomes visible. The [eye pilot report](../../docs/MASCOT_V2_PHASE1B_EYE_PILOT.md) and [expanded Phase 1B checklist](../../ROADMAP.md) track the necessary artist repaint. The Slytherin sheet remains authoritative for the crest.

This experiment is isolated from the app's production resources. The current Book Spirit remains unchanged.

## Follow-up: iris-only tracking experiment

The original whole-eye pilot exposed seams when moving eyelash outlines. The separate `tools/prototype_mascot_v2_iris_motion.py` isolates **only the illustrated iris interiors**, leaves lashes and eyelids fixed and proves pixel-exact neutral recomposition at 640×640. It exports a four-layer experimental OpenRaster master, five tiny gaze-shift frames and 160px comparison images. Run:

```sh
python tools/prototype_mascot_v2_iris_motion.py --portrait /path/to/Chibi_Malfoid_1000.png --output /tmp/iris-v2
```

These layers are not painted sclera or finished blinking assets. Read [the iris study](../../docs/MASCOT_V2_PHASE1B_IRIS_STUDY.md) before reusing the output. Production still uses the Book Spirit.

## Approved-expression reference extraction and neutral image QA

Extract the twelve existing approved Slytherin expression illustrations with `python tools/extract_mascot_v2_expressions.py --sheet /path/to/slytherin_chibi_character_master_sheet.png --output /tmp/face-ref`. Each unscaled crop is kept losslessly as WebP; the reference atlas is scaled only for browsing. **These are not transparent rig layers.**

When a genuine independently layered atlas exists, run `python tools/qa_mascot_raster_atlas.py --manifest /path/to/atlas-manifest.json --reference /path/to/neutral-reference.png --output /tmp/qa --strict`. It must preserve alpha and visible RGB against light and dark backgrounds, even before moving any joints. A perfectly matching resting frame is necessary but not sufficient for rig approval. See [the Phase 1B report](../../docs/MASCOT_V2_PHASE1B_QA_REFERENCES.md). Automated closed-eye artwork trials did not pass visual inspection and have not been accepted.
