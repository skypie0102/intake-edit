# Companion animation revamp plan

Status: **in progress**. The user approved the five-state appearance; the waiting thinking preview received a positive visual review; new-state greeting, happy-state writing, and hungry-state hopeful glance and sigh previews are available for review. Device and launcher validation remains open. Track the work and phase gates in the [repository roadmap](../ROADMAP.md).

## Purpose and current evidence

Make the monocled book spirit feel alive through character acting, not movement of the whole illustration. The user wants his body, head, arms, book, and symbols to move; he should sometimes think, turn a page, and write. The animation must work both inside Intake Edit and, within Android launcher limits, in the 2×2 home-screen widget. The widget should communicate mood visually, without a background, border, visible level, status, or counters.

The current production baseline is v0.15.27. [PR #47](https://github.com/skypie0102/intake-edit/pull/47) contains a separate vector preview and remains unaccepted. In that preview the user observed flickering and an extra arm during writing. The user later confirmed that its widget was present under the preview's different app name; the earlier missing-entry report was withdrawn.

The inspected prototype crossfades complete images in the app, and its widget fades between complete images. These fades and abrupt pose boundaries are plausible contributors to flicker; the exact device-specific cause is not yet established. The writing sequence explicitly adds a new arm-shaped path without replacing the existing limb. These facts must guide reproduction and correction, not be presented as a verified device fix.

## Constraints and behavior to preserve

- Use the approved expressive, gold-monocled book-spirit artwork. The new companion has an exclamation mark; waiting has a question mark. The book retains visible writing lines and a ribbon hanging below, and the companion's body continues behind the book without an abrupt exposed edge. Keep the SVG editable and use a vector-first approach; retain any generated assets and their generation process where needed for a supported runtime.
- No companion is shown until the user has connected a project repository and supplied a GitHub token, and a verified project snapshot is available.
- A successfully committed **Tag for QA** feeds the companion once per chapter; a chapter sent back for correction must not feed it again.
- Level and progress derive from completed chapters in the currently connected project repository, not from a global total or animation activity. Keep per-project/branch identity and existing offline cache behavior.
- The home-screen widget has no visible text, frame, colored background, level indicator, or count. Accessibility descriptions remain available to screen readers; launcher-owned picker labels are separate from widget content.
- A widget tap opens the next suitable translation chapter using the existing selection logic. Preserve behavior for no eligible chapter, missing configuration, network errors, and already-open editors.
- Animation must not change chapter content, perform new network requests, trigger feeding, alter progress, or leak repository data.
- Keep the app lightweight. Measure the APK and runtime costs of each option; scalable source artwork does not automatically guarantee smaller compiled resources or cheaper rendering.

## Character rig and drawing order

Build the rig as named, independently controllable vector components. Group elements in a stable local coordinate system and define anchors/pivots explicitly rather than generating unrelated full-body drawings.

1. **Book base:** back cover, left/right pages, spine, bookmark, page markings. It anchors the scene and stays stable during ordinary character motion.
2. **Spirit body and head:** body silhouette and head motion, with eyes, pupils, brows, mouth, blush/tears, and the monocle grouped under the head. The monocle and its attachment must move with the head and remain present through blinks.
3. **Left and right arms:** each arm/hand is one continuous part with a shoulder anchor and an intentional hand position. Define behind-book and in-front-of-book ordering; do not draw a second visible limb to fake a gesture.
4. **Page:** an attached movable page, with a pivot at the spine, the appropriate front/back appearance, and clipping/shading that keeps it inside the book.
5. **Pen:** attached to the writing hand while held; hidden or resting in a defined location otherwise. Its tip must meet the page before a writing stroke appears.
6. **Mood symbols:** question mark/thought dots, sparkles, hungry marks, and sad droplet as independent parts. They remain within the transparent widget bounds and are used only where appropriate.

The actual rendering implementation may use native vector drawables, Compose vector drawing, a tested animation engine, or generated vector poses as appropriate. Choose after visual, size, performance, and launcher tests. An SVG source is an authoring format; the Android runtime may require conversion. Avoid importing a large animation dependency without measuring the tradeoff.

## State and mood choreography

The following are intended visual behaviors, not new workflow semantics. Transitions use the existing local feeding history and connected-project status. Current `petStatus` logic is: NEW on the starting day with no feed; HAPPY if fed today; WAITING at age one day; HUNGRY at age two or three days; SAD at age four days or more. Do not change thresholds as a side effect of the revamp.

| State | Resting expression and idle | Occasional action |
| --- | --- | --- |
| NEW | Curious, alert eyes, a welcoming pose, and an exclamation mark. | Peek over the book, look toward the viewer, and settle back. |
| HAPPY | Warm smile, lively but restrained breathing, bright eyes, softly animated sparkles. | Turn a page with interest or write a short note, then react with a pleased expression. |
| WAITING | Attentive and expectant, glance between the page and viewer, question mark when curious. | Raise a hand to his chin, pause thoughtfully, then return to reading. |
| HUNGRY | Slower movement, slightly drooped posture and weary eyes; hungry marks remain subtle. | Pause over the page, imagine a bowl of food, then settle back with a gentle sigh; keep the mouth downturned. |
| SAD | Lower posture, subdued eyes and slow breathing, with an understated tear/droplet. | Peek up briefly, then curl back toward the book without exaggerated distress. |

Actions must communicate status without spoken text or widget labels. Mood-specific poses should be visually distinct at 2×2. Do not require an action to complete before a status update; finish or transition safely from the current pose.

## Action design

Each action has anticipation, movement, a readable hold, and recovery to a shared resting pose. Timings below are design targets to tune by observation, not promises about launcher frame rates.

- **Blink:** close and reopen over roughly 120–220 ms, with natural pauses between blinks and occasional double blinks where suitable. No full-body fade; eyes change while head and monocle stay attached.
- **Thinking:** bring the existing hand from its book-resting position to the chin; settle the head and eyebrows, then animate the question mark or thought dots independently. Pause, lower the same hand, and return to reading.
- **Turn page:** move the existing hand to a page corner, lift and bend the page around the spine, show its front/back and a modest shadow, settle it on the opposite side, then release and return the hand. Avoid a floating triangular flap or an abrupt reset.
- **Write:** reach with the existing hand, attach and angle the pen, touch its tip to the page, make a few short strokes that follow the tip, lift the pen, and return to rest. Never display an added third arm or leave the pen floating.
- **Mood change:** interpolate or use compatible transition poses for posture, expression, symbols, and book/hand positions. Preserve continuity if a state changes during another action.

Schedule actions with varied, reasonably spaced idle periods and mood-dependent choices. Avoid an obvious short cycle or constant motion. Respect Android's reduced-motion/animation settings when available, and always provide a stable resting pose.

## Rendering and flicker strategy

### In the app

- Draw one coherent character scene; animate transforms and compatible paths for its named parts. Avoid `Crossfade` of the entire character between every animation frame.
- Keep motion continuous across segment boundaries. Do not repeatedly recreate or fade the book, head, and other unchanged parts.
- Cache or otherwise manage vector parsing and drawing appropriately. Verify frame pacing on a real device and profile before adding complexity.

### In the widget

- Android widgets run in a launcher through `RemoteViews`, which limits supported views and lifecycle control. A Compose rig cannot simply be assumed to play in the launcher as it does in the app.
- First prototype a vector-compatible method using supported widget views. Eliminate whole-image in/out transparency fades and double exposure. Ensure each displayed pose has a consistent background, anchor, scale, and draw order.
- Assess the performance, memory, and smoothness of the chosen frame strategy at the actual 2×2 size, with multiple instances and resizing. Do not drive rapid background updates merely to simulate a frame clock.
- Launchers or system power management can pause or throttle updates; when continuous animation is unavailable, keep a correct, attractive static mood pose rather than flickering or showing stale/misaligned parts.
- If a raster fallback is necessary for a particular launcher or performance target, keep the SVG/rig as the source of truth and record the measured reason and size impact. Do not silently replace vector artwork based on an assumption.

## Preview widget investigation

The earlier preview APK uses a separate application ID and declares `TranslationPetWidget`. The user initially missed its widget because of the preview's different app name, then confirmed that it works. This is no longer an open missing-widget defect. Continue to verify registration and playback for new preview builds rather than assuming they behave identically.

Give the preview app and widget explicitly different, recognizable labels, e.g. **Intake Edit Vector Preview** and **Companion — Vector Preview**. Verify the compiled manifest, package identity, provider metadata, launcher picker grouping, fresh installation, first launch, and availability after a project is connected. Use Android device/widget-service inspection where available and confirm with the user's launcher. A blank companion when no verified project is connected is separate from a missing picker entry.

## Validation and acceptance

Automated checks should cover named rig parts and anchors, each mood mapping, valid vector resources, stable starting/ending poses, exactly one left and one right limb in each action, prop attachment, widget metadata and text-free layout, and preservation of existing project/feeding and chapter-selection behavior. Tests must check meaningful observable properties, not merely that an image resource exists.

Visual review must inspect intermediate poses and full-speed playback for every action and mood, including monochrome/colored backgrounds, dark/light wallpaper, the widget picker and 2×2 home-screen widget, resize, app reopening, device sleep/resume, and multiple instances where supported. Check for flicker, edge halos, ghosting, snapping, detached monocle/pen, extra limbs, page clipping, unreadable motion, and excessive repetition.

Measure release APK size, memory, redraw/frame pacing, and representative idle battery impact against the current release. Record device, Android version, launcher, test method, and observed limitations. Preview builds should be installable alongside production with a distinct widget entry and separate data. A build/test pass is not a substitute for verifying the animation on the user's device.

The revamp is complete when the user can see a smooth, appealing companion performing the agreed actions without extra limbs or flicker; the widget is discoverable and behaves as specified on the tested launcher; workflow/progress rules remain intact; and the final build has passing checks plus documented size and install/signing implications.
