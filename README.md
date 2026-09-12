# Intake Edit

Android editor for the Pure Love x Violation translation workflow.

Current application version: **0.6.0**.

## Workspace sections

The launcher opens a small workspace home with two sections:

- **Chapter Intake** — the existing authoritative chapter editor, including robust local drafts, restricted-English completion, full-file mode, QA findings/overrides, and GitHub commit flow.
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

## Chapter draft safety

Chapter edits retain the existing atomic local-draft system. Drafts are restored only when their base GitHub SHA still matches the remote chapter, preventing a stale local draft from silently overwriting a newer remote revision.

## CI artifact

Every push runs unit tests and builds a debug APK. The GitHub Actions artifact is named:

`intake-edit-debug-apk`
