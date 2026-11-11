# Intake Edit roadmap

**On hold as of 2026-11-11: replace the Book Spirit with the user's blonde witch chibi mascot.** The approved direction uses the supplied character sheet and original illustration; it requires a new layered character rig, not a reskin of the Book Spirit. See the [Mascot v2 design bible](docs/MASCOT_V2_DESIGN.md) and [machine-readable rig contract](artwork/mascot-v2/rig-contract.json). **The existing Book Spirit remains in production until the new character is complete.**

## Current priority — chapter editor usability (2026-11-11)

- [x] Keep Paste anchored on the left of each paragraph card's toolbar while formatting actions scroll.
- [x] Add a persistent **Copy ref** switch in the editor bottom bar. OFF = RAW only; ON = RAW plus the current imported/machine translation.
- [x] Auto-dismiss editor, chapter-list and settings notices after five seconds; keep enduring QA warnings visible.

The mascot replacement is paused until explicitly resumed. The existing Book Spirit assets remain the production graphics.

## Phase 0 — audit and safe foundation

- [x] Audit the app's rig, SVG→frame generation, preview pipeline, in-app renderer and 2×2 widget.
- [x] Establish the design bible, five-mood acting matrix, non-regression constraints and separate v2 manifest.
- [x] Add contract validation to CI without changing the production animation pack.
- [ ] Import the two **original full-resolution** reference images into the art repository; verify SHA-256 digests and preserve pristine copies.

**Gate:** documented canonical source, validated manifest and unchanged old rendering.

## Phase 1 — high-fidelity illustrated raster foundation (PAUSED)

The vector approximation from PR #62 is rejected. Keep the approved illustrated artwork. [New pipeline and quality gates](docs/MASCOT_V2_RASTER_PIPELINE.md).

### Phase 1A — faithful source and static reference

- [x] Verify the original portrait is RGBA with a detached speech bubble; isolate the character without repainting or vectorization.
- [x] Implement deterministic full-size extraction and compressed, transparent 160/320px WebP **still** previews, checking alpha preservation and visible-pixel fidelity.
- [x] Export a one-layer, original-pixel-preserving OpenRaster art master to serve as a real editable starting point (not yet a rig).
- [x] Produce full-body and bust framing comparisons on both light and dark backgrounds.
- [x] User approved the overall raster character preview as good enough to continue on 2026-11-10; **specific full-body vs bust widget framing remains undecided**.
- [ ] Commit both original, SHA-256-verified reference images and the approved Slytherin character sheet to the art source directory; leave the user images untouched.

**Gate:** the illustrated raster master must look like the approved character. A crop is not a rig.

### Phase 1B — true layered raster rig (PAUSED)

**Visual baseline:** The user accepted the high-fidelity static raster preview. This approves the design direction, **not** a movable rig. All editable layers must be faithful to the approved portrait and Slytherin sheet. No crude SVG substitutions, no image sliced into fake rectangle puppet parts, no claims of acceptance based only on passing tests.

#### 1B.0 — Layer authoring workflow and source control

- [x] Implement a transparent WebP atlas packer and dormant Android Canvas renderer (prior foundation).
- [ ] Import the two original SHA-256-pinned character references and Slytherin sheet as untouched source images.
- [ ] Establish a **single editable master project** (OpenRaster/Krita or PSD-equivalent) with original flattened art preserved as locked reference; export separate RGBA layers without rescaling or degrading detail.
- [ ] Record logical canvas, pixel density, parent/bone hierarchy, z-order, pivot coordinates, visual source and art-review status for each moving piece. Do not call a crop with no hidden paint an approved bone.

#### 1B.1 — Facial pilot before large-scale separation

