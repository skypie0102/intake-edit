# Intake Edit

Android editor for the Pure Love x Violation translation workflow.

Application version: **0.15.36**.

All current development work is tracked in the [roadmap](ROADMAP.md). Earlier plans and trackers are preserved in the [archive](docs/archive/).

## Workspace sections

The launcher opens a small workspace home with two sections:

- **Chapter Intake** — the authoritative chapter editor, including stable `H1`/`H2`/paragraph cards, local drafts, SDLXLIFF/XHTML translation import, controlled inline markup/endnotes, full-file mode, QA findings/Resolve/Override, and GitHub commit/approval flow.
- **Glossary** — human governance for translation terminology.

## Translation companion

The workspace includes a companion that is fed when a chapter is successfully tagged for QA and committed. Each chapter feeds it once, including if the chapter is later returned for correction. The companion is available after a GitHub token and the selected project repository are verified. Its level is based on the selected repository’s approved/completed chapters: every five completed chapters increase its level. Mood and feeding history remain on this device and are kept separate for each project and branch. The companion uses the latest verified project count when offline; a new project needs a successful sync before its companion appears.

To add the companion to your Android home screen, touch and hold an empty area, choose **Widgets**, then find **Translation companion** under Intake Edit. It is designed for a 2×2 space and can be resized where supported. Tap it to open the earliest Pending Review chapter with missing English; if none are missing English, it opens the earliest Pending Review chapter. When the project is not connected, it opens the connection screen. The widget refreshes after successful feedings and around the next local day; it periodically syncs project progress in the background. Android may delay background work when the device is idle. The current companion uses mood-specific animation frames in the app and widget, with mood conveyed visually on a transparent widget. Widget size, placement, and animation behavior depend on the launcher. The app includes mood-specific reactions and shared reading, writing, and page-turning actions. The widget requests the full sequence from a launcher-hosted adapter; timing and behavior depend on the launcher. Android’s reduced-motion setting displays a still pose.

## Mascot v2 replacement (on hold)

The next companion is the user's blonde witch chibi character, based on the supplied character sheet. Work on the layered rig and five-state animation pack is paused while chapter editor usability changes take priority. The current Book Spirit remains in production until the replacement passes visual, Android and widget checks. The existing feed/level/project and tap-to-chapter behavior must stay unchanged. See the [active roadmap](ROADMAP.md), [character/rig design bible](docs/MASCOT_V2_DESIGN.md) and [art contract](artwork/mascot-v2/rig-contract.json). **No mascot artwork has been switched in the released app yet.**

## Mascot v2 raster prototype (art preproduction only)

The user-approved Slytherin chibi will use **high-quality painted raster layers** with a small lossless WebP texture atlas and compact animation keyframes, rather than the rejected hand-authored vector substitute. See [the raster pipeline specification](docs/MASCOT_V2_RASTER_PIPELINE.md) and [active roadmap](ROADMAP.md).

The source portrait already has transparency. A standalone preproduction script can remove its **separately connected speech bubble** without redrawing the character, producing full-body and face/torso still images at 160 and 320 pixels with original alpha preserved. It also exports a one-layer OpenRaster working master that can be opened in Krita to begin properly painting the moving parts. The atlas packer and dormant native Android renderer have been created with validation tests. **These are not animated frames or separately painted rig parts**: proper articulated layers, human visual approval, preview integration, widget integration and all five mood animations remain outstanding. The shipped Book Spirit and all translation functions remain unchanged.

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

## Smart chapter action

The primary bottom-bar button follows the editor's current workflow state, rather than requiring a choice between committing progress and tagging for QA:

- **Commit** while any English entries remain unsupplied. It saves changes as Pending Review and keeps the editor open; without changes it is disabled.
- **Tag for QA** when every English entry is supplied, even if the chapter was already committed as Pending Review. Tagging still requires explicit confirmation and the existing schema/endnote validation.
- **Tag for Fresh QA** if the previous QA pass is stale and the chapter is complete.
- **Review QA Findings** when the current QA pass is reusable but has active findings. The action opens the QA findings panel instead of bypassing resolution/override.
- **Commit for Approval** after a reusable QA pass has no active findings but the chapter still needs an approval-ready commit.
- **Approve Chapter** once the complete chapter has a reusable, closed QA pass and no uncommitted changes.
- **Awaiting QA** or **Approved** (disabled) when there is no new action to perform.

