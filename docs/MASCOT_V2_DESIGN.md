# Mascot v2 — character bible, rig and motion contract

**Status:** specification / art preproduction (2026-11-10). This document does not claim the new character is already drawn or live in the app.

## Source of truth

- **Primary design reference:** the user's 2026-11-10 character sheet (chibi blonde witch in green/black school uniform, turnaround, 12 expressions, detail studies, color palette, and actions).
- **Secondary, higher-resolution pose reference:** the user's original single-character illustration from the same conversation, with arms crossed and a "Hmph!" bubble.
- These images were provided in the conversation; **the actual reference image files have not yet been imported into this repository**. The corresponding original filenames and SHA-256 digests are tracked in [rig-contract.json](../artwork/mascot-v2/rig-contract.json). Copy the originals without resizing or lossy conversion before attempting a production rig. The speech bubble and illustration page decorations are **not** part of the mascot.
- Maintain one canonical design; do not stitch unrelated drawings into the rig. The sheet's turnaround is for silhouette guidance; the larger image supplies the authoritative likeness.

## Character identity and design locks

There is no user-confirmed character name; internal ID is **mascot-v2**, not a public-facing name.

- Chibi / super-deformed proportions: large head, relatively petite body and shorter limbs. Match the user's drawings, not an arbitrary anime template.
- Very long, lightly layered, nearly platinum blonde hair with a center-to-slightly-offset part; front strands frame the face; the back reaches to the mid/lower back. Preserve the silky strand flow with small highlights.
- Gray, half-lidded eyes; slanted expressive brows; a tiny pursed mouth; pink cheeks. Her usual attitude is haughty, suspicious, pouty, and endearing, not perpetually happy.
- Black outer robe, dark green lining/hood, green V-neck knit, pale gray collared shirt, green striped tie, green plaid skirt, opaque black tights, black loafers, small serpent house crest. Avoid flattening her outfit into a generic robe. For tiny render sizes, simplify the crest while keeping its silhouette.
- Dark, consistent outlines, smooth anime cel shading and restrained highlights. Transparent background, no text, no border, no halo, no whole-character fades.

Suggested palette (visual starting point from the reference, **not** pixel-calibrated): hair #FBF1D9; eyes #929190; skin #F8E1CF; cheeks #E9A992; outline #1C1B1D; robe #171819; lining #174B32; knit #2B5539; shirt #DADCDD; skirt shadow #24342C; gold crest #C8A465. Check colors against the original image before locking the master.

## Artwork pipeline: independent layers, not chopped screenshots

Author a clean editable **master vector rig** at a normalized 320×320 viewport. Keep a higher-resolution working master where helpful. Every moving component needs a visible part and painted-overlap beneath any hinge to prevent holes during movement. No automatic cutout alone can meet this quality bar.

The versioned [rig contract](../artwork/mascot-v2/rig-contract.json) names the mandatory draw groups, parent attachments and normalized pivots. Actual paths and masks still need to be authored. Important depth rules:

1. Hair behind face and body, with separately animated side locks/bangs placed in front.
2. Feet/legs behind skirt and robe, robe back behind torso, torso behind face/head.
3. Both arms and wrists attach to the same shoulder chain; sleeves conceal wrist joins, hands connect naturally.
4. Face features move with the head; eye highlights track the pupils; hair follows turns with small delay.
5. Layers for reading, writing, page turning and wand poses may include optional book, pen and wand props, but these props are not required in the neutral crossed-arms pose.

**Master rig acceptance:**
- Clean neutral/rest pose and accurate front and three-quarter silhouettes compared against the reference; back/side views preserved for design QA.
- No duplicated/floating hands, missing arm segments, distorted fingers, gapping neck, detached hair, or robe intersections.
- All 12 expressions identifiable without symbols, and eyelids, eyes, brows, and mouths are true independent assets.
- Hair and robe parts behave as connected physical forms with soft secondary motion. The character stays centered and legible within a transparent 2×2 widget.
- Prefer authoring vector paths over shipping hundreds of raster cutouts; verify AndroidSVG support for chosen features. If gradients or masks fail on device, preprocess them for the pack rather than silently dropping details.
- Produce a full-size turntable + expression contact sheet and a 160×160 check sheet before animating.

## Stable compatibility with production

