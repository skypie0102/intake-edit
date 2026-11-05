# Companion animation: liveliness tracker

Work in progress on `preview/companion-rig-first-action`. The five rigs are preview work; device and launcher validation is still outstanding. Check an item only when implemented and visually reviewed at widget size.

## First pass: Waiting

- [ ] Subtle breathing with a stable book and connected face, eyes and monocle. **In progress:** generated preview adds a gentle body rise and fall; intermediate poses reviewed, device playback pending.
- [ ] Blink naturally and vary pauses; avoid a predictable short loop. **In progress:** two blinks are spaced across the longer preview sequence; it still repeats deterministically.
- [ ] Glance at the page, pause, and look up before the existing sequential thinking dots and drawn question mark. **In progress:** the gaze now lowers and returns more slowly, with a brief hold. The preview runs a longer sequence with reading time; intermediate poses were inspected on a dark background. Full-speed and device review remain open.
- [ ] Add small posture shifts and delayed tuft and bookmark follow-through without visible detachment. **In progress:** the tuft and bookmark movement has been increased again after feedback that it was still not noticeable. The preview uses a faster, wider sway, with the ribbon pivoting around its hidden attachment; user and device review remain open. Device review and posture shifts remain open.
- [ ] Review intermediate poses and playback at actual in-app and 2×2 widget sizes.

## Shared animation and rigging

- [ ] Use restrained idle movement so the character is present without constant bouncing.
- [ ] Vary blink, glance, action and idle timing; provide a few spaced variations instead of synchronized repetition.
- [ ] Lead actions with the eyes, followed by head and body; use anticipation, readable holds, recovery and follow-through.
- [ ] Animate compatible transitions between moods, including when an action is interrupted; keep status changes responsive.
- [ ] Keep eyes, eyelids, head/body, tuft, arms, book, pen, and bookmark independently controllable but connected; preserve pivots, attachment, overlaps and drawing order.
- [ ] Explore restrained soft body deformation without warping the face or silhouette.
- [ ] Keep the book, visible page lines, hanging bookmark and gold monocle consistent; avoid whole-character fades or extra limbs.
- [ ] Preserve a stable resting pose and support reduced motion; measure performance and power use.

## State-specific acting

- [ ] **New:** dip behind the book, peek up, notice the viewer, show the exclamation mark, and settle with secondary motion. **In progress:** the preview adds a dip and peek, a brief surprised expression that resolves into a smile, and a single hand that raises, waves visibly and lowers to rest. Viewer-directed eye movement, user feedback and device review remain open.
- [ ] **Waiting:** read, look puzzled, and retain the sequential dots and question mark; use the polished idle as the basis for other states.
- [ ] **Happy:** look at the page, write several strokes with pauses and a longer mark, then smile with twinkling stars. Keep the pen aligned with the hand and writing; do not add a finger grip.
- [ ] **Hungry:** retain a weary, downturned expression; imagine steaming food and settle with a gentle sigh. Do not introduce a smile.
- [ ] **Sad:** retreat toward the book and occasionally glance up; use the tear sparingly.

## Validation and rollout

- [ ] Review every state and transition on light and dark backgrounds, in motion and at actual device/widget size; check clipping, flicker, snapping, jitter, prop contact and repetition.
- [ ] Test app and launcher behavior, resizing, sleep/resume and multiple widgets; record device, launcher, performance and known limitations.
- [ ] Preserve the existing project, feeding, progress and chapter-opening behavior. Keep preview and production separate until the revamp is accepted.
- [ ] Update this tracker with implementation and review evidence as work lands.

See [the animation revamp plan](COMPANION_ANIMATION_PLAN.md) for existing behavior, constraints and acceptance criteria.
