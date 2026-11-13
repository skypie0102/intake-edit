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

## Sleeve corrective smoothing — 2026-11-13

The [sleeve correction study](https://chatgpt.com/library?file_id=libfile_be51803ed0788191a7529e22f5a7cfaa&account_id=personal) adds a mild corrective smoothing modifier to arm-weighted areas of the cloak and undersleeves, after the armature. It retains the preceding elbow/cuff weights, standing pose and wand attachment. Three smoothing strengths were compared in four sampled stress poses. For the non-wand arm outstretched test, the selected mild setting reduced the 95th-percentile area ratio of 715 sleeve-influenced polygons from 1.463 to 1.374 and the count with more than twice their rest area from seven to zero. The mild version also had zero above-2x polygons in the other three sampled poses; stronger settings caused some new high ratios. Polygon area is a narrow diagnostic, not proof of no clipping or natural appearance.

Four 24-frame elbow and shoulder loops were re-rendered from the front and angled cameras; the preview pack revision `2026-11-13-elbow-smoothing-test-1` replaces the earlier elbow tests. Visual differences are subtle, and the cloak can still deform during larger motion. Real gestures, other views and collisions need individual review. The old physics preview is still from an older rig, and facial, gaze and finger controls remain unfinished; there is no finished character animation or physical device test.

## Gaze and smile controls — 2026-11-13

The [gaze and smile study](https://chatgpt.com/library?file_id=libfile_f97c6230fb488191ab3079ae921c32f1&account_id=personal) builds on the sleeve-correction rig. Driven shape keys move both eyes' iris, pupil and highlight assemblies horizontally and vertically, raise the eyebrow mesh, and bend the mouth into a modest smile. Controls are experimental; front and angled poses, including a small head tilt, were rendered and control drivers were checked. Preview revision `2026-11-13-gaze-smile-test-1` adds two 32-frame tests at close-up and widget scale, preserving elbow tests. The prior physics clip was removed from the remote pack to fit the new previews and remains available in the earlier physics prototype archive.

The source eye whites are part of the head texture. Two attempted masking-based blinks produced visible mismatched edges and were discarded; the .blend contains no blink. Facial shapes remain limited; no eyelid, sad/angry expression, complete mouth, finger or independent hair controls have been finished. The older physics prototype has not been adapted, and cloak deformation requires animation-specific testing. Full character rigging and final performances remain unfinished; no device testing is claimed.

## Body audit and experimental cloak hem controls — 2026-11-13

The supplied gaze/smile study scene was inspected in Blender 4.3.2. Every mesh vertex has normalized deform-bone weights and an armature modifier; this is a binding check, not proof of good deformation. The skirt is rigidly bound to the hips, the hair to the head, and the hands have no finger controls. Front and angled renders were sampled for individual leg bends, a torso lean, arm movement and a combined pose. These few static views do not establish clearance or adequate deformation over a full range. Previously reported sleeve stretching remains unresolved.

Three experimental bones, `CloakHem.L`, `CloakHem.C` and `CloakHem.R`, provide manual lower-cloak motion. They are children of `Hips`; weights gradually transfer from the existing hip influence toward the hem, preserving the other bone influences. Rotate them in Pose Mode for modest secondary motion; they do not simulate cloth or automatically avoid the legs. The skirt remains separate and rigid. Static neutral, 15-degree hem, lateral hem and combined torso/leg/hem poses were rendered from front, three-quarter and rear views. These are exploratory samples, not a collision or animation approval; the cloth may intersect in other poses.

`tools/draconia_add_cloak_controls.py` reproduces the change from the supplied unmodified `Draconia_face_controls_test.blend` (SHA-256 `bfeffa616f946ad07aac46efa015037dcb10638eba15b54e9a3a57b37a548e34`) into a new file. `tools/draconia_validate_cloak_controls.py` compares evaluated geometry with the original in neutral and three body/arm poses, checks normalized weights, exercises the hem bones and checks a gaze driver. Blender 4.3.2 reported a maximum original-versus-new difference below `8e-8` scene units with the hem controls at rest. This preserves the prior deformation in those samples; it does not repair it. The editable model is not included in the repository; preserve the supplied scene and the resulting file separately, subject to the source model's distribution terms.

```sh
blender -b --python-exit-code 1 --python tools/draconia_add_cloak_controls.py -- INPUT.blend OUTPUT.blend
blender -b --python-exit-code 1 --python tools/draconia_validate_cloak_controls.py -- INPUT.blend OUTPUT.blend
```

## Skirt controls and preview loop — 2026-11-13

The experimental garment study adds `SkirtHem.L`, `SkirtHem.C` and `SkirtHem.R`, parented to `Hips` with gradually blended weights toward the skirt hem. They are manual controls and do not follow legs or resolve collisions automatically. Relative to the gaze/smile scene, four sampled poses with all six hem controls at rest differ by less than `8e-8` scene units. Weight normalization and independent movement of the cloak and skirt were checked. The skirt's low-poly shape and its clearance with the legs still need review in actual gestures; sleeve stretching, hair and finger controls remain unfinished.

Preview pack revision `2026-11-13-garment-controls-1` adds front and angled 24-frame looping tests of torso, leg, cloak and skirt motion. They are diagnostic controls, not a natural performance or proof of collision-free movement. The preview app fetches this pack from the `preview/draconia-companion` branch. `tools/draconia_add_skirt_controls.py` builds on the cloak-control scene, and `tools/draconia_render_garment_loop.py` and `tools/draconia_pack_garment_loop.py` produce the preview. The model remains outside this repository.

## Automatic leg follow — 2026-11-13

The next garment study adds hidden non-deforming follow bones between the hip and the existing side hem controls for both cloak and skirt. The side follow bones copy 60% of each thigh's local X/Z rotation for the cloak and 50% for the skirt; the cloak's centre follows both thighs at 50% each. A small eased outer-cloak correction activates for extreme outward thigh rotation. The existing hem controls remain available for manual adjustments. This is an experimental rig, not cloth simulation or collision avoidance.

In Blender 4.3.2, `tools/draconia_validate_garment_follow.py` compared the previous and revised rigs. Three poses without thigh motion, including manual hem posing, matched within `9e-8` scene units. In the rendered 24-frame step, measured cloak/leg overlapping face pairs fell from 94 to zero and skirt/leg pairs from 29 to zero. These totals count pairs per frame, not distinct visible defects. A separate audit of 37 rest, one-leg and combined-leg samples reduced cloak/leg pairs from 308 to 20 and skirt/leg pairs from 58 to zero; two poses still had cloak overlaps. A seeded set of 180 mixed poses reduced cloak/leg pairs from 2,875 to 218 and skirt/leg pairs from 320 to 28, with some individual poses getting worse. These surface-overlap counts exclude self-collisions and are not a visual approval or proof of a complete rig.

An additional 58-pose sleeve area study found that raising corrective smoothing from 0.35 to 0.5 reduced the count of jacket-under-sleeve faces above twice their rest area, but increased that count for the cloak from 63 to 94, so the existing settings were kept. Blender's volume-preserving skinning also worsened sampled sleeve area distortion. Sleeve stretching, extreme poses, mixed-motion intersections, and realistic secondary motion therefore remain unresolved. Continue with targeted weighting or topology corrections and continuous visual checks before declaring clothing and cloak rigging finished.

Preview revision `2026-11-13-garment-follow-2` replaces the two previous garment-control clips with front and angled 24-frame leg-follow tests. The build, validation, broad-pose audits and render scripts are in `tools/draconia_*`. The scene remains outside the repository; retain it alongside the original source, subject to the model's distribution terms. No physical-device validation is claimed.

## Sleeve-edge and cuff-weight refinement — 2026-11-13

The worst measured sleeve-area spikes were concentrated in thin cloak cuff edges. The refinement synchronizes deform weights across 26 short, arm-influenced edges and excludes their 52 vertices from corrective smoothing, which had been pulling the two edge surfaces apart. Two vertices at the other undersleeve's cuff had substantial hip weight despite being near the wrist; their hip weight now transfers to the forearm, and their smoothing influence is adjusted accordingly. `tools/draconia_refine_sleeve_edges.py` reproduces the change from the garment-follow scene; `tools/draconia_validate_sleeve_edges.py` checks normalized weights, neutral appearance, unaffected meshes and wand-to-hand attachment.

In the same 58 sampled sleeve poses used above, the number of cloak polygon-pose samples above twice their rest area fell from 63 to 9, and the largest ratio fell from 3.65 to 2.43. For the undersleeves the count fell from 6 to zero, with a largest ratio of 1.81. In the two new 24-frame preview loops, cloak samples above twice their rest area fell from 26 to zero, and the largest ratio fell from 3.60 to 1.75. Triangle-pair totals between cloak and arms fell from 506 to 502 across those loops; cloak/undersleeve pairs rose slightly from 10,752 to 10,760. The meshes have existing internal overlaps, and these totals are not a direct count of visible defects. Area ratios and limited poses do not establish collision-free or naturally deforming clothing.

Neutral geometry matched within `1.4e-7` scene units and wand position in hand coordinates stayed within `2e-7` units across sampled poses. The cuff intentionally moves differently during torso motion after removal of its hip influence; one sampled 20-degree lean changed vertices by up to 0.034 scene units. Front and both three-quarter close-ups were inspected. Larger sleeve gestures and mixed leg/cloak poses still need review and correction, and no physical-device validation is claimed.

Preview revision `2026-11-13-sleeve-cuff-refinement-1` replaces the wand-sleeve, other-sleeve and leg-follow studies with clips rendered from this scene. These are diagnostic loops, not final performances. The editable scene remains outside the repository, subject to the source model's distribution terms.
