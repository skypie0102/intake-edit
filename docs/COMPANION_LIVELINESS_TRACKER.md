# Companion animation: liveliness tracker

Work in progress on `preview/companion-rig-first-action`. The five rigs are preview work; device and launcher validation is still outstanding. Check an item only when implemented and visually reviewed at widget size.

## First pass: Waiting

- [ ] Subtle breathing with a stable book and connected face, eyes and monocle. **In progress:** generated preview adds a gentle body rise and fall; intermediate poses reviewed, device playback pending.
- [ ] Blink naturally and vary pauses; avoid a predictable short loop. **In progress:** scripted blinks include a double blink. Preview playback now inserts variable idle intervals and separately timed blinks at rest before each action; blink choreography inside the action is still fixed.
- [ ] Glance at the page, pause, and look up before the existing sequential thinking dots and drawn question mark. **In progress:** the gaze now lowers and returns more slowly, with a brief hold. The preview runs a longer sequence with reading time; intermediate poses were inspected on a dark background. Full-speed and device review remain open.
- [ ] Add small posture shifts and delayed tuft and bookmark follow-through without visible detachment. **In progress:** the tuft and bookmark movement has been increased again after feedback that it was still not noticeable. The preview uses a faster, wider sway, with the ribbon pivoting around its hidden attachment; user and device review remain open. Device review and posture shifts remain open.
- [ ] Review intermediate poses and playback at actual in-app and 2×2 widget sizes. **In progress:** sampled neutral and closed-eye poses were rendered on light and dark backgrounds; launcher playback remains unverified.

## Shared animation and rigging

- [ ] Use restrained idle movement so the character is present without constant bouncing.
- [ ] Vary blink, glance, action and idle timing; provide a few spaced variations instead of synchronized repetition. **In progress:** the new preview player inserts randomized idle intervals between actions, returning to the neutral pose before each action. Resting blinks are independently timed; action choreography remains fixed. Widget playback remains a repeating, launcher-controlled sequence.
- [ ] Lead actions with the eyes, followed by head and body; use anticipation, readable holds, recovery and follow-through. **In progress:** all five preview moods have a small anticipatory posture shift and recovery; hungry and sad move less. Tuft and bookmark follow-through is less uniform, and Waiting has a spaced double blink. Sampled poses were reviewed on alternating light and dark backgrounds. The preview view box was expanded slightly after edge clipping was detected; 24 sampled frames per mood now render with a transparent outer edge at 160 px. Full-motion and device review remain open.
- [ ] Animate compatible transitions between moods, including when an action is interrupted; keep status changes responsive. **In progress:** the preview finishes the current action quickly, closes the eyes at a neutral pose, then changes to the latest requested mood and returns the new state to rest. There is no whole-scene fade. Expression and limb shapes still switch while the eyes are closed; this is not continuous path interpolation. The widget does not yet use these transitions.
- [ ] Keep eyes, eyelids, head/body, tuft, arms, book, pen, and bookmark independently controllable but connected; preserve pivots, attachment, overlaps and drawing order.
- [ ] Explore restrained soft body deformation without warping the face or silhouette.
- [ ] Keep the book, visible page lines, hanging bookmark and gold monocle consistent; avoid whole-character fades or extra limbs.
- [ ] Preserve a stable resting pose and support reduced motion; measure performance and power use. **In progress:** the preview app and widget use a resting frame while Android animations are disabled; the widget applies that setting at its next refresh. Device behavior and performance remain unverified.

## State-specific acting

- [ ] **New:** read with head and eyes lowered, scanning the page left to right and back; lift his head and center his gaze toward the viewer; show surprise and the exclamation mark together; switch directly to a smile as the mark disappears; wave, lower his hand and return to reading. **In progress:** this sequence is implemented in the preview; user feedback and device review remain open.
- [ ] **Waiting:** read, look puzzled, and retain the sequential dots and question mark; use the polished idle as the basis for other states.
- [ ] **Happy:** look at the page, write several strokes with pauses and a longer mark, then smile with twinkling stars. Keep the pen aligned with the hand and writing; do not add a finger grip. **In progress:** the preview adds a longer writing sequence with a pause between strokes, a resting hand, blinking and tuft/bookmark movement; user and device review remain open.
- [ ] **Hungry:** retain a weary, downturned expression; imagine steaming food and settle with a gentle sigh. Do not introduce a smile. **In progress:** longer food-thought and sigh preview, with slower body, tuft and ribbon movement; rendered poses reviewed, user and device review remain open.
- [ ] **Sad:** retreat toward the book and occasionally glance up; use the tear sparingly. **In progress:** longer preview with a more visible upward glance, retreat, single tear, and softer tuft/bookmark movement; sampled poses reviewed on a dark background; the earlier 96-frame pack was regenerated and matched the published file. User and device review remain open.

## Validation and rollout

- [ ] Review every state and transition on light and dark backgrounds, in motion and at actual device/widget size; check clipping, flicker, snapping, jitter, prop contact and repetition.
- [ ] Test app and launcher behavior, resizing, sleep/resume and multiple widgets; record device, launcher, performance and known limitations. **In progress:** the preview widget frame interval now matches the duration of the 96-frame pack when sampled into 16 frames. Launcher playback, reduced motion, resizing and power use remain unverified.
- [ ] Preserve the existing project, feeding, progress and chapter-opening behavior. Keep preview and production separate until the revamp is accepted.
- [ ] Update this tracker with implementation and review evidence as work lands.

See [the animation revamp plan](COMPANION_ANIMATION_PLAN.md) for existing behavior, constraints and acceptance criteria.