Keep the existing logic and **five enum moods exactly**: NEW, WAITING, HAPPY, HUNGRY, SAD. Do not touch GitHub authentication, pet level, project/branch scoping, feed/Tag-for-QA, stored mood, offline behavior, or chapter deep-linking to perform this visual replacement.

The production renderer already consumes **SVG frames inside a JSON pack**, with 96 reaction frames per mood, 48 poses per shared action (reading, writing and page_turn), two extended idle scenes per mood, a blink pose, and frame duration metadata. Keep this contract as the initial integration target. Avoid replacing it with an unsupported Android live-SVG-layer widget scheme. We may add internal authoring metadata, but export must remain compatible with the rendering pipeline.

Build new source/animation outputs under a separate mascot-v2 directory. Existing Book Spirit files remain the production default until release cutover. Never overwrite \`companion/animation-pack.json\` during authoring or initial preview work.

## Acting bible

| Mood | Default expression | Acting rhythm / key beats |
|---|---|---|
| NEW | curious / suspicious → surprised → shy smile | eyes track reading → lifts head → surprised **with** ! → ! disappears as a slight smile starts → brief wave → hands down → return to book |
| WAITING | hmph / suspicious | arms folded, measured side eye, foot tap or searching for her line; not a continuous scowl animation |
| HAPPY | satisfied / smug | more energetic eye tracking, restrained smile, humming and delighted-by-passage sparkles that actually twinkle |
| HUNGRY | annoyed / tired | sluggish reading, stomach rumble, distracted food thought; never grin while starving |
| SAD | subdued / tired | slowly loses focus, rests with book, rare tear; avoid repeatedly exaggerated sobbing |

**Shared across all five moods:** reading (noticeable left→right pupil movement and line reset), writing (eyes on the page, one continuous writing arm/hand, real pen contact and enough writing duration), page turning (fingers touch paper, page curls from spine then settles, no spikes), short thoughtful reaction. Shared actions vary in pacing and expression with mood, not in fundamental hand anatomy.

**Additional existing two idles per mood** are retained conceptually:
- NEW: curious_peek, absorbed
- WAITING: find_place, thinking
- HAPPY: humming, delighted
- HUNGRY: food, rumble
- SAD: losing_focus, book_comfort

For authored actions use **anticipation → motion → readable hold → recovery → follow-through**. No sudden jumps between motions, mismatched moods, random fading faces, unexplained speeding, generic bouncing, or entire-body sways unrelated to the gesture. Blink transitions may conceal only an expression change, not a body teleport. Follow-through should be visible at 160 px without becoming theatrical.

## Preview and widget workflow

- Author assets and animations against the existing preview application first; retain its Refresh/remote-pack workflow for the **in-app** preview.
- A remotely refreshed preview pack does **not** update the Android home-screen widget: the widget uses the APK-bundled pack. Rebuild the preview APK before claiming widget visual QA.
- Validate at 320×320 and the actual production 160×160 widget bitmap. Check both dark/light launcher wallpapers, 2×2 size, larger resizing, clipping, battery/CPU, frame flicker, update/reboot and reduced motion.
- The widget must remain transparent, borderless, text-free, animated where supported, and tappable to open the next chapter. Preserve the current graceful static fallback.
- Switch both in-app and widget assets together only after the production pack passes tests and visual review; remove the previous mascot from the shipped app **after** verifying the new fallback and icon.

## Quality and release gates

1. **Character match:** independent full-size and 160 px visual comparisons; every face expression and outfit detail recognizable. No screenshots used as rigid puppets.
2. **Rig:** pivot/mask/part audit; long hair, robe, arms, hands, and props remain attached through full test range.
3. **Animation:** complete five-state reactions, 3 shared actions × 5 moods, 2 idles × 5 moods; no wrong-state flash on switching.
4. **Preview:** inspect looping and interrupt transitions, rest-pose continuity, gesture readability and timing. Regenerate a distinct v2 pack and verify the bundled output byte-for-byte.
5. **Production cutover:** app, home-screen widget, widget picker preview, and app icon updated together; chapter and feeding/level behaviors unchanged.
6. **Validation:** automated contract tests, generator tests, Android build, preview walk-through, launcher checks; update docs and release only after all applicable gates pass.

**Rule:** no early “looks done” sign-off based only on a static render. Each phase remains pending until its tests/artifacts exist. Current production Book Spirit is intentionally untouched.
