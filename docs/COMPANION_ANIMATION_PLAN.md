# Companion animation

The accepted five-mood artwork and reaction sequences are bundled with the app. Reading, writing and page turning are shared across moods, with mood-specific expressions and pacing. Mood changes start the newly selected state rather than finishing the previous action. The production app does not download animation updates from the preview branch.

The home-screen widget disables the platform’s default fades between frames and uses an `AdapterViewFlipper` and uses URI-backed frame collections on Android 12 and newer, with a `RemoteViewsService` on older versions. It cycles through the complete reaction, reading, writing and page-turning sequences. The 48-pose shared actions hold each pose for two ticks to match the 96-tick in-app action duration. Widget scheduling is a continuous fixed sequence; the in-app player includes variable idle intervals. Reduced motion shows a still pose. The widget remains transparent, project-specific, and opens the next translation chapter.

## Outstanding validation

- Verify the full frame sequence, tempo, taps, and mood changes on the user's launcher, including multiple widgets and light and dark wallpapers.
- Check resizing, app update, reboot, sleep/resume, offline loading, memory and battery consumption. Launchers control playback and may pause or throttle animations.
- Smooth interpolation between different moods is not implemented; the new mood appears immediately.

The previous planning history is retained in [the archived plan](archive/COMPANION_ANIMATION_PLAN_PREVIEW.md) and [liveliness tracker](archive/COMPANION_LIVELINESS_TRACKER_PREVIEW.md). Their unchecked preview-stage entries describe historical review status and are not the current roadmap.
