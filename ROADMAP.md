# Intake Edit roadmap

**Active initiative (opened 2026-11-10): replace the Book Spirit with the user's blonde witch chibi mascot.** The approved direction uses the supplied character sheet and original illustration; it requires a new layered character rig, not a reskin of the Book Spirit. See the [Mascot v2 design bible](docs/MASCOT_V2_DESIGN.md) and [machine-readable rig contract](artwork/mascot-v2/rig-contract.json). **The existing Book Spirit remains in production until the new character is complete.**

## Phase 0 — audit and safe foundation

- [x] Audit the app's rig, SVG→frame generation, preview pipeline, in-app renderer and 2×2 widget.
- [x] Establish the design bible, five-mood acting matrix, non-regression constraints and separate v2 manifest.
- [x] Add contract validation to CI without changing the production animation pack.
- [ ] Import the two **original full-resolution** reference images into the art repository; verify SHA-256 digests and preserve pristine copies.

**Gate:** documented canonical source, validated manifest and unchanged old rendering.

## Phase 1 — high-fidelity illustrated raster foundation (ACTIVE)

The vector approximation from PR #62 is rejected. Keep the approved illustrated artwork. [New pipeline and quality gates](docs/MASCOT_V2_RASTER_PIPELINE.md).

### Phase 1A — faithful source and static reference

- [x] Verify the original portrait is RGBA with a detached speech bubble; isolate the character without repainting or vectorization.
- [x] Implement deterministic full-size extraction and compressed, transparent 160/320px WebP **still** previews, checking alpha preservation and visible-pixel fidelity.
- [x] Produce full-body and bust framing comparisons on both light and dark backgrounds.
- [ ] Get visual approval for the transparent master and preferred 2×2 widget framing.
- [ ] Commit both original, SHA-256-verified reference images and the approved Slytherin character sheet to the art source directory; leave the user images untouched.

**Gate:** the illustrated raster master must look like the approved character. A crop is not a rig.

### Phase 1B — true layered raster rig

- [x] Create deterministic lossless WebP atlas packer for separately painted images, with pivots, draw order, safe paths and size guardrails.
- [x] Add a dormant Android Canvas atlas renderer with composable part transforms; do **not** replace the current Book Spirit.
- [ ] Produce a real editable layered source (.kra/PSD-equivalent), painting hidden anatomy beneath hair/robe/arms, and isolate 31 provisional parts.
- [ ] Author twelve faithful facial expressions and maintain the chibi Slytherin crest.
- [ ] Run edge/halo and joint stress tests, 160px visibility reviews, physical-device memory/decoding measurements.
- [ ] Approve the character's rig appearance at rest and during small trial movement.

**Gate:** an approved character rig with no gaps, detached hands or loss of illustration quality; not merely a passing part-count test.

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
