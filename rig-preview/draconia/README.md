# Draconia preview pack

The separate debug preview downloads `rig-preview/draconia/pack.json` from the `preview/draconia-companion` branch when opened and when Refresh animations is tapped. The user can choose a named study, pause, and compare light/dark backgrounds. A validated offline copy is cached; the debug APK includes a fallback pack. The Book Spirit widget remains separate and is not updated by this preview.

The checked-in initial clips are **rough head-motion studies**, not approved reading or happy animations: the book and hands are not connected and facial acting is absent. Pack format 1 contains a revision, description and named clips. Each clip has a unique lowercase ID, label, duration in milliseconds and up to 96 base64-encoded WebP frames (maximum 256×256). The app enforces pack/image bounds and validates the whole update before replacing its cached copy.

To update animations, render transparent frames from the current Blender master, package them under the existing format, test their playback, and commit the pack to this branch. Opening or refreshing the app fetches them without an APK reinstall. Changes to the preview app itself still need a new APK. The debug package has the .rigpreview application ID and is separate from the production app.
