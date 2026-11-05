# Companion rig preview updates

The separately installed rig-preview app checks `rig-preview/animation-pack.json` on the `preview/companion-rig-first-action` branch when opened and when **Refresh animations** is tapped. It keeps the last successfully validated pack for offline use in the in-app preview. The preview widget uses the production widget playback path and the animation pack bundled in the APK, so refreshing the in-app preview does not update the widget's frames. Rebuild the preview APK to test changes to the bundled pack or widget implementation.

After editing the approved SVG and rig generator, run `python tools/generate_rig_preview_pack.py` and commit both `rig-preview/animation-pack.json` and `app/src/main/assets/companion/animation-pack.json`. The production app and widget use the bundled pack and do not fetch animation updates from the preview branch.
