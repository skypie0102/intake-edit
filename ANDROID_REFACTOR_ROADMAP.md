# Android architecture refactor roadmap

The goal is to make Intake Edit feel immediate on-device while preserving the existing schema-v5 workflow, draft safety, QA semantics, glossary governance, and GitHub commit behavior.

## Current status

- ✅ Milestone 1 — editor responsiveness and draft persistence: complete.
- ✅ Milestone 2 — single-activity navigation shell: complete.
- ✅ Milestone 3 — ViewModel/state extraction: complete for Chapter Intake, editor, repository settings, and glossary.
- 🟡 Milestone 4 — repository boundaries: chapter and glossary GitHub/parser workflows are behind injectable repositories; draft/settings stores remain intentionally small lower-level dependencies.
- 🟡 Milestone 5 — chapter-list loading/cache: chapter paths render before progress completes, existing in-memory progress is retained during refresh, and progress requests are bounded to four concurrent loads. Persistent progress cache and staged/lazy QA counts remain.
- 🟡 Milestone 6 — glossary decomposition: the old `HomeActivity` controller has been removed; glossary load/mutation logic is behind `GlossaryViewModel`/`GlossaryRepository`, and UI cards/dialogs are split. Export file I/O remains UI-owned.
- ⬜ Milestone 7 — UI polish and performance verification: pending after the remaining loading/cache work.

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

- Introduce repositories for chapters, drafts, glossary, and settings/auth concerns.
- Keep `GitHubApi`, parsers, and stores as lower-level implementations.
- Centralize error mapping and conflict handling.
- Make repository APIs easy to fake in unit tests.

**Acceptance:** ViewModels do not directly coordinate raw GitHub calls, parser calls, and disk stores in the same action.

## Milestone 5 — Chapter-list loading and cache

**Goal:** make the homepage useful immediately.

- Render the chapter list as soon as repository paths are known.
- Cache progress metadata locally.
- Refresh progress with bounded concurrency rather than launching an unbounded request pair per chapter.
- Load QA counts lazily or separately from the minimum chapter-list metadata.
- Preserve manual refresh and conflict correctness.

**Acceptance:** opening Chapter Intake does not require every chapter/QA request to finish before the list is useful; refresh work is bounded and visible.

## Milestone 6 — Glossary decomposition

**Goal:** remove the second large controller-composable.

- Move glossary loading, proposal mutation, approval, and export preparation behind state/actions.
- Split proposal and approved-entry surfaces into smaller composables.
- Preserve atomic multi-file approval commits.

**Acceptance:** glossary business workflows are testable without rendering Compose UI.

## Milestone 7 — UI polish and performance verification

**Goal:** capitalize on the cleaner architecture.

- Profile typing, chapter opening, list refresh, and glossary loading.
- Reduce unnecessary recompositions and large object copying where measurements justify it.
- Add loading/error affordances tied to specific operations.
- Revisit spacing, button density, and screen-space usage after structural work is complete.

**Acceptance:** no obvious input jank on representative chapter files; navigation remains responsive during background work; regression tests and CI stay green.

## Delivery order

Milestones 1–3 are complete. Finish the remaining Milestone 4–6 edges next, then use Milestone 7 as the verification/polish pass rather than a place to hide architectural fixes.
