# Companion rig preview updates

The separately installed rig-preview app checks `rig-preview/animation-pack.json` on the `preview/companion-rig-first-action` branch when opened and when **Refresh animations** is tapped. It keeps the last successfully validated pack for offline use in the in-app preview. The preview widget uses the production widget playback path and the animation pack bundled in the APK, so refreshing the in-app preview does not update the widget's frames. Rebuild the preview APK to test changes to the bundled pack or widget implementation.

After editing the approved SVG and rig generator, run `python tools/generate_rig_preview_pack.py` and commit both `rig-preview/animation-pack.json` and `app/src/main/assets/companion/animation-pack.json`. The production app and widget use the bundled pack and do not fetch animation updates from the preview branch.

## Mascot v2 raster development (separate from production)

The vector Book Spirit preview remains functional. The new character has a **separate, incomplete raster pipeline** documented in [the mascot raster guide](../docs/MASCOT_V2_RASTER_PIPELINE.md). Its current neutral WebP images are static reference proofs only, not frames or editable rig layers. Use the generator to inspect 160px/320px quality; do not pass them to the legacy SVG animation parser. The new native atlas renderer will be wired into a dedicated raster preview only after the real illustrated layers exist.
