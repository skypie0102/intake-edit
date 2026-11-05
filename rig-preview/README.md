# Companion rig preview updates

The separately installed rig-preview app checks `animation-pack.json` from this preview branch when opened and when **Refresh animations** is tapped. It keeps the last successfully validated pack for offline use; the demo widget uses the same cached pack. Reopen the preview or tap refresh to fetch changes and update the widget.

After editing the approved SVG and rig generator, run `python tools/generate_rig_preview_pack.py` and commit both `rig-preview/animation-pack.json` and `app/src/debug/assets/rig-preview-pack.json`. The app renders the vector poses in the foreground and samples them for the widget. The pack is data, not an executable app update. New animation capabilities or changes to the pack format may still require an APK update.
