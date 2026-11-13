> **Historical raster study (2026-11-13):** This document describes the previous illustrated-raster approach. It is retained for reference and does not describe the active 3D companion plan. See [the current roadmap](../ROADMAP.md).

# Mascot v2 Phase 1B — Iris-only gaze pilot (2026-11-10)

**Status: technical / visual experiment, NOT art-approved; no finished rig.**

## Reason for this revision

The initial [whole-eye split](MASCOT_V2_PHASE1B_EYE_PILOT.md) attempted to move the original lashes and eyelids along with the eyeball. That revealed torn or floating linework around the face.

This follow-up isolates **only the iris and pupil interiors** from the SHA-256-pinned original illustration. Eyelash/eyelid outlines and the character's face remain fixed. The aim is to create a subtler way of tracking a book or the viewer while preserving her recognizable suspicious expression, not to fake a blink.

## Implemented proof

- New [reproducible Python source](../tools/prototype_mascot_v2_iris_motion.py) uses Pillow and validates the exact untouched 667×1000 original portrait.
- Source is isolated from the detached “Hmph!” speech bubble while preserving character pixels.
- Two tight iris-only masks produce independent, original-pixel RGBA illustrations.
- Exports an experimental four-layer 640×640 OpenRaster file: two irises, temporary sclera underpaint and hidden pristine reference. **The rest of the character remains flat.**
- Builds five *small* horizontal gaze samples at -2, -1, 0, +1 and +2 pixels; also exports 160×160 widget readability comparison.
- Recombining the neutral layers is checked to produce **zero RGBA pixel difference** from the approved original at 640×640.
- The experimental iris WebP atlas produced locally with the [raster atlas packer](../tools/pack_mascot_raster_layers.py) is approximately **132 KB**, including the temporary body/face background. It is not a production pack or forecast for final rig size.
- Adds [isolated unit tests](../tools/test_mascot_v2_iris_motion.py) for source integrity, transparent reference isolation, pixel-exact neutral reconstruction, four-layer OpenRaster export, and localized moving regions.

## Findings and hard limits

1. Fixing eyelashes while moving only the irises avoids the original whole-eye dragging, but it does **not** provide deforming eyelids, genuine blinks, or all twelve facial expressions.
2. Underlying sclera is temporarily painted with sampled pixels, **not** finished hidden-eye art. It still needs manual illustration and larger-motion stress tests. Do not describe this as approved facial rigging.
3. The left/right mask polygons are provisional and hand-located for this *one* portrait. Different angle/expression sheets will need separate paintings and calibration.
4. There are no separately painted body, hair, robe or Slytherin crest layers yet. This test must not be deployed to the app or home-screen widget.

## Repeat the technical preview

```sh
python -m pip install Pillow
python tools/prototype_mascot_v2_iris_motion.py \
  --portrait /path/to/Chibi_Malfoid_1000.png \
  --output /tmp/mascot-iris-v2
```

Run tests with `python -m unittest discover -s tools -p 'test_mascot_v2_iris_motion.py'`. The original reference is provided separately; do not substitute another portrait.

## Next acceptance work

Artist-repaint the sclera behind both pupils and the original eyelid edges; draw independent brow, eyelid, eye-white and blink variants; test small pupil motion with those overlays at 160px and full art resolution. Move on to separate painted hair/head layers only after those pass human visual review. Keep Book Spirit in production throughout.
