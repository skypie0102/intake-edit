> **Historical raster study (2026-11-13):** This document describes the previous illustrated-raster approach. It is retained for reference and does not describe the active 3D companion plan. See [the current roadmap](../ROADMAP.md).

# Phase 1B first pilot — original-pixel eye layers (experimental)

**Status: exploratory rigging test, NOT approved artwork or a complete mascot rig.** Created 2026-11-10 after the user approved the raster-first static visual approach.

## What was actually made

Using the original untouched 667×1000 RGBA portrait (SHA-256 `57b9a2f264769656eaf39e58e0169cdb41d7c700ea93b535272b4e9efa2c0ce1`), the local pilot:

1. Removes the separate “Hmph!” bubble by preserving only the alpha-connected character, without redrawing or vectorization.
2. Frames the original character to a transparent 640×640 raster (2 pixels per 320 logical units).
3. Uses limited manually defined masks to isolate each eye **including the original eyelash/pupil pixel art**; clears invisible RGB for compact storage.
4. Creates a separate underpainted base using a *temporary algorithmic* skin estimate behind the eye masks (OpenCV Telea). This is **not final artist-painted hidden geometry**.
5. Writes a four-layer OpenRaster .ora working file: left eye, right eye, underpainted base, and a locked/hidden neutral reference.
6. Exports tiny eye movement/squash tests and full-size contact sheets, plus a lossless WebP texture atlas and sprite-position manifest.

## Measured technical results on the real portrait

- Neutral RGBA reassembly is **pixel exact** relative to the 640×640 cleaned/framed source: maximum visible RGB difference 0 and identical alpha.
- Lossless WebP atlas reassembly likewise produced **maximum RGBA difference 0**.
- `eye_left.png`: approximately 5.3 KB; `eye_right.png`: approximately 8.3 KB.
- `texture-0.webp`: approximately 133 KB (1024×1024, containing the underpainted flattened base plus the two detached eyes).
- The local preview pack includes the `eye-pilot.ora` art file, source inputs, reproduction scripts, masks, pose examples, atlas and neutral-fidelity manifest. It is not a packaged APK.

**These numbers apply only to this tiny demonstration, not the eventual full character pack or the 5 MB aspirational budget.**

## Visual failures discovered

Movement/squash tests visibly expose a thin mismatched underpaint/lash boundary at larger-than-neutral eye changes. A flattened character body is still underneath the two eye sprites. This test therefore **fails the artist/animation acceptance gate**. It must not be turned into a pretend final blink by simply stretching the eye bitmap.

Needed next: real eyelid/closed-eye paintings, manually repainted hidden face skin, cut masks refined to individual actual visual features, independent brows and pupils, then face/hair separation. After that, head/hair/arms/robes and prop attachment must be authored from the approved illustration.

## Where to continue

- Follow the ordered Phase 1B checklist in [ROADMAP.md](../ROADMAP.md).
- The standalone raster-pilot artwork pack produced during this work is not an app resource and has not been pushed as binary artwork. The original references are also still pending GitHub import.
- The current Book Spirit app, widget, feed/levels and chapter navigation remain untouched.

**Do not change the contract's `stage=specification`, or check off Phase 1B visual approval, on the basis of these tests.**
