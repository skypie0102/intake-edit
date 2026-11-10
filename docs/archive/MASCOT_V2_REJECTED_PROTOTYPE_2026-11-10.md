# Rejected first-pass mascot art — 2026-11-10

The user rejected the Phase 1 artwork generated in [PR #62](https://github.com/skypie0102/intake-edit/pull/62), reporting that it looked ugly and nothing like the approved character. The critique is valid. The produced rig reduced the detailed anime illustration to a crude, boxy, generic cartoon.

**Why it failed:** It was drawn by approximating the desired artwork with hand-authored SVG shapes and invented anatomy. Consequently the hair silhouette, face/eye styling, robe, legs, colors, shading and overall character proportions diverged from the reference. Technical checks verified IDs, pivots and frame formats but did not compare artwork against the reference image. Passing CI was incorrectly presented as progress toward an acceptable art rig.

**Disposition:** Reject and remove the SVG master, side/back silhouette studies, expression presets, authoring generator and structural tests introduced in PR #62 from active development. Retain the original approved illustration, revised Slytherin character reference sheet, design specification, stage manifest and existing Book Spirit production resources. The rejected prototype remains visible through Git history for audit only, not as an asset to build on.

**Mandatory next approach:** Establish a reference-faithful character master first, then isolate layers. Maintain fine anime linework, smooth shading, distinctive half-lidded gray eyes, thick flowing blonde hair, consistent chibi proportions, green plaid uniform and a proportionate Slytherin crest. Treat user inspection of the neutral master and 160px rendition as a gate *before* allocating time to detailed animations. Hand drawing the SVG data from invented coordinates is **not** an acceptable replacement for faithful character art. Do not call Phase 1 complete because the manifest has 31 elements or because Python unit tests pass.

**Production:** The existing Book Spirit app and widget were never switched. Do not change them during artwork correction.
