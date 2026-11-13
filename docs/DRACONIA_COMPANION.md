# Draconia companion — design and work record

Updated 2026-11-13. This is the active design reference; [ROADMAP.md](../ROADMAP.md) is the task tracker.

## Character and acting

Draconia is a fan-created female interpretation of Draco Malfoy; different creators tell different stories rather than sharing one canon. The [fan guide](https://malfoid.me/guide) describes this variety, while [Know Your Meme's overview](https://trending.knowyourmeme.com/editorials/guides/what-is-the-malfoid-meme-the-viral-female-draco-malfoy-fan-art-trend-explained) documents the fan-art trend. Draco's rivalry and pride inform the direction ([official character profile](https://www.harrypotter.com/fact-file/characters-and-pets/draco-malfoy)). This design interprets her as poised, proud, expressive and quick to hide affection or embarrassment; it does not claim a single canonical Draconia personality.

- **New:** absorbed in reading, notices the viewer, briefly surprised, collects herself, offers a restrained greeting and returns to reading.
- **Waiting:** composed, curious reading and occasional impatient glances or a small haughty look.
- **Happy:** visible delighted surprise and a fleeting warm smile; she recovers her proud composure. Energy can rise without frantic motion.
- **Hungry:** slower, tired and irritable; distracted by food, not inexplicably smiling.
- **Sad:** subdued and less guarded; a quiet glance away or comfort from her book, with tears used sparingly.

Mundane actions are shared across moods but timed and acted differently. Prefer specific poses, meaningful pauses, expressive face and controlled follow-through to constant body swaying. Symbols should appear only when relevant. Avoid depicting character interpretations as approved finished animation.

## Source and observed model

The user supplied the [Sketchfab FBX](https://sketchfab.com/3d-models/malfoid-chibi-draco-malfoy-25dce601e17945a89fac8381fab5f7cc), **Malfoid Chibi (Draco Malfoy)** by **0x00001337**, with six texture images. The user's download screen states Creative Commons Attribution and commercial use; confirm the exact license version and attribution obligations before redistribution. Rights in the underlying character/franchise are a separate consideration.

Blender inspection found separate mesh objects and UVs, but no armature, animation actions, or shape keys. Its original pose is asymmetrical; the coarse facial geometry and fixed arm position limit expression and book handling. Textures needed manual reconnection. [Textured static preview](https://chatgpt.com/library?file_id=libfile_a946878ce15481919152116b417d171f&account_id=personal) includes a packed Blender scene. This is not an approved rig.

The [exploratory motion study](https://chatgpt.com/library?file_id=libfile_11436cc12e0c819189cf6f2bdc666d7b&account_id=personal) contains 24-frame reading and happy-labelled head-movement tests and the script used to generate them. They are **unapproved diagnostics**, not completed acting: the book floats with its pages facing the viewer, the hands do not hold it, there is no independently animated face, gaze or blink, and the happy test does not actually express happiness. The script depends on temporary paths and is not a portable production pipeline. No companion replacement has been shipped.

## Preservation and visual gates

Keep the existing Book Spirit in production while preparing and reviewing the alternative. Preserve the 2×2 transparent, borderless, text-free widget, full sequences, tapping to a chapter, five moods, daily feeding after successful QA tag/commit, project/branch separation, token gate, level counts and offline behavior.

Before production: demonstrate a coherent book grip, convincing face and gaze, a reading and a happy reaction, clean motion transitions, and 160px readability. Seek visual feedback on the two pilot performances before creating the entire animation set. Then validate the approved frames in the app and actual Android launchers, including state changes, restart, performance and update compatibility. The previous illustration-based artwork and raster experiments are historical references, not the active 3D rig or proof of approval.