- [x] Produce an **experimental eye-only** layer pilot from exact original portrait pixels (separate left/right eye artwork and editable OpenRaster layers); verify a pixel-exact neutral reassembly at 640×640. **No full rig or blink approval implied.**
- [ ] Artist-repaint skin and eyelid geometry hidden by those eye layers; refine eye masks and repeat neutral/stress checks to eliminate outline seams.
- [x] Render first tiny eye translations/squashes for visual diagnostics; **the stress poses expose seams**, so they are intentionally marked **unapproved**.
- [x] Prototype **iris-only motion** while keeping eyelashes and eyelids attached to the face; neutral RGBA reconstructs exactly from the original artwork. Export an editable OpenRaster file, 160px previews, and a compact preliminary lossless WebP atlas.
- [ ] Repaint the hidden sclera/eyelid art (currently a temporary sample fill) and verify all small iris shifts have no halos or mismatched shading at full resolution and 160px. **Gaze pilot is not visually approved or a blink animation.**
- [x] Extract and checksum-lock all twelve **originally illustrated facial expression reference crops** from the approved Slytherin sheet, each with lossless WebP pixel-exact source match and a compact reference atlas. **References are not independent rig layers or approved interchangeable facial animation.**
- [ ] Separate eyebrows, eyelids, pupils/highlights, mouth and facial blush, preserving the original unique half-lidded expression. Add fully drawn blink/open/closed-eye variants.
- [ ] Reproduce twelve labeled expressions from the approved reference using genuinely edited facial artwork, not automatic warping of one face; validate that she remains recognizable in every state.

#### 1B.2 — Hair and head

- [ ] Separate back hair, bangs, left/right side locks and smaller flowing sections with correct depth and root pivots.
- [ ] Paint **hidden forehead, scalp, ears, and hair underneath each moving lock**; do not accept transparent holes when a strand moves.
- [ ] Build unified head+neck joint and ensure eye/eyebrow/mouth/hair attachments follow head orientation.
- [ ] Stress-test small head rotation/translation and delayed hair movement without stiff cutout seams.

#### 1B.3 — Body, outfit and articulated arms

- [ ] Paint torso, neck/collar, gray shirt, green tie, knit vest, Slytherin crest, skirt, tights/legs, shoes and robe front/back at full detail.
- [ ] Isolate both upper arms, forearms and hands as continuous anatomical chains, painting **hidden shoulders, wrists and sleeves** for uncrossing and gestures.
- [ ] Verify crossed-arm resting pose, writing/reaching pose and return, with hands attached and robe sleeves covering joints.
- [ ] Add correctly occluded prop attachment points for book, pen and wand; do not author full action animation yet.

#### 1B.4 — Layer pack, diagnostics and performance

- [x] Add a deterministic **raster neutral-pose atlas QA tool**: recompose from sprite atlas, test pixel/alpha fidelity on light and dark backgrounds, export magnified error heatmap and 160px comparisons. Existing iris pilot's static rest reconstruction tested with 0 alpha and 0 visible color difference.
- [ ] Export real painted layers to PNG/WebP with alpha; create compact atlas and skeleton/pose manifest from the one master file.
- [ ] Test no holes/halos at rest; layer redraw must match approved illustration at full resolution and 160/320px.
- [ ] Test small head/eye/arm/hair deformations, all pivot bounds and return-to-rest reconstruction; label unapproved poses.
- [ ] Measure asset size (initial **5 MB texture budget**, not a guaranteed cap), decode memory/time and Android preview behavior.
- [ ] Verify render order, layer edge handling, static fallback and rapid pose/mood switching, without modifying Book Spirit.

#### 1B.5 — Approval and hand-off

- [ ] Produce full-size and 160px contact sheets of neutral pose, expression tests and **actual movement stress frames**.
- [ ] Obtain visual approval of resting character and key joint poses; fix likeness and art seams *before* Phase 2.
- [ ] Document remaining animation/mesh deformation needs and freeze versioned rig assets for Phase 2.

**Phase 1B development note (2026-11-11):** Automatic closed-eyelid painting trials failed visual quality (mismatched skin and hair edges); **none was accepted or shipped**. The twelve original approved expression illustrations are now preserved as lossless references, and neutral atlas art fidelity can be checked deterministically. See [Phase 1B QA report](docs/MASCOT_V2_PHASE1B_QA_REFERENCES.md).

