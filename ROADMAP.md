# Intake Edit roadmap

This is the single current roadmap for unfinished work. Historical plans and trackers are preserved in [the archive](docs/archive/).

## ~~1. Release signing and data safety~~ — deferred

Not planned for now; retained for future reference, not marked complete.

- [ ] Establish a persistent, securely stored Android release signing key and configure the release workflow to use it. Verify that a subsequent release signed with the same key installs as an update and retains app data.
- [ ] Document the migration from prior one-off-signed releases, including their update incompatibility and the risk to local settings, drafts, and companion history. Do not assume an old one-off key can be recovered.
- [ ] Determine and provide a reliable way to preserve or migrate local data before any required reinstall.

## 2. Android performance and interaction polish

Status: the first optimization pass caches chapter and glossary filter results across unrelated recompositions, stops the refresh animation while idle, updates individual English fields without rewriting the chapter, and moves chapter parsing and draft restoration off the UI thread. Representative-device profiling remains outstanding.

- [ ] Profile typing, chapter opening, list refresh, glossary loading, XLIFF import, and the exceptional Whole File editing path on representative devices and chapter files.
- [ ] Address measured input jank, background-work responsiveness, or interaction issues. Revisit spacing, button density, and screen-space use where profiling identifies a need.
- [ ] Verify navigation stays responsive and regression tests and CI pass.

Preserve the existing single-entry typing path, debounced off-main draft persistence, off-main XLIFF processing, and editor state across QA override refreshes.

## ~~3. Companion widget validation~~ — deferred

Not planned for now; retained for future reference, not marked complete.

- [ ] Verify the complete animation sequence, tempo, tap-to-open behavior, mood changes, and reduced-motion behavior on devices and launchers, including light and dark wallpapers.
- [ ] Verify resizing, multiple widgets, app updates, reboot, sleep and resume, and offline loading.
- [ ] Measure memory use, loading performance, and battery consumption; fix issues found and record device and launcher results.

The user confirmed on November 6, 2026 that the frame-fade flicker stopped. That confirmation does not establish completion of the other checks.

## 4. Companion mood transitions

- [x] Review the first-pass in-app transition. The user approved the brief blink transition on November 6, 2026.
- [x] Integrate the approved implementation from [PR #52](https://github.com/skypie0102/intake-edit/pull/52).

The approved transition switches immediately to the selected mood with a brief blink and keeps the character opaque; it does not geometrically morph between poses.

## 5. Additional companion idle animations — next animation priority

Detailed acting, pacing, and sequencing are in the [idle-animation design](docs/COMPANION_IDLE_ANIMATIONS.md). Implement and visually review one sequence at a time, beginning with Happy humming; retain the shared reading, writing, and page-turning actions.

- [ ] Happy: humming to himself; delighted by a passage. **In progress:** both standalone previews have been approved; app and widget integration remains open.
- [ ] Waiting: searching for his place; quietly thinking. **In progress:** standalone previews for both Waiting actions are available in [draft PR #53](https://github.com/skypie0102/intake-edit/pull/53); app and widget integration remains open.
- [ ] Hungry: distracted by food; stomach rumble. **In progress:** the food-thought preview is approved; the stomach-rumble preview is available for review in [draft PR #53](https://github.com/skypie0102/intake-edit/pull/53); app and widget integration remains open.
- [ ] Sad: losing focus; comforting himself with the book. **In progress:** the losing-focus standalone preview is approved and the book-comfort preview is ready for review in [draft PR #53](https://github.com/skypie0102/intake-edit/pull/53); app and widget integration remains open.
- [ ] New: curious peek; getting absorbed in the book.

The remaining actions are planned; the approved standalone previews still need app and widget integration. The separate widget validation work in item 3 remains deferred.
