# Mascot v2 Phase 1B — expression references and neutral atlas QA

**Date: 2026-11-11. Status:** reference extraction and QA tools are implemented. No approved blink, layered facial rig, or production mascot replacement.

## Completed work

The approved Slytherin master sheet is SHA-256 pinned as f89e7d8ea51ad8131994bf3a40660329e01d6839977a54c3157536f206b9e5f6.

- The Python extractor (tools/extract_mascot_v2_expressions.py) crops twelve illustrated expression panels: neutral, hmph, annoyed, surprised, thinking, suspicious, flustered, angry, embarrassed, tired, satisfied and sulky. All individual WebP crops are lossless and verified against their original source rectangles. The tiled 512×384 WebP reference atlas is *scaled for browsing* and not an animation texture. These are **illustrated references, not separate transparent eye/brow/face rig parts**.
- The neutral QA script (tools/qa_mascot_raster_atlas.py) rebuilds the resting character from a format-2 sprite atlas, compares it to the approved reference on **light and dark** backgrounds, checks alpha independently of visible RGB, and writes a diff heatmap and 160px side-by-side comparison. Strict mode fails on any visible or alpha difference.
- The regression tests (tools/test_mascot_v2_quality_gates.py) detect pixel color errors even with matching alpha, alpha mismatches, wrong source SHA, and errors in lossless reference roundtrips. Android CI runs them under the existing mascot-test pattern.

## Actual proof on the existing iris-only pilot

Input: previous 640×640 neutral illustration plus three-sprite experimental atlas, including a flattened remainder/body and two separate iris parts.

| Measurement | Local output |
|---|---:|
| Alpha changed pixels | 0 |
| Maximum visible color delta, white background | 0/255 |
| Maximum visible color delta, dark background | 0/255 |
| Neutral atlas reconstruction | Pixel-exact on both backgrounds |
| Approved expression source crops | 12 / 12, lossless |
| Full facial rig approved | **No** |
| Blink animation approved | **No** |

These checks do not prove a moving eyelid looks correct. They prove only that the original resting image survives the raster atlas pipeline.

## Rejected art experiment

Attempts to synthesize closed eyelids through automatic inpainting and transfer of closed-eye lash drawings caused unconvincing flat patches, distorted bang boundaries and oversized dark lashes. **The resulting images are rejected experiments, not accepted production layers.** We are not adding them to the app.

## Reproduce

    python -m pip install Pillow
    python tools/extract_mascot_v2_expressions.py --sheet /path/to/slytherin_chibi_character_master_sheet.png --output /tmp/mascot-expressions
    python tools/qa_mascot_raster_atlas.py --manifest /path/to/atlas/atlas-manifest.json --reference /path/to/neutral-reference.png --output /tmp/mascot-qa --strict

The original artwork images are separately provided in the current conversation art ZIP and not committed as binary files to the repository. Their hashes are tracked in the rig contract.

## Art tasks still required

1. Properly paint both hidden eye whites and eyelid/skin surfaces without damaging the original hair and eyebrow lines; produce real open/half-closed/closed artwork.
2. Separate the eyebrows, eye highlights, lids, mouth, blush and face into registration-aligned independent RGBA layers. Validate gaze and blink motion, then neutral return at 160px and full resolution.
3. Only after the face is approved, separately paint the long hair, head/neck, crossed arms, clothing, robe and Slytherin crest and validate small motions.
4. Keep the production Book Spirit unchanged until all acceptance gates pass. No full mascot replacement or release has happened.
