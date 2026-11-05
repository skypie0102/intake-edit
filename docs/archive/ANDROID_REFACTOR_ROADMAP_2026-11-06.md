# Android architecture refactor roadmap

> Archived on November 6, 2026. This is a historical snapshot; current work is tracked only in the [current roadmap](../../ROADMAP.md).

Milestones 1–6 are complete. Their goals, implementation scope, and acceptance criteria are preserved in the [completed milestones archive](ANDROID_REFACTOR_MILESTONES_1_6.md). Milestone 7 remains open pending representative-device profiling and any fixes justified by the results.

## Milestone 7 — UI polish and performance verification

**Goal:** capitalize on the cleaner architecture.

- Profile typing, chapter opening, list refresh, glossary loading, XLIFF import, and the exceptional Whole File path on representative devices.
- Keep normal card typing on the single-entry raw-patch path instead of rewriting every English field for each character.
- Keep local draft persistence debounced/off-main and XLIFF cache/read/XML parse/save/remove work off the main thread.
- Preserve editor UI state across QA override refreshes instead of recreating the editor subtree.
- Revisit spacing, button density, and screen-space usage only after device profiling identifies remaining interaction issues.

**Acceptance:** no obvious input jank on representative chapter files; navigation remains responsive during background work; regression tests and CI stay green.

