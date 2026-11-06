# Intake Edit roadmap

This is the single current roadmap. Completed plans and superseded roadmaps are in [the archive](docs/archive/). Priorities below reflect the app-wide review; device testing and the previously deferred release-signing and widget-validation work remain deferred.

## 1. Draft safety and recovery

- [x] Report draft-save failures, retain unsaved edits, and allow retry before closing.
- [x] Report draft deletion failures without misreporting successful remote commits.
- [x] Reopen incomplete Whole File drafts in a repairable raw editor without losing their contents.
- [x] Preserve drafts whose base revision changed and provide an editor view to inspect and copy them.
- [x] Add an export flow for preserved drafts.
- [ ] Verify draft recovery across app restarts in automated Android tests.
- [x] Isolate drafts by repository, branch, and chapter. Keep older unassigned drafts available for explicit inspection and recovery; never guess their project.
- [ ] Add regression tests for failed writes, interrupted editing, stale drafts, and recovery.

## 2. Complete chapter lists and reliable QA state

- [x] Replace the 1,000-entry-limited GitHub Contents fallback with a tree-based listing; reject truncated trees and test large volumes.
- [x] Distinguish missing QA data from network, permission, and parse errors; report failures and let the user retry opening the chapter.
- [x] Isolate imported translation references by project and branch; keep older unassigned references available for explicit recovery.

## 3. Network resilience and interaction clarity

- [x] Distinguish offline, timeout, rate-limit, permissions, and stale-index failures and explain how to retry.
- [x] Show whether chapter status comes from cache, the workflow index, or direct refresh, and explain index fallback.
- [ ] Review editing, navigation, and approval interactions for clear feedback and recovery.

## 4. Accessibility and automated Android interaction tests

- [ ] Add emulator-based tests for chapter opening, editing, view switching, QA, and navigation where feasible.
- [ ] Review screen-reader labels, keyboard focus, large text, and compact screens; address issues found.
- [ ] Keep the automated unit, animation, and Android build checks passing.

## 5. Further performance investigation

- [ ] Investigate large-chapter QA calculations, import alignment, cache size, and companion memory use; measure before claiming improvements.
- [ ] Preserve the current single-entry typing path, background parsing and draft persistence, and full companion animations.
- [ ] Deferred at the user's request: physical-device profiling and interaction testing; revisit if problems are reported.

## Deferred: release signing and data safety

Not planned for now; not complete. Establish a persistent release key, verify update compatibility, document one-off-key migration, and determine a reliable way to preserve local data before any required reinstall.

## Deferred: companion widget validation

Not planned for now; not complete. Validate animation sequences, speed, interactions, mood changes, accessibility, resizing, launchers, memory, battery, reboot, and offline behavior on devices. The user confirmed that the earlier frame-fade flicker stopped.

Completed companion transitions and idle animations are documented in the [animation milestone archive](docs/archive/COMPANION_ANIMATION_MILESTONE_2026-11.md). Additional idle designs remain a [reference](docs/COMPANION_IDLE_ANIMATIONS.md).
