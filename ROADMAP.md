# Intake Edit roadmap

Updated 2026-11-13. The active companion investigation is a 3D Draconia Malfoy adaptation. The Book Spirit remains the shipping mascot until a replacement is reviewed and integrated. The earlier illustrated-raster plan is historical; its tools and references are retained for possible reuse, not as a mandate for the 3D pipeline. See [the current design and work record](docs/DRACONIA_COMPANION.md).

## Current status

- [x] Inspect the user's original FBX and six texture images. Blender import has separate meshes but no armature, animation actions, or facial shape keys.
- [x] Reconnect and pack the textures and render a transparent static preview: [textured preview package](https://chatgpt.com/library?file_id=libfile_a946878ce15481919152116b417d171f&account_id=personal).
- [x] Research Draconia fan interpretations and draft a proud, composed, occasionally flustered acting direction; interpretations differ and this is a design choice, not canon.
- [x] Make a disposable head-pivot motion study and temporary book test: [motion study](https://chatgpt.com/library?file_id=libfile_11436cc12e0c819189cf6f2bdc666d7b&account_id=personal). It is not approved or production ready.
- [ ] Build and review real reading and happy performances. The test has a floating book with pages facing the viewer, no connected book-holding hands, no eye or facial acting, and only simple head motion.
- [ ] No 3D assets or animations have been integrated into the app; no new replacement release or device validation has occurred.

## Phase 1 — source and feasibility

- [ ] Preserve original FBX/textures and record their exact provenance, license version, and any changes. Model: [Malfoid Chibi (Draco Malfoy)](https://sketchfab.com/3d-models/malfoid-chibi-draco-malfoy-25dce601e17945a89fac8381fab5f7cc), by 0x00001337. The download screen states CC Attribution and commercial use; verify exact license and provide required attribution. Character/franchise rights are separate from the model license, particularly before public distribution.
- [x] Inspect topology, separate mesh islands, original pose and material appearance; preserve source hashes and compare flat/smooth renders, head close-ups and 160px poses in the [head-control study](https://chatgpt.com/library?file_id=libfile_7b404028f5b881919bce733ca8db10d0&account_id=personal). Arm and hand islands can be separated; head geometry is coarse, while hair and cloak are single connected meshes. More detailed deformation inspection remains part of rigging.
- [ ] Improve shading and define the camera, lighting, transparent composition, and 2×2 framing; compare with the user's character references. Low-poly face and fixed asymmetrical arm pose are known limitations.
- [ ] Build a complete reproducible editable source with packed textures, validated pivots and pose controls, and a repeatable render setup. The latest [gaze and smile study](https://chatgpt.com/library?file_id=libfile_f97c6230fb488191ab3079ae921c32f1&account_id=personal) builds on the body, cloak and sleeve rig, but the editable scene is in the linked study rather than this repository. Keep the source and any rebuilding scripts accessible for future rigging and validation, subject to the model's distribution terms.

## Phase 2 — expressive prototype

- [ ] Finish and validate the head, body, arms, hands, legs and clothing rig in representative poses; repair geometry and weights where needed. The experimental rig has body and arm bones, a hand-bound wand, revised cloak and undersleeve weights, corrective smoothing, and experimental manual cloak- and skirt-hem controls. Stronger sleeve bends can still stretch, and the sampled poses do not establish full-body deformation or collision coverage. Test legs, torso, cuffs, cloak and clothing together from multiple views through continuous gestures.
- [ ] Finish facial, finger and hair controls. Experimental gaze, brow and smile controls exist, but blink, broader expressions, finger articulation and independent hair controls remain incomplete. See the [work record](docs/DRACONIA_COMPANION.md) for the limits of each study.
- [ ] Build a correctly oriented book and connected hands; fix intersections, silhouette and pages. Stage other props only when needed.
- [ ] Create a composed reading performance: eyes track the page, a small skeptical or interested pause, and a smooth return. No floating book or disconnected hands.
- [ ] Create a happy reaction: surprise/pleasure, a proud attempt to regain composure, a fleeting genuine smile, and a natural return. Her expression must actually change.
- [ ] Render both complete previews at full size and 160px on light/dark backgrounds. Review movement, timing, expression, loops, and likeness with the user; iterate before expanding. Neither head rotation alone nor a technical pass counts as approval.

## Phase 3 — five moods and shared actions

- [ ] Adapt NEW, WAITING, HAPPY, HUNGRY, SAD to Draconia's personality. Preserve the initial notice/surprise/wave/return sequence, with character-appropriate acting.
- [ ] Build reading, writing and page turning as shared actions performed differently in each state. Hungry is slower and clearly hungry; happy is more lively. Keep contact, occlusion, readable motion and complete sequences.
- [ ] Reinterpret the ten existing additional idle actions for Draconia, including food distraction, thinking, delight, and comfort; ensure every action belongs to its selected mood.
- [ ] Test clean transitions, no mismatched moods, flicker, abrupt cuts or unnatural speed; avoid repetitive full-body sways. User review required.

## Phase 4 — Android/widget integration

- [ ] Compare prere­ndered transparent frames against feasible alternatives using actual widget constraints. Measure asset size, memory, frame pacing and battery on target devices; do not assume a particular implementation already works.
- [ ] Retain transparent, borderless, text-free 2×2 widget, full animations, consistent app/widget appearances, preview icon and tap-to-chapter behavior.
- [ ] Preserve project/branch connection, token gate, per-chapter feeding, progress/levels, local mood, sync and offline behavior.
- [ ] Validate launcher behavior, resizing, switching moods, restart, rapid updates and flicker, and document actual device/launcher coverage.

## Phase 5 — cutover and release

- [ ] Verify attribution and distribution status, visual approval, Android tests, performance, signing/update and data preservation.
- [ ] Replace the app companion, widget assets, widget picker preview and app icon together only after the new character passes review; keep a safe fallback through validation.
- [ ] Update docs, merge and release only after the above gates. Physical-device validation and release signing/data-safety work remain outstanding.

Prior app reliability and Book Spirit milestones remain in [docs/archive](docs/archive/); the previous raster experiments remain documented as historical studies.
