# Intake Edit

Android editor for the Pure Love x Violation translation workflow.

Current application version: **0.15.11**.

## Workspace sections

The launcher opens a small workspace home with two sections:

- **Chapter Intake** — the authoritative chapter editor, including stable `H1`/`H2`/paragraph cards, local drafts, SDLXLIFF import, controlled inline markup/endnotes, full-file mode, QA findings/Resolve/Override, and GitHub commit/approval flow.
- **Glossary** — human governance for translation terminology.

## Glossary governance

The Glossary section reads the master glossary state from the configured translation repository.

It supports:

- viewing/searching approved effective glossary entries;
- reviewing agent-generated translation-time suggestions;
- editing a suggestion before deciding;
- approving or rejecting suggestions;
- manually adding glossary entries;
- editing approved entries;
- approving/removing `qa_lock` and case-sensitive lock metadata.

Agent suggestions are stored in `glossary/proposals.json`. Approved additions go to `glossary/approved_additions.json`; edits/lock metadata go to `glossary/governance.json`. Restored Volume 1–5 base glossary files remain under `glossary/full/` in the translation repository.

The agent proposes; the human editor decides what becomes authoritative.

## Chapter workflow and approval

The chapter selection page keeps the four workflow filters **Active / Pending QA / Ready / Completed** visible in a horizontal bar at the top. Search and volume controls remain under the filter icon.

For scale, the chapter list no longer loads every editor YAML and QA file during a normal refresh. PureLove publishes a derived `workflow_status/vol-NN.json` file per volume, and Intake Edit fetches one selected-volume index plus the branch head to populate the whole list. The cached list remains visible while refreshing. The app verifies that the index matches the current repository status-refresh commit; if the index is missing, malformed, or still catching up, it falls back to the older bounded-concurrency per-chapter loader. Opening and approving chapters always reload authoritative GitHub files, so the index is only a list cache, never an approval authority.

The chapter editor follows four repository-backed states:

- **Pending Review** — editing is still in progress; semantic QA ignores the chapter.
- **Pending QA** — the user used **Tag for QA & Commit** and a fresh semantic QA pass is required.
- **Ready for Approval** — a current reusable semantic QA pass exists with zero active findings.
- **Approved** — the user explicitly approved the exact committed editor content and the repository finalizer produced matching reader XHTML plus a hash-bound approval artifact.

Semantic QA does not itself approve a chapter and does not generate the final reader XHTML. When a chapter is **Ready for Approval**, Intake Edit shows **Approve Chapter**. In the **Ready** list, an **Approve all** floating button processes every Ready chapter in the currently selected volume with one confirmation. Bulk approval is strictly sequential: the app reloads and validates the first chapter, dispatches its uniquely identified `finalize-chapter.yml` run, monitors that exact GitHub Actions run until it completes, and only then dispatches the next chapter. A completed failed/cancelled run is reported and the queue may continue; if the app cannot confirm that a dispatched run has finished, the remaining queue stops so finalizers cannot overlap.

The finalizer performs deterministic XHTML/endnote materialization and repository validation. If the committed editor content changes after the approval request, the finalizer refuses the approval rather than materializing different content.

The configured GitHub token therefore needs **Contents: write** for chapter/glossary commits and **Actions: write** to dispatch chapter approval.

## SDLXLIFF chapter import

SDLXLIFF whole-XHTML imports preserve translatable block order across `h1`, `h2`, and `p` elements. Numbered chapter-title links are excluded from the translatable H1 text so imported headings align with PureLove's `H1-N` locators.

## Chapter draft safety

Chapter edits retain the existing atomic local-draft system. Drafts are restored only when their base GitHub SHA still matches the remote chapter, preventing a stale local draft from silently overwriting a newer remote revision.

## CI artifact

Every push runs unit tests and builds a debug APK. The GitHub Actions artifact is named:

`intake-edit-debug-apk`
