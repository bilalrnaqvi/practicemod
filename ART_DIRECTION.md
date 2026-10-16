# Current art direction

## Shared visual language

Minecraft-native, Blockbench-editable forms: chunky cuboids, stepped silhouettes,
selective faceted/mesh slopes, broad readable panels and purposeful mechanical details.
Use crisp pixel textures, sparse seams, restrained wear and dark structural components.
Avoid photorealistic surfaces, fine noise, excessive bevels and decorative clutter.
Individual palettes are intentional; new vehicles need not be yellow or white.

Before new art, inspect the relevant images in `docs/art-references/` and the current
canonical model. Actual models outrank inconsistent generated concept projections.
Concept sheets should include a large three-quarter view and matching orthographic views.

## Current vehicle constraints

- **White Horizon:** strict Minecraft block construction: axis-aligned block courses, stepped bow, slabs/stairs, fence/pane/door proportions and 16-pixel/block material tiling. White shell, pale timber, dark blue glazing and warm lanterns. No wedges, smooth hull pieces, bevels or arbitrary decorative micro-geometry. Canonical static project: `src/main/blockbench/white_horizon/white_horizon.bbmodel`; see `docs/yacht/WHITE_HORIZON_MODEL.md`. Runtime integration and verification are documented in `docs/yacht/WHITE_HORIZON_RUNTIME.md`; visual approval remains separate.
- **Compact Racer:** stepped white shell, graphite lower hull, charcoal saddle and simple handlebars. Reference: `docs/art-references/compact-racer.png`.
- **CRAG:** compact two-seat buggy with roll cage, layered angular armor and chunky tires. Preserve side-by-side fitted seats and the selected 1.97-block exterior (10% narrower revision, 2026-10-15). Body #1F4700, highlights #336310, shadows #132E00, charcoal mechanics, steel and warm lamps. Reference: `docs/art-references/crag-two-seat.png`. Earlier single-seat and wide-body constraints are obsolete; latest proportions still need visual feedback.
- **Deepseeker:** ochre/charcoal hull, blue glass, warm forward lamps with uniform integrated bezels, low octagonal hatch. Current entry is aft starboard with a forward-opening hatch and continuous ladder; rungs are one model pixel deep. Preserve bunk, desk, stacked storage and connected aisle access. `docs/art-references/deepseeker-exterior-style.png` is an older exterior STYLE reference, not current hatch/layout evidence. Use `src/main/blockbench/deepseeker.bbmodel` for current geometry.
- **Mechanical Dolphin / Panorama:** blue armor, white belly, cyan lights, slim rectangular sensor eyes, prone cockpit, swept fins and jointed tail. No anthropomorphic pupils/brows. Canopy opening remains deferred. Reference: `docs/art-references/mechanical-dolphin.png` shows integrated geometry, not the latest fin motion.
- **Other equipment:** preserve each canonical model's palette and established silhouette; consult `docs/SOURCE_MAP.md`.

- **Grappling hook:** selected original Grapple / Momentum Concept 01: graphite rectangular launcher, steel folding three-prong claw, exposed side reel, restrained brass trim and dark wooden grip. Reference: `docs/art-references/grapple-momentum.png`. Current item is a gameplay blockout; final geometry and animation remain to be reviewed. See `GRAPPLING_HOOK.md`.

## Acceptance and source ownership

Check real player fit and usable access before detailing. Keep furniture fronts,
headroom and movement routes clear. Preserve approved shapes outside the requested change.
Geometry checks do not imply visual approval. Do not label a new revision approved unless
the user has actually approved it.

Use the canonical sources and exporters in `docs/SOURCE_MAP.md`. Do not rebuild current
models from old concept-authoring scripts. For animation, use `ANIMATION_DIRECTION.md`.
Historical decisions and rejected studies are retained in
`docs/history/ART_DIRECTION-through-2026-10-13.md`; their old status and stop points are inactive.
