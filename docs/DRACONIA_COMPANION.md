# Draconia companion — design and work record

Updated 2026-11-13. This is the active design reference; [ROADMAP.md](../ROADMAP.md) is the task tracker.

## Character and acting

Draconia is a fan-created female interpretation of Draco Malfoy; different creators tell different stories rather than sharing one canon. The [fan guide](https://malfoid.me/guide) describes this variety, while [Know Your Meme's overview](https://trending.knowyourmeme.com/editorials/guides/what-is-the-malfoid-meme-the-viral-female-draco-malfoy-fan-art-trend-explained) documents the fan-art trend. Draco's rivalry and pride inform the direction ([official character profile](https://www.harrypotter.com/fact-file/characters-and-pets/draco-malfoy)). This design interprets her as poised, proud, expressive and quick to hide affection or embarrassment; it does not claim a single canonical Draconia personality.

- **New:** absorbed in reading, notices the viewer, briefly surprised, collects herself, offers a restrained greeting and returns to reading.
- **Waiting:** composed, curious reading and occasional impatient glances or a small haughty look.
- **Happy:** visible delighted surprise and a fleeting warm smile; she recovers her proud composure. Energy can rise without frantic motion.
- **Hungry:** slower, tired and irritable; distracted by food, not inexplicably smiling.
- **Sad:** subdued and less guarded; a quiet glance away or comfort from her book, with tears used sparingly.

Mundane actions are shared across moods but timed and acted differently. Prefer specific poses, meaningful pauses, expressive face and controlled follow-through to constant body swaying. Symbols should appear only when relevant. Avoid depicting character interpretations as approved finished animation.

## Source and observed model

The user supplied the [Sketchfab FBX](https://sketchfab.com/3d-models/malfoid-chibi-draco-malfoy-25dce601e17945a89fac8381fab5f7cc), **Malfoid Chibi (Draco Malfoy)** by **0x00001337**, with six texture images. The user's download screen states Creative Commons Attribution and commercial use; confirm the exact license version and attribution obligations before redistribution. Rights in the underlying character/franchise are a separate consideration.

Blender inspection found separate mesh objects and UVs, but no armature, animation actions, or shape keys. Its original pose is asymmetrical; the coarse facial geometry and fixed arm position limit expression and book handling. Textures needed manual reconnection. [Textured static preview](https://chatgpt.com/library?file_id=libfile_a946878ce15481919152116b417d171f&account_id=personal) includes a packed Blender scene. This is not an approved rig.

The [exploratory motion study](https://chatgpt.com/library?file_id=libfile_11436cc12e0c819189cf6f2bdc666d7b&account_id=personal) contains 24-frame reading and happy-labelled head-movement tests and the script used to generate them. They are **unapproved diagnostics**, not completed acting: the book floats with its pages facing the viewer, the hands do not hold it, there is no independently animated face, gaze or blink, and the happy test does not actually express happiness. The script depends on temporary paths and is not a portable production pipeline. No companion replacement has been shipped.

## Preservation and visual gates

Keep the existing Book Spirit in production while preparing and reviewing the alternative. Preserve the 2×2 transparent, borderless, text-free widget, full sequences, tapping to a chapter, five moods, daily feeding after successful QA tag/commit, project/branch separation, token gate, level counts and offline behavior.

Before production: demonstrate a coherent book grip, convincing face and gaze, a reading and a happy reaction, clean motion transitions, and 160px readability. Seek visual feedback on the two pilot performances before creating the entire animation set. Then validate the approved frames in the app and actual Android launchers, including state changes, restart, performance and update compatibility. The previous illustration-based artwork and raster experiments are historical references, not the active 3D rig or proof of approval.

## Head-control and topology pilot — 2026-11-13

The [feasibility and head pilot](https://chatgpt.com/library?file_id=libfile_7b404028f5b881919bce733ca8db10d0&account_id=personal) records original source hashes, mesh connected-component diagnostics, repeatable camera/lighting renders at full and 160px sizes, and a Blender scene with a single head bone. Three static poses were rendered and visually inspected; face, eyes and hair follow the head, but the whole hair mesh moves rigidly. This does not establish acceptable deformation or hair clearance over a full range. Neither arm/hand posing, eye movement, blink, expression, reading nor happy action is implemented. Flat and smooth shading remain faceted; neither is an approved final style. The separate left/right arm and sleeve-hand islands give a starting point for a real arm rig; hair and cloak require further articulation/deformation evaluation. No app integration or device test was performed.

## Appearance restoration — 2026-11-13

The user supplied a Sketchfab viewer screenshot as the appearance reference. The prior lit Blender preview exaggerated polygon shading on the face and dulled the original colors. A revised experimental scene connects each original texture to an unlit emission shader, with Standard color management and transparent output; original model geometry and image textures are unchanged. The visual result is closer to the screenshot, but exact original Sketchfab viewer settings are unknown, and no user approval is implied. [Editable scene and comparison renders](https://chatgpt.com/library?file_id=libfile_a9ca408cffbc8191b0d201d1f40f5e67&account_id=personal).

The preview branch pack revision `2026-11-13-appearance-2` contains a static close-up and a full-body head-control test with the revised materials. These replace the old floating-book/head-motion studies in the downloaded pack; they are not reading or happy performances. Refresh in the existing Draconia preview app. The APK's initial bundled fallback still contains the earlier studies until a network update succeeds. Not tested on a physical device. Preview branch and assets remain separate from production.

## Experimental body and cloak rig — 2026-11-13

A first body armature and preliminary weights were added for the head, torso, arms, hands and legs. The original model's head tilt and bent, lifted leg are built into its asymmetric mesh. In the [cloak rig study](https://chatgpt.com/library?file_id=libfile_9c044aa9fffc8191b98f36bd2c64fd18&account_id=personal), the head and hair have been rotated toward level and the lifted leg partially reshaped toward standing; the arms, wand and foot remain asymmetric, so this is not a fully neutral pose. The corrected appearance materials remain in use.

The cloak is one connected mesh. Experimental weights blend sleeve areas with the arm bones and the rest of the robe with torso and hips. Modest movement is visible in preview, but wide poses show deformation, gaps and clipping. Further manual skin weighting and possibly topology changes are required; hair remains rigid, and facial and finger controls are still absent. Neither reading nor happy animations are complete. The preview branch revision `2026-11-13-cloak-straight-pose-test-1` supplies a still and two modest sleeve tests. This is separate from production and has not been tested on a physical device.

## Standing pose and cloak-weight study — 2026-11-13

The source leg was visibly positioned behind the other. In the [standing and cloak study](https://chatgpt.com/library?file_id=libfile_4d2115a0abe08191964dbbdfe525c911&account_id=personal), the bent leg mesh island was replaced by a mirrored copy of the straighter leg, aligning both feet in height and depth; the original model remains preserved separately. The head remains level. The wand-holding arm keeps its original raised pose, so this is not a symmetrical arms-down or T-pose. Front and three-quarter renders were inspected.

Cloak skin weights were smoothed locally across the upper mesh; 24-frame tests show small arm motions, but wide and forward motions still cause visible gaps, stretching and clipping. Manual weight painting and possibly mesh corrections remain needed. Preview branch revision `2026-11-13-standing-cloak-study-2` contains front and angled stills and separate sleeve motion tests. No final performance or physical-device validation is claimed.

## Wand attachment correction — 2026-11-13

The wand had been inadvertently weighted to the torso and stayed behind when the hand moved. In the [wand attachment study](https://chatgpt.com/library?file_id=libfile_2d90539257f08191aba049a17b577c42&account_id=personal), the wand and the existing rigid hand island are bound to the same hand bone; the original grip position is preserved. Relative position was checked across shoulder, elbow, wrist and combined poses, and a 24-frame test was rendered. Preview pack revision `2026-11-13-wand-attached-test-1` includes the new test. This does not add finger controls or fix the previously noted cloak deformations; the rig remains experimental and no physical device test is claimed.

## Secondary-motion physics prototype — 2026-11-13

The [physics prototype](https://chatgpt.com/library?file_id=libfile_7093aa743b8081918f6e71a6bbacc00a&account_id=personal) adds experimental pinned cloth modifiers to the cloak, hair and skirt after their existing armature modifiers, with trial collision modifiers on body objects. A gentle keyed head and torso motion drives the test. A 19-frame segment after warm-up is sampled and reversed into a 36-frame diagnostic loop. The reversal makes a loop, but is not a physically continuous production simulation; a future animation needs a settled or cyclic bake. Preview pack revision `2026-11-13-secondary-physics-prototype-1` adds the diagnostic while retaining prior previews.

The hair remains a single stiff mesh and motion is barely visible at widget size. No cloth self-collision or independent hair strand controls are configured. The existing wide/forward-arm cloak gaps and stretching were not fixed by adding physics. Collision is experimental and needs careful testing across poses and viewing angles; small frame samples are not proof that intersections are absent. The .blend needs sequential playback or baking to rebuild its simulation cache. No finished performance, production integration or physical-device test is claimed.

## Refined cloak and sleeve weights — 2026-11-13

The [refined cloak rig](https://chatgpt.com/library?file_id=libfile_815ff4d63fc08191be7457bafdee454c&account_id=personal) rebuilds the cloak's arm influences using interpolated under-sleeve weights and smooths transitions into torso and hips. The undersleeves were then matched to the cloak surface to reduce previously visible poke-through. Seven rest, shoulder, and elbow poses were rendered from front, three-quarter and rear; three additional 24-frame diagnostic loops exercise the wand arm, other arm side lift, and forward movement. Skin weights are normalized and the existing wand-to-hand relationship was checked numerically across shoulder, elbow and wrist poses. The earlier large visible gaps are reduced in these tested poses, but minor poke-through and deformation can remain, and this does not establish correctness for all possible poses or production animations.

Preview revision `2026-11-13-cloak-rig-refined-1` adds the updated sleeve tests. Its physics clip is explicitly labeled as based on the previous rig: the physics setup has not been ported or checked with the new weights. Hair, facial, gaze, blink and finger controls remain incomplete, and neither final performances nor physical-device tests are complete.

## Continuous elbow and cuff tests — 2026-11-13

The [elbow and cuff study](https://chatgpt.com/library?file_id=libfile_73cae2bbcb1c8191845a761fc873ad91&account_id=personal) adjusts the cloak weights around both cuffs toward their forearm bones, smooths the transitions, and remaps the under-sleeve weights. Static stress tests on the preceding rig used front, both three-quarter views and rear. The revised rig was rendered in four continuous 24-frame loops, front and angled views for each arm, with +/-45 degrees on the wand-side forearm and +/-50 on the other plus a shoulder lift during one half of the cycle, relative to the original asymmetric pose. These are local-axis stress tests, not an anatomical full-range or production animation validation.

Simple nearest-hand proximity comparisons improved in the sampled poses, but are not collision tests. Visual review still shows the non-wand sleeve/cuff stretching at stronger bends and shoulder lifts; the posed source mesh and cuff shape need more work. Do not claim clipping is fully resolved. The revised armature and textures are packed in the study file; cloth physics remains based on a previous rig. Preview revision `2026-11-13-elbow-cuff-test-1` supplies the four elbow tests. No device testing is claimed.
