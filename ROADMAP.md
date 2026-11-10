# Intake Edit roadmap

**Active initiative (opened 2026-11-10): replace the Book Spirit with the user's blonde witch chibi mascot.** The approved direction uses the supplied character sheet and original illustration; it requires a new layered character rig, not a reskin of the Book Spirit. See the [Mascot v2 design bible](docs/MASCOT_V2_DESIGN.md) and [machine-readable rig contract](artwork/mascot-v2/rig-contract.json). **The existing Book Spirit remains in production until the new character is complete.**

## Phase 0 — audit and safe foundation

- [x] Audit the app's rig, SVG→frame generation, preview pipeline, in-app renderer and 2×2 widget.
- [x] Establish the design bible, five-mood acting matrix, non-regression constraints and separate v2 manifest.
- [x] Add contract validation to CI without changing the production animation pack.
- [ ] Import the two **original full-resolution** reference images into the art repository; verify SHA-256 digests and preserve pristine copies.

**Gate:** documented canonical source, validated manifest and unchanged old rendering.

## Phase 1 — master artwork and layered vector rig (RESET: visual work rejected)

**2026-11-10 quality decision:** The first-pass artwork in merged PR #62 was rejected by the user because it did not resemble the approved character. Its code-drawn SVG puppet, expression files and pose studies were removed from the active repository. **No Phase 1 artwork deliverable is accepted.** The 31-part list and animation contract describe requirements only, not completed art.

**Revised quality-first workflow:** Start from the user-approved high-detail character reference and Slytherin-crest sheet. Establish a faithful, high-resolution *isolated* master of the character with the same face, hair, eye shapes, uniform proportions, linework and shading. Visually compare full-size and 160px renders against the approved original before cutting layers. Once approved, separate/redraw occluded joints and hair/robe overlaps for rigging; verify full pose ranges, then export to an appropriate editable vector/layered format. Do not generate a simplified cartoon from hand-guessed SVG geometry. Structural tests and Android CI cannot substitute for character likeness or human visual approval.



- [ ] Draw neutral front/3/4/side/back turnarounds from the user's images (not a cropped screenshot).
- [ ] Draw independently movable back/front hair, face, brows, eyes/blinks, mouth, shoulders, arm segments, hands, torso, robe, skirt, legs and footwear, including overlapping fills/masks.
- [ ] Reproduce green-and-black outfit, serpent crest, blonde hair, gray eyes and the 12 labeled expressions.
- [ ] Export 320×320 alpha master rig, pose contact sheet and 160×160 visibility check; inspect character fidelity at both sizes.
- [ ] Add rig tests for pivots, continuity, supported SVG features and unclipped silhouettes.

**Gate:** independently movable and recognizable character with no visible joints or seams through test poses.

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