**Phase 1B pilot note (2026-11-10):** A local experimental pixel-preserving eye-layer/OpenRaster pack is available. It has a flattened remainder for the body and machine-estimated eye underpaint, and it does **not** satisfy the eye or whole-rig visual gate. See the [whole-eye pilot](docs/MASCOT_V2_PHASE1B_EYE_PILOT.md) and [iris-only follow-up](docs/MASCOT_V2_PHASE1B_IRIS_STUDY.md).

**Phase 1B gate:** The character must remain visibly faithful to the approved illustrations *and* support real independent motions. Automated image/fidelity tests check technical integrity; human visual review decides artistic acceptance. **At present the full character is not layered or animatable; the Book Spirit remains the production mascot.**

## Phase 2 — five state reactions and transitions

- [ ] NEW greeting: read → notice viewer → surprise synchronized with ! → restrained smile as ! disappears → short wave → return to reading.
- [ ] WAITING folded-arm impatience, HAPPY smug delight, HUNGRY sluggish frustration, SAD subdued/rare tears.
- [ ] Real blinks, gaze tracking, eyebrow and mouth acting, visible hair/robe follow-through and varied cadence.
- [ ] Cancel outgoing animations on mood switch; no mismatched frames, whole-character fades or abrupt body jumps.

**Gate:** all five previews start, loop, restart and switch cleanly.

## Phase 3 — shared actions and mood idles

- [ ] Recreate reading, writing and page turning for all five moods, preserving eyes-on-page, connected hands, pen contact and physically coherent book/page motion.
- [ ] Recreate NEW curious_peek/absorbed, WAITING find_place/thinking, HAPPY humming/delighted, HUNGRY food/rumble, SAD losing_focus/book_comfort.
- [ ] Vary energy, pace and expression by mood; do not shuffle actions between mood previews.
- [ ] Inspect readability and smooth recovery at 160×160 without shortening sequences or introducing uncanny transitions.

**Gate:** five reactions, 15 mood-specific shared-action performances and ten extended idles verified.

## Phase 4 — preview, pack and production cutover

- [ ] Add a dedicated mascot-v2 raster preview mode (independent from legacy Book Spirit SVG preview), with a refreshable approved atlas/pose pack.
- [ ] Package the illustrated WebP atlas and separate keyframe tracks (raster format 2), and validate decoding, size and memory without routing them through the Book Spirit SVG parser.
- [ ] Replace the in-app portrait, 2×2 transparent widget, picker preview, static/reduced-motion fallbacks and companion-branded app icon together.
- [ ] Preserve tap-to-chapter, levels, feeding, project/branch cache, GitHub sync and offline behavior. Retain Book Spirit assets until the v2 fallback is verified.

**Gate:** the same mascot and mood appear in app/widget after switching, restart and offline.

## Phase 5 — release QA and cleanup

- [ ] Run Python generator/contract tests, Android CI, animator and launcher checks.
- [ ] Validate no flicker, abrupt transitions, cropped poses or wrong-state faces at 2×2 and resized widget sizes; audit memory/battery where possible.
- [ ] Test on Android device/launchers where available; explicitly record untested device-specific behavior.
- [ ] Update documentation, replace icon, clean obsolete assets, merge and publish the new release only after the gates.

**Gate:** new mascot shipped without translation-workflow regressions.

## Deferred unrelated work

- Physical-device performance and interaction work outside mascot cutover remains deferred.
- Release signing/data safety work is not yet complete; preserve/update compatibility before a required reinstall.
- The older Book Spirit widget validation was deferred; it does not count as validation of the new mascot.

Prior app reliability work is in the [reliability archive](docs/archive/APP_RELIABILITY_ROADMAP_2026-11.md), companion history in the [animation archive](docs/archive/COMPANION_ANIMATION_MILESTONE_2026-11.md), and synthetic emulator notes in the [performance review](docs/PERFORMANCE_REVIEW.md).
