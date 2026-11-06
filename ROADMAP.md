# Intake Edit roadmap

This is the single current roadmap. Completed plans and superseded roadmaps are in [the archive](docs/archive/). Priorities below reflect the app-wide review; device testing and the previously deferred release-signing and widget-validation work remain deferred.

## 1. Draft safety and recovery

- [x] Report draft-save failures, retain unsaved edits, and allow retry before closing.
- [ ] Report draft deletion failures without misreporting successful remote commits.
- [x] Reopen incomplete Whole File drafts in a repairable raw editor without losing their contents.
- [x] Preserve drafts whose base revision changed and provide an editor view to inspect and copy them.
- [ ] Add an export flow for preserved drafts and verify recovery across app restarts.
- [ ] Isolate drafts by repository, branch, and chapter, with a safe migration for existing drafts.
- [ ] Add regression tests for failed writes, interrupted editing, stale drafts, and recovery.

## 2. Complete chapter lists and reliable QA state

- [ ] Replace the 1,000-entry-limited GitHub Contents fallback with a complete listing strategy; detect truncation and test large volumes.
- [ ] Distinguish missing QA data from network, permission, and parse errors; offer retry and keep findings from disappearing silently.
- [ ] Isolate imported translation references by project and branch, with safe migration.

## 3. Network resilience and interaction clarity

- [ ] Distinguish offline, rate-limit, permissions, stale-index, and other failures; offer useful retry actions.
- [ ] Show whether chapter status comes from cache or a fresh index and explain incomplete refreshes.
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
