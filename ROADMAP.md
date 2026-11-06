# Intake Edit roadmap

This is the single current roadmap for unfinished work. Historical plans and trackers are preserved in [the archive](docs/archive/).

## ~~1. Release signing and data safety~~ — deferred

Not planned for now; retained for future reference, not marked complete.

- [ ] Establish a persistent, securely stored Android release signing key and configure the release workflow to use it. Verify that a subsequent release signed with the same key installs as an update and retains app data.
- [ ] Document the migration from prior one-off-signed releases, including their update incompatibility and the risk to local settings, drafts, and companion history. Do not assume an old one-off key can be recovered.
- [ ] Determine and provide a reliable way to preserve or migrate local data before any required reinstall.

## 2. Android performance and interaction polish

Status: the first optimization pass caches chapter and glossary filter results across unrelated recompositions, stops the refresh animation while idle, updates individual English fields without rewriting the chapter, and moves chapter parsing and draft restoration off the UI thread. The next pass loads a consistent glossary snapshot with bounded concurrent requests, moves its parsing and indexing off the UI thread, reuses editor derived data across unrelated recompositions, and cancels obsolete volume refreshes without starting a stale compatibility fallback. Physical-device testing and profiling are deferred at the user's request; revisit them if issues are reported.

- [ ] Deferred: profile typing, chapter opening, list refresh, glossary loading, XLIFF import, and Whole File editing on representative Android devices and chapter files.
- [ ] Deferred: investigate device performance or interaction issues if reported, including spacing and screen-space use where needed.
- [x] Run available regression tests and Android CI builds.\n- [ ] Deferred: verify navigation responsiveness on a physical Android device.

Preserve the existing single-entry typing path, debounced off-main draft persistence, off-main XLIFF processing, and editor state across QA override refreshes.

## ~~3. Companion widget validation~~ — deferred

Not planned for now; retained for future reference, not marked complete.

- [ ] Verify the complete animation sequence, tempo, tap-to-open behavior, mood changes, and reduced-motion behavior on devices and launchers, including light and dark wallpapers.
- [ ] Verify resizing, multiple widgets, app updates, reboot, sleep and resume, and offline loading.
- [ ] Measure memory use, loading performance, and battery consumption; fix issues found and record device and launcher results.

The user confirmed on November 6, 2026 that the frame-fade flicker stopped. That confirmation does not establish completion of the other checks.

Completed companion transitions and idle animations are documented in the [animation milestone archive](docs/archive/COMPANION_ANIMATION_MILESTONE_2026-11.md). Device and launcher validation remains deferred under item 3.
