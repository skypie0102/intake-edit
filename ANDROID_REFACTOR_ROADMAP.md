# Android architecture refactor roadmap

The goal is to make Intake Edit feel immediate on-device while preserving the existing schema-v5 workflow, draft safety, QA semantics, glossary governance, and GitHub commit behavior.

## Current status

- ✅ Milestone 1 — editor responsiveness and draft persistence: complete.
- ✅ Milestone 2 — single-activity navigation shell: complete.
- ✅ Milestone 3 — ViewModel/state extraction: complete for Chapter Intake, editor, repository settings, and glossary.
- ✅ Milestone 4 — repository boundaries: chapter, glossary, and local-draft workflows are behind injectable data boundaries with fake-backed ViewModel tests; the small settings/token store remains intentionally cohesive rather than wrapped for symmetry.
- ✅ Milestone 5 — chapter-list loading/cache: repository paths and cached progress render before network progress completes; base English/review progress is published before QA counts; progress/QA work is bounded to four concurrent requests; cache writes are debounced; cached rows stay interactive during background refresh.
- ✅ Milestone 6 — glossary decomposition: the old `HomeActivity` controller has been removed; glossary load/mutation logic is behind `GlossaryViewModel`/`GlossaryRepository`; UI cards/dialogs are split; export serialization/file writing runs off the main thread while the document picker remains UI-owned.
- 🟡 Milestone 7 — UI polish and performance verification: the main structural/runtime hot paths have been reduced; representative-device profiling and final interaction polish remain.

## Principles

- Refactor incrementally; no full rewrite.
- Keep parser/domain behavior stable while changing UI/state ownership.
- Preserve local-draft recovery and SHA conflict protection throughout.
- Keep every milestone buildable and releasable.
- Add tests around extracted state/repository behavior before removing old paths.

## Milestone 1 — Editor responsiveness and draft persistence

**Goal:** remove avoidable main-thread work from the typing path.

- Separate in-memory editor updates from local draft persistence.
- Debounce routine draft writes instead of writing on every keystroke.
- Run draft file I/O off the main thread where possible.
- Flush the current draft on explicit editor exit and before risky transitions.
- Keep commit behavior and stale-base-SHA protection unchanged.

**Acceptance:** typing no longer causes one disk write per character; immediately leaving the editor still preserves the latest draft; commit success still removes the local draft.

## Milestone 2 — Single-activity navigation shell

**Goal:** make navigation predictable and remove split Activity ownership.

- Introduce one application Activity and explicit screen destinations for Home, Chapter Intake, Editor, Glossary, and Settings.
- Replace `HomeActivity -> MainActivity` launching with in-app navigation.
- Centralize back behavior and post-commit return-to-home behavior.
- Keep deep state local to its screen rather than the Activity.

**Acceptance:** all major surfaces live under one Activity; Review & Commit returns through navigation rather than Activity handoff; back behavior is consistent.

## Milestone 3 — ViewModel/state extraction

**Goal:** move orchestration out of composables.

- Add `ChapterListViewModel`, `EditorViewModel`, `GlossaryViewModel`, and settings state where useful.
- Represent each screen with an immutable UI-state model and explicit actions.
- Replace broad global `busy` flags with operation-specific state.
- Keep Compose functions mostly declarative and preview/test friendly.

**Acceptance:** network/storage mutations are not defined inside large composables; screen state survives normal recreation; unrelated controls stay usable during independent operations.

## Milestone 4 — Repository boundaries

**Goal:** give UI state a stable data layer.

- Keep chapter GitHub/parser workflows behind `ChapterRepository`.
- Keep glossary GitHub/parser workflows behind `GlossaryRepository`.
- Keep local editor-draft persistence behind `DraftRepository` so editor state does not own Android file-store mechanics.
- Keep `GitHubApi`, parsers, and concrete stores as lower-level implementations.
- Make repository APIs easy to fake in unit tests.
- Leave settings/token persistence as a small cohesive settings concern rather than adding a wrapper that provides no additional isolation.

**Acceptance:** chapter/editor/glossary ViewModels do not directly coordinate raw GitHub/parser/disk implementations in the same action; their data dependencies can be replaced with fakes in unit tests.

## Milestone 5 — Chapter-list loading and cache

**Goal:** make the homepage useful immediately.

- Render the chapter list as soon as repository paths or a local cache are available.
- Persist chapter progress metadata locally per repository/branch/root.
- Refresh progress with bounded concurrency rather than launching an unbounded request pair per chapter.
- Publish English/review progress first, then layer QA counts in as a second-stage update.
- Keep cached rows interactive while background refresh is running.
- Preserve manual refresh and conflict correctness.

**Acceptance:** opening Chapter Intake does not require every chapter/QA request to finish before the list is useful; cached rows render before remote refresh completes; refresh work is bounded and visible without locking unrelated list actions.

## Milestone 6 — Glossary decomposition

**Goal:** remove the second large controller-composable.

- Move glossary loading, proposal mutation, approval, and export preparation behind state/actions.
- Split proposal and approved-entry surfaces into smaller composables.
- Preserve atomic multi-file approval commits.
- Keep Android document-picker ownership in Compose while running export serialization/file writing on `Dispatchers.IO`.

**Acceptance:** glossary business workflows are testable without rendering Compose UI, and export file work does not block the main thread.

## Milestone 7 — UI polish and performance verification

**Goal:** capitalize on the cleaner architecture.

- Profile typing, chapter opening, list refresh, glossary loading, XLIFF import, and the exceptional Whole File path on representative devices.
- Keep normal card typing on the single-entry raw-patch path instead of rewriting every English field for each character.
- Keep local draft persistence debounced/off-main and XLIFF cache/read/XML parse/save/remove work off the main thread.
- Preserve editor UI state across QA override refreshes instead of recreating the editor subtree.
- Revisit spacing, button density, and screen-space usage only after device profiling identifies remaining interaction issues.

**Acceptance:** no obvious input jank on representative chapter files; navigation remains responsive during background work; regression tests and CI stay green.

## Delivery order

Milestones 1–6 are complete. Milestone 7 is now a focused device-verification/polish pass: validate the optimized typing, chapter-list, glossary, QA, and XLIFF flows on representative hardware, then address only measured remaining issues rather than doing another broad rewrite.
