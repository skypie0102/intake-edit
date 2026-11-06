# Android performance review

These are synthetic observations from an API 35 x86_64 GitHub Actions emulator on November 6, 2026, not measurements of a physical phone. They are a single run, not a statistical benchmark. The performance tests can be rerun with the Android emulator workflow.

- On a synthetic 1,000-entry chapter, 200 repeated hashes of one QA paragraph took 84 ms without the per-snapshot cache and less than 1 ms with it at the timer's displayed resolution. The cache is rebuilt when the document changes and does not alter QA decisions.
- Importing a synthetic 1,000-entry XLIFF reference took 163 ms. Input reads are capped while streaming; alignment still uses exact source matching and reports partial results.
- Serializing and reloading a synthetic 1,400-chapter status cache took 116 ms; the JSON occupied 364,922 UTF-8 bytes. Cache work runs off the UI thread. Real repositories and storage may differ.
- Loading the complete Happy companion pack took 1,516 ms in the test process. Process PSS was 209,241 KB before and 226,685 KB after; this is not an isolated memory measurement, since unrelated allocations and garbage collection affect it. The bundled core animation JSON is 5,930,263 bytes, with separate idle-action JSON files. The app loads the pack off the UI thread, caches the selected mood, and keeps the full sequences. Further launcher and physical-device memory and battery work remains deferred.
- GitHub JSON decoding, workflow-index parsing, XLIFF processing, and draft persistence run off the UI thread. Chapter fallback uses bounded workers and rejects truncated trees.

The 34 companion vector checks, unit tests, and emulator interactions remain part of CI. Results do not establish typing latency, battery use, or launcher behavior on physical devices.
