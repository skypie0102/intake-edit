# Intake Edit

Android editor for the Pure Love x Violation translation workflow.

Current application version: **0.15.29**.

Current development priorities are tracked in the [roadmap](ROADMAP.md); the remaining Android performance work is in the [architecture refactor roadmap](ANDROID_REFACTOR_ROADMAP.md). Companion playback and outstanding launcher checks are tracked in the [companion animation notes](docs/COMPANION_ANIMATION_PLAN.md).

## Workspace sections

The launcher opens a small workspace home with two sections:

- **Chapter Intake** — the authoritative chapter editor, including stable `H1`/`H2`/paragraph cards, local drafts, SDLXLIFF import, controlled inline markup/endnotes, full-file mode, QA findings/Resolve/Override, and GitHub commit/approval flow.
- **Glossary** — human governance for translation terminology.

## Translation companion

The workspace includes a companion that is fed when a chapter is successfully tagged for QA and committed. Each chapter feeds it once, including if the chapter is later returned for correction. The companion is available after a GitHub token and the selected project repository are verified. Its level is based on the selected repository’s approved/completed chapters: every five completed chapters increase its level. Mood and feeding history remain on this device and are kept separate for each project and branch. The companion uses the latest verified project count when offline; a new project needs a successful sync before its companion appears.

To add the companion to your Android home screen, touch and hold an empty area, choose **Widgets**, then find **Translation companion** under Intake Edit. It is designed for a 2×2 space and can be resized where supported. Tap it to open the earliest Pending Review chapter with missing English; if none are missing English, it opens the earliest Pending Review chapter. When the project is not connected, it opens the connection screen. The widget refreshes after successful feedings and around the next local day; it periodically syncs project progress in the background. Android may delay background work when the device is idle. The current companion uses mood-specific animation frames in the app and widget, with mood conveyed visually on a transparent widget. Widget size, placement, and animation behavior depend on the launcher. The app includes mood-specific reactions and shared reading, writing, and page-turning actions. The widget requests the full sequence from a launcher-hosted adapter; timing and behavior depend on the launcher. Android’s reduced-motion setting displays a still pose.

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

For scale, the chapter list no longer loads every editor YAML and QA file during a normal refresh. PureLove publishes a derived `workflow_status/vol-NN.json` file per volume, and Intake Edit fetches one selected-volume index plus the branch head to populate the whole list. The cached list remains visible while refreshing. The app verifies the index against repository history; when it is briefly behind, it keeps the index as a baseline and reloads only chapters changed since that source commit. The full compatibility loader is reserved for missing/malformed indexes or repository-wide status-logic changes. Opening and approving chapters always reload authoritative GitHub files, so the index is only a list cache, never an approval authority.

The chapter editor follows four repository-backed states:

- **Pending Review** — editing is still in progress; semantic QA ignores the chapter.
- **Pending QA** — the user used **Tag for QA & Commit** and a fresh semantic QA pass is required.
- **Ready for Approval** — a current reusable semantic QA pass exists with zero active findings.
- **Approved** — the user explicitly approved the exact committed editor content and the repository finalizer produced matching reader XHTML plus a hash-bound approval artifact.

Semantic QA does not itself approve a chapter and does not generate the final reader XHTML. Every semantic QA finding can be overridden with an audit reason; BLOCKING findings require an additional explicit high-risk warning confirmation in Intake Edit. When a chapter is **Ready for Approval**, Intake Edit shows **Approve Chapter**. In the **Ready** list, an **Approve all** floating button processes every Ready chapter in the currently selected volume with one confirmation. Bulk approval is one real batch: the app validates the selected Ready set and dispatches one uniquely identified `finalize-chapters.yml` run containing every selected chapter and approved content hash. That workflow materializes the chapters sequentially inside one run, validates the complete workspace, and publishes one batch approval commit plus current status indexes. A failure stops the batch before partial approval outputs are pushed.

The finalizer performs deterministic XHTML/endnote materialization and repository validation. If the committed editor content changes after the approval request, the finalizer refuses the approval rather than materializing different content.

The configured GitHub token therefore needs **Contents: write** for chapter/glossary commits and **Actions: write** to dispatch chapter approval.

## SDLXLIFF chapter import

SDLXLIFF whole-XHTML imports preserve translatable block order across `h1`, `h2`, and `p` elements. Numbered chapter-title links are excluded from the translatable H1 text so imported headings align with PureLove's `H1-N` locators.

## Machine translation reference

In a chapter, tap the **Translation reference** button on the bottom bar, then choose **Import Translation** or **Machine Translation**. Machine Translation detects the source language automatically and translates it into English using the Google Translate web service. This is an unofficial integration and does not require an API key. It sends chapter text to Google and needs a network connection. The app packs multiple entries into each request, validates the returned line markers, honors retry guidance, and saves completed entries if a rate limit or connection error interrupts translation; retrying resumes the chapter. Chapters that fit within the request size limit use one request. Google may still rate-limit or change the service.

The first complete translation is saved locally for the project, chapter, and source revision. Open the translation reference dialog and choose Hide; choose Show saved translation to restore it without sending a new request. An imported XLIFF reference remains separate and can be selected in the same dialog. Machine translations are references and do not change the English fields until explicitly applied.

## Chapter draft safety

Chapter edits retain the existing atomic local-draft system. Drafts are restored only when their base GitHub SHA still matches the remote chapter, preventing a stale local draft from silently overwriting a newer remote revision.

## CI artifact

Every push runs unit tests and builds a debug APK. The GitHub Actions artifact is named:

`intake-edit-debug-apk`

