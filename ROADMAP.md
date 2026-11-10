# Intake Edit roadmap

**Active initiative (opened 2026-11-10): replace the Book Spirit with the user's blonde witch chibi mascot.** The approved direction uses the supplied character sheet and original illustration; it requires a new layered character rig, not a reskin of the Book Spirit. See the [Mascot v2 design bible](docs/MASCOT_V2_DESIGN.md) and [machine-readable rig contract](artwork/mascot-v2/rig-contract.json). **The existing Book Spirit remains in production until the new character is complete.**

## Phase 0 — audit and safe foundation

- [x] Audit the app's rig, SVG→frame generation, preview pipeline, in-app renderer and 2×2 widget.
- [x] Establish the design bible, five-mood acting matrix, non-regression constraints and separate v2 manifest.
- [x] Add contract validation to CI without changing the production animation pack.
- [ ] Import the two **original full-resolution** reference images into the art repository; verify SHA-256 digests and preserve pristine copies.

**Gate:** documented canonical source, validated manifest and unchanged old rendering.

## Phase 1 — master artwork and layered vector rig

- [x] Draw initial front/3/4 master and independent side/back SVG silhouette studies (not raster cutouts).
- [ ] Refine full front and 3/4-back turnaround details and confirm design consistency against the approved sheet.
- [x] Author first-pass 31 separate SVG rig layers (hair, face, eyes, brows, mouth, articulated arms/hands, uniform, robe, legs and footwear) with normalized pivots.
- [ ] Refine overlapping fills/masks and stress-test shoulder, wrist, hair and robe motion through the required movement range.
- [x] Add the Slytherin-inspired chibi crest, uniform palette and 12 machine-editable facial expressions (first pass).
- [ ] Visual polish: improve anatomical fidelity, clothing folds, hair strands, expression likeness and the Slytherin crest at 160px.
- [x] Produce 320×320 transparent master rig, local expression/turnaround/pose contact sheets and 160px visibility images.
- [ ] Complete visual art sign-off, including accurate front/back silhouette and small-widget comparison.
- [x] Add 31-layer/12-expression SVG topology tests and generator verification in Android CI.
- [ ] Complete rendered seam/occlusion stress checks and AndroidSVG feature compatibility review.

**Current result:** first-pass vector rig committed on the Phase 1 branch, visual preview generated; detailed articulation/stress QA and design approval still open. **Gate not yet passed:** independent movement must remain seamless across full-range test poses.

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

- [ ] Produce distinct mascot-v2 preview pack, retaining remote refresh for the **in-app** preview.
- [ ] Export the production-compatible SVG/JSON animation pack and test parsing, package size and memory.
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
