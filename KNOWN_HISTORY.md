# Intake Edit recovery history

## Known milestones

- `997b921ad63a4a0bd03bbcdb5a421f4468118f4c` — Android v0.5.0 release target.
- `725d11a7dec946dcb5689301c4c972b1d478a14a` — Android v0.5.1 feature merge baseline.
- `a46aa8caaa43a81721961ff56102815fc91b9a32` — Windows functional-editor baseline.
- `9e88bb41184f3400b649d246ed1c7ab3a79f223e` — Windows search/filter/QA-jump merge.
- Windows credential branch reached `2a7000fc8891ef724b59f46098a8a426d0bbd8a0` and passed Rust tests + Windows release build.
- Android smart rich-inline branch existed before suspension; final merge/release was not verified.

## Android v0.5.1 confirmed behavior

- Active / Completed / All chapter queues.
- QA findings and explicit overrides with audit reason.
- Stale overrides reactivate after authoritative content changes.
- `[b]...[/b]` and `[i]...[/i]` controlled inline markup.
- Copy Both and Paste return focus to Authoritative English.
- Structured English edits reset editor review.

## Smart rich-inline work in progress

Requested/implemented before suspension:
- visual rich formatting in the English editor rather than visible markup tags;
- B/I toggle semantics on selected text;
- removal of the helper text below the editor;
- persistence remains controlled `[b]/[i]` markup.
