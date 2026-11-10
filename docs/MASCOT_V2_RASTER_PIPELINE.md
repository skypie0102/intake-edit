# Mascot v2: high-fidelity raster / hybrid animation pipeline

**Adopted 2026-11-10. Current status: first static raster proof, NOT an animated or art-approved layered rig.**

## Why this replaces the earlier pure-SVG requirement

PR #62 proved that code-authored vector paths were not visually faithful to the approved blonde Slytherin chibi. The problem was the approximation, not that every vector drawing is inherently bad. For this project, preserve the original illustrated pixels in *artist-authored layers*, then animate those layers with transforms and selective deformation. Never redraw her face, hair or robe as crude vector primitives just to satisfy a part count.

The original 667×1000 RGBA portrait is **already transparent**. Its “Hmph!” bubble is a detached alpha component. The reference preparation tool keeps only the real character's alpha-connected component; it does not remove color backgrounds, paint new features or invent missing anatomy. A separate approved Slytherin character sheet remains the crest / pose / expression design reference, not an automatically segmented rig source.

## Phase 1A: reference-faithful static proof (implemented, awaiting visual acceptance)

Use the exact, unmodified original portrait; its SHA-256 is pinned in the design contract:

```sh
python -m pip install Pillow
python tools/prepare_mascot_raster.py \
  --portrait /path/to/Chibi_Malfoid_1000.png \
  --output /tmp/mascot-v2-raster
```

Outputs:
- `neutral-isolated-lossless.png`: original full-resolution RGBA character, speech bubble gone; no repaint.
- `neutral-full-160.webp`, `neutral-full-320.webp`: transparent, full-character still preview.
- `neutral-bust-160.webp`, `neutral-bust-320.webp`: alternative head/torso **crop** from the same illustration, not an additional artwork asset.
- `preview-manifest.json`: source digest, export sizes, pixel-fidelity checks, and explicit `rig_ready=false`, `animation_ready=false`, `layers=[]`.

Every WebP is quality 95 with lossless alpha, and exported images are checked against their uncompressed rendered originals. The tool rejects artifacts with alpha mismatch or visible-pixel PSNR below 37 dB. These checks detect codec degradation; **human art review is still mandatory**.

The user has not yet approved the transparent master or chosen full-body versus bust framing for the widget. Do not mark Phase 1A complete before that approval.

## Phase 1B: proper illustrated layers and compact texture atlas (packer implemented)

Art should be created/retouched in a native **Krita .kra, PSD or equivalent layered file**, preserving pristine original reference(s). Independently paint the **hidden material** behind hair locks, neck, sleeves and robe openings. Export each legitimately separate component as RGBA PNG/WebP. Merely splitting one flattened image into rectangular patches is not a valid rig.

Once actual painted parts exist, prepare a manifest like:

```json
{
  "format": 2,
  "canvas": [320, 320],
  "layers": [
    {
      "id": "hair_back",
      "file": "layers/hair_back.png",
      "position": [0, 0],
      "pivot": [160, 110],
      "parent": null,
      "z": 0
    }
  ]
}
```

The example demonstrates the schema, **not a finished layer**. Use `tools/pack_mascot_raster_layers.py --layers path/to/layers.json --output out/` to trim transparent margins and pack layers into a transparent **lossless WebP texture atlas**, separated by gutters. It exports `texture-0.webp` and `atlas-manifest.json` with each part's atlas rectangle, logical position, pivot, parent and draw order. The script rejects empty sets, duplicate IDs, unsafe paths, unpainted/empty parts, overlapping coordinate declarations and atlases exceeding a 5 MB first-pass asset budget. That budget is a working target, not a guarantee for finished animations.

A dormant native Android renderer, `RasterCompanionAtlas.kt`, can load and draw these separate sprites using pivots, rotation, scale and translation. It is **not enabled by default** and requires a valid artist-authored layer atlas. Later add selective mesh deformation where proven necessary, without shipping an entire Live2D SDK prematurely.

## Animation and home-screen widget architecture

**In the app:** reuse a small atlas across moods; keyframes describe motion instead of embedding complete images for each frame. Maintain the existing NEW / WAITING / HAPPY / HUNGRY / SAD state rules, shared reading/writing/page turning and idle performances. Plan for proper occlusion, joint continuity and hair/robe follow-through.

**In the widget:** Android `RemoteViews` cannot host arbitrary custom animated Canvas content. Render selected frames from the same approved rig at 160×160 (or another validated size), store compressed still frames, and reuse the existing transparent frame adapter. Avoid rebuilding the atlas and allocating large bitmaps during each widget update.

The following constraints are locked:
- 2×2 widget, no border or status text; tapping opens the appropriate chapter.
- All five moods still derive from actual project progress / Tag for QA.
- Preserve offline, level/feeding history and chapter navigation.
- Keep the Book Spirit bundled and displayed during design/preview stages.
- No expression/page-turn claim until the actual painted layer geometry and animation previews exist.
- Do not confuse compression/CI success with visual approval.

## Acceptance gates

1. **Source fidelity:** compare approved character artwork to isolated high-resolution portrait and 160px previews; deliberate crop/framing only.
2. **Illustrated layer quality:** every eye, bang, sleeve, arm and hair section matches the approved drawing even when separated; hidden overlaps are genuinely drawn.
3. **Atlas feasibility:** verify original alpha, outline detail, packed size, memory and decode time on target Android.
4. **Animation:** expressive poses and natural joint motion, no cutouts or disembodied limbs, no wrong-state frames; mood-specific timing.
5. **Cutover:** both in-app mascot and widget use the same approved character, static fallbacks and the existing state/feeding logic.
6. **Device testing and release:** build, test on Android, check launcher frame pacing/flicker, then merge and release.

**Status as of this change:** image extraction, WebP previews, a real-layer atlas packer, dormant native renderer and automated tests have been created. **No illustrated rig layers, animation clips, production widget assets or replacement APK have been completed.**