For complete chapters with uncommitted changes, the editor's three-dot overflow menu includes **Commit as Pending Review**. It is a separate, confirmed escape hatch for saving progress without tagging or advancing QA, and can reset the previous QA-ready status. Imported and machine-translated references do not count toward completion until their English is actually applied in the editor.

## Chapter translation reference import

From **Translation reference → Import Translation → Choose file**, select a translated `.xhtml` or an SDLXLIFF/XLIFF file. The importer saves the result locally for the current project, branch, chapter and Japanese source revision, without modifying the chapter's authoritative English fields. To switch imported files, choose **Replace file**. The imported reference can be used per entry or to fill blank English fields after review.

- **SDLXLIFF/XLIFF:** source/target segments are aligned by normalized Japanese source text as before. Whole-XHTML XLIFF units preserve translatable block order across `h1`, `h2`, and `p` elements; numbered chapter links are excluded from title text.
- **Translated XHTML:** reads nonempty `h1`, `h2`, and `p` blocks inside the XHTML `body`, including chapter titles, and maps them to the chapter's matching locator order. Empty layout paragraphs are skipped. There is no Japanese source column to verify, so the importer requires the **same block count and element order** as the chapter, checks recognizable chapter numbers in filenames, and refuses mismatches rather than shifting translations into the wrong entries. Exact unchanged Japanese blocks are not imported as English. XHTML files must be well-formed XML; inline formatting is flattened to reference text.

If an XHTML import reports a structural mismatch, keep the title and paragraph layout consistent with the original chapter and retry. SDLXLIFF remains the safer choice when block structure may have changed.

## Machine translation reference

In a chapter, tap the **Translation reference** button on the bottom bar, then choose **Import Translation** or **Machine Translation**. Machine Translation detects the source language automatically and translates it into English using the Google Translate web service. This is an unofficial integration and does not require an API key. It sends chapter text to Google and needs a network connection. The app packs multiple entries into each request, validates the returned line markers, honors retry guidance, and saves completed entries if a rate limit or connection error interrupts translation; retrying resumes the chapter. Chapters that fit within the request size limit use one request. Google may still rate-limit or change the service.

The first complete translation is saved locally for the project, chapter, and source revision. Open the translation reference dialog and choose Hide; choose Show saved translation to restore it without sending a new request. An imported XHTML or XLIFF reference remains separate and can be selected in the same dialog. Machine translations are references and do not change the English fields until explicitly applied. In the paragraph cards, **Paste** is pinned to the left of the editing toolbar. The bottom-bar **Copy ref** toggle controls every card's Copy action: OFF copies only RAW (the default), while ON copies RAW plus the currently visible imported or machine translation, if available. This setting is saved between chapters. Top-of-screen transient notices are dismissed after five seconds. Imported references are stored per project and branch; an older reference without project information can be explicitly copied to the current project after review.

## Chapter draft safety

In **Saved drafts**, each archived draft has an individual **Dismiss (×)** control. Confirming dismissal removes only that older local recovery copy; the current working draft, other saved drafts, and GitHub content are left untouched. Stale working drafts are archived safely and then cleared so a dismissed recovery copy does not reappear upon reopening. The chapter editor's context-aware primary action stays anchored to the right of the bottom bar, while the secondary tool icons can scroll independently on narrow screens.

Chapter edits use atomic local drafts. A draft is restored only when its base GitHub SHA still matches the remote chapter. Incomplete Whole File drafts reopen in raw mode for repair. If saving a draft fails, the editor reports the error and keeps the edits open for retry. Drafts based on older remote revisions are preserved: open **Saved drafts** in the chapter editor to inspect, copy, or export them. New drafts are isolated by repository, branch, and chapter. Older drafts did not record a project; they remain available under Saved drafts as “project unknown” for inspection and explicit recovery when the base revision matches.

## CI artifact

Android CI runs unit tests and builds debug and release APKs for application changes. The debug GitHub Actions artifact is named:

`intake-edit-debug-apk`

