# Intake Edit

Android editor for the Pure Love x Violation translation workflow.

Current application version: **0.15.20**.

## Workspace sections

The launcher opens a small workspace home with two sections:

- **Chapter Intake** — the authoritative chapter editor, including stable `H1`/`H2`/paragraph cards, local drafts, SDLXLIFF import, controlled inline markup/endnotes, full-file mode, QA findings/Resolve/Override, and GitHub commit/approval flow.
- **Glossary** — human governance for translation terminology.

## Translation companion

The workspace includes a companion that is fed when a chapter is successfully tagged for QA and committed. Each chapter feeds it once, including if the chapter is later returned for correction. The companion is available after a GitHub token and the selected project repository are verified. Its level is based on the selected repository’s approved/completed chapters: every five completed chapters increase its level. Mood and feeding history remain on this device and are kept separate for each project and branch. The companion uses the latest verified project count when offline; a new project needs a successful sync before its companion appears.

To add the companion to your Android home screen, touch and hold an empty area, choose **Widgets**, then find **Translation companion** under Intake Edit. It is designed for a 2×2 space and can be resized where supported. Tap it to open the earliest Pending Review chapter with missing English; if none are missing English, it opens the earliest Pending Review chapter. When the project is not connected, it opens the connection screen. The widget refreshes after successful feedings and around the next local day; it periodically syncs project progress in the background. Android may delay background work when the device is idle. The home screen companion blinks on supported launchers, with its mood conveyed visually on a transparent background. The in-app companion also floats and sways. Widget size and placement depend on the launcher.

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

In a chapter, open **Editor actions** and choose **Machine Translation · Translate with Google**. The app uses Google's on-device Japanese-to-English translation model, the same model family used by Google Translate's offline mode. The first use downloads language models and can use mobile data; subsequent translations run on the device. Each source entry is translated separately, with headings and paragraphs kept aligned by locator.

Machine Translation is a local reference. It does not change the English fields until you use a translation or choose **Fill blanks**. An imported XLIFF reference is stored independently; switch between references from **Editor actions**. Machine translation quality varies and should be reviewed against the source. Translations powered by Google Translate; see [Cloud Translation](https://cloud.google.com/translate).

## Chapter draft safety

Chapter edits retain the existing atomic local-draft system. Drafts are restored only when their base GitHub SHA still matches the remote chapter, preventing a stale local draft from silently overwriting a newer remote revision.

## CI artifact

Every push runs unit tests and builds a debug APK. The GitHub Actions artifact is named:

`intake-edit-debug-apk`

