# Intake Edit roadmap

There are no active roadmap items. The completed app-review work is recorded in the [app reliability roadmap archive](docs/archive/APP_RELIABILITY_ROADMAP_2026-11.md), and earlier plans are in [the archive](docs/archive/). Synthetic emulator results are in the [performance review](docs/PERFORMANCE_REVIEW.md).

## Deferred: physical-device performance and interaction checks

Deferred at the user's request. Revisit if problems are reported; emulator checks do not establish phone-specific typing latency, TalkBack behavior, or battery use.

## Deferred: release signing and data safety

Not planned for now; not complete. Establish a persistent release key, verify update compatibility, document one-off-key migration, and determine a reliable way to preserve local data before any required reinstall.

## Deferred: companion widget validation

Not planned for now; not complete. Validate complete sequences, tempo, interactions, mood changes, accessibility, resizing, launchers, memory, battery, reboot, and offline behavior on devices. The user confirmed the earlier frame-fade flicker stopped.

Completed companion transitions and idle animations are documented in the [animation milestone archive](docs/archive/COMPANION_ANIMATION_MILESTONE_2026-11.md). Additional idle designs remain a [reference](docs/COMPANION_IDLE_ANIMATIONS.md).
