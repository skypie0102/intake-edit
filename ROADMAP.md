# Intake Edit roadmap

This roadmap tracks the current companion-animation revamp. Items are planned unless explicitly marked complete. It does not replace the project workflow or the implementation details in the [companion animation plan](docs/COMPANION_ANIMATION_PLAN.md).

## Current priority: companion animation revamp

The current public release is **v0.15.27**. The separate vector preview in [PR #47](https://github.com/skypie0102/intake-edit/pull/47) is a prototype, not an accepted fix: the user reported flickering and an extra arm while writing. The user later confirmed that its widget does appear under the preview app's different name. Do not merge the prototype as the final animation implementation without resolving and validating the animation reports.

### Phase 0 — reproduce and define the baseline

- [ ] Reproduce flickering in the in-app companion and, separately, in a registered widget; record device, Android version, launcher, wallpaper, state, and whether the effect occurs at an action or frame boundary.
- [x] User confirmed that the earlier preview widget is discoverable under the preview app's name; the earlier missing-entry report was withdrawn.
- [ ] Give the new rig preview widget a distinct name and verify it can be added beside the production widget; inspect provider and lifecycle behavior on the target launcher.
- [ ] Capture the current vector art, mood thresholds, source-of-progress rules, widget appearance and navigation behavior as regression baselines.
- [ ] Profile the current animation's APK size, frame rendering, memory, and background behavior; record measurements rather than assuming vectors are always cheaper.

**Exit:** The reported issues can be reproduced or have a documented device-specific investigation, and the current behavior is recorded.

### Phase 1 — build the character rig and first action

**In progress:** the user approved the five-state concept, including a new-state exclamation mark, waiting-state question mark, visible book pages, bookmark below the book, and a body hidden behind the book. The waiting thinking preview received a positive visual review. New-state greeting, happy-state writing, and hungry-state food thought and sigh, and sad-state glance and tear are available for review through the downloadable preview pack; device and launcher validation remains open.

- [ ] Separate the approved SVG artwork into named, layered parts with stable pivots and attachment points for the head, eyes, monocle, arms, book, page, pen, and mood symbols.
- [ ] Make each hand a single continuous limb; attach the monocle to the head and the pen to the writing hand. Define draw order and clipping against the book.
- [ ] Implement a restrained base idle and one complete, smooth action in the app, without fading the entire image between poses.
- [ ] Validate that the action starts and ends at the same resting pose, has no extra limbs or detached props, and is legible at widget size.

**Exit:** One rigged action has passed visual review, including light/dark backgrounds and every intermediate pose; remaining actions use this foundation.

### Phase 2 — mood-specific animation and actions

- [ ] Implement the choreography for **NEW, HAPPY, WAITING, HUNGRY, and SAD** described in the plan, including smooth, bounded transitions between states.
- [ ] Implement thinking, page turning, and writing as complete actions with clear anticipation, movement, hold, and recovery.
- [ ] Add varied pauses and sequencing so actions do not repeat mechanically; keep motions appropriate to each mood and respect reduced-motion settings where available.
- [ ] Preserve the distinction between a feeding event and repository-backed level progress; animation must not change project data or chapter state.

**Exit:** Every mood and action is visually reviewed, no animation creates or removes character parts, and existing project/feeding behavior is unchanged.

### Phase 3 — widget playback and lifecycle

- [ ] Implement the best supported vector-first widget playback method after device testing; the launcher-hosted widget may need a different rendering strategy from the app.
- [ ] Keep the widget transparent and text-free, designed for 2×2, with no visible level, counters, status, or background/border.
- [ ] Preserve the tap-to-next-translation-chapter behavior and the blank/unavailable companion before a project and token are verified.
- [ ] Verify behavior when the launcher pauses or recreates the widget, the device sleeps, the app is updated, the widget is resized, or multiple widget instances exist.
- [ ] If full continuous vector animation cannot run reliably in a launcher, provide a graceful, visually consistent fallback; do not claim full animation support without observing it.

**Exit:** The widget appears under the correct app, is usable on target launchers, and maintains the agreed visual and navigation behavior.

### Phase 4 — validation and delivery

- [ ] Add meaningful automated checks for rig structure, pose continuity, required SVG/vector assets, mood-to-animation mappings, and widget registration/layout.
- [ ] Visually verify every mood and action in the app and on the user's Android launcher; inspect both intermediate poses and full-speed playback for flicker, ghosting, extra limbs, and disconnected accessories.
- [ ] Test 2×2 and resized widgets on light/dark wallpapers, on fresh setup and an existing connected project, with multiple instances where supported.
- [ ] Measure release APK size and representative rendering/memory/battery behavior against the Phase 0 baseline; document results and any launcher limitations.
- [ ] Build a clearly named side-by-side preview with a distinct widget entry, validate it with the user, then merge and publish a release only after the agreed checks pass.

**Exit:** User-confirmed animation and widget behavior, passing checks, and a release with accurately reported size and signing/install consequences.
