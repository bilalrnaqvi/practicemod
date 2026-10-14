# White Horizon — editable model handoff

The static yacht is modeled and furnished. It is **not registered as a Minecraft
vehicle**. Moving-floor physics, the captain's map, water exclusion, multiplayer and
runtime rendering remain the engineering work described in the [technical brief](WHITE_HORIZON.md).

## Open and review

- **Canonical editable project:** [white_horizon.bbmodel](../../src/main/blockbench/white_horizon/white_horizon.bbmodel)
- [Original pixel textures](../../src/main/blockbench/white_horizon/textures/)
- [Local rendered review gallery](../../output/yacht/white-horizon/index.html)
- [Four-view overview](../../output/yacht/white-horizon/overview.png)
- [Portable OBJ](../../src/main/yacht/white_horizon/exports/white_horizon.obj), [MTL](../../src/main/yacht/white_horizon/exports/white_horizon.mtl), and [export textures](../../src/main/yacht/white_horizon/exports/textures/)
- [Layout / routes / interaction anchors](../../src/main/yacht/white_horizon/white_horizon.layout.json)
- [Local collision boxes](../../src/main/yacht/white_horizon/white_horizon.collision.json)
- [Render section bounds](../../src/main/yacht/white_horizon/white_horizon.sections.json)
- [Portable cuboid scene](../../src/main/yacht/white_horizon/exports/scene.json)
- [Recorded static validation](WHITE_HORIZON_VALIDATION.json)

Review images are local generated output, intentionally excluded from source checkpoints.
They can be regenerated from the tracked model and scripts. The source is Blockbench
**free format, version 5.0**, with embedded textures; it is not a Java entity model,
Bedrock export, or vanilla block-model JSON. The OBJ is a static interchange mesh,
not an importable Minecraft structure. Keep the BBModel for editing groups and pivots.

## Construction and layout

Overall envelope is **60 × 17 blocks**; origin is main-deck center. **16 model units
= 1 block**, X is starboard, Y up, Z toward bow, and station s = Z + 30. The 17-block
beam is centered at X=0, giving half-integer outside edges. Architecture uses block
courses and slab subdivisions; pane, fence, ladder and trapdoor details use Minecraft
pixel fractions. All geometry is axis-aligned cuboids with zero element rotation.
There are no mesh elements, diagonal faces, curved pieces or bevels. The final
project contains 12,515 editable cuboids, 144 groups and 17 material tiles.

The visual language is white blockwork, pale timber, blue glazing, dark salon frames,
warm lanterns and restrained furnishings. Every texture is an original 16 × 16 tile;
face UVs maintain **16 texels per block**, including partial-block shapes. Luxury
comes from space and materials. Small faces at joined block boundaries are trimmed
construction joins, not added surface decorations. Repeated blocks remain editable.

| Occupied level | Floor Y | Included spaces |
| --- | --- | --- |
| Machinery | -8 | Twin engine banks, center service aisle, core A access |
| Guest deck | -4 | Six two-person bedrooms, wardrobes, washstands, central corridor, linen/store rooms and pool pumps |
| Swim platform | -2 | Timber boarding platform, two stair runs and two ladders to water |
| Main | 0 | Recessed aft pool, beach bar, dining, party lane, furnished double-height salon, quiet foyer and foredeck seating |
| Upper | 4 | Dining terrace/bar, salon galleries, bridge console and captain anchors |
| Rooftop | 8 | Sunbeds, retreat seating, tables, canopy, mast and core B arrival |

The roof slab above the salon begins at Y=7.5. The central salon opening is six
blocks wide and sixteen long; it contains no upper floor or hanging fitting.
The main floor has a thin carpet, with a clear standing volume above it. Stairs A
and B retain their planned 5 × 8 footprints. Each flight rises in 0.5-block steps,
with a two-block-deep intermediate landing and full entry/exit landing. A connects
machinery/lower/main/upper; B connects lower/main/upper/roof.

Circulation corrections made while testing the full-size structural blockout:

- Notch the transom where the two swim stairs enter; the original full-width wall
  blocked both approaches.
- Widen the central lower hull/keel to 13 blocks so machinery-level stair A fits.
  The outside beam remains 17. The lower hull retains block steps.
- Treat intermediate stair landings as **half-height landings**, not cross-passages
  on the upper floor. Approach the port gallery around the forward end of the void;
  the aft gallery rail protects the stair opening.
- Move main side rails to X=±8.375 to retain over two blocks of passage beside the
  projecting stair cores. Merge the promenades into the bow lane before the taper.
- Use an underside Y=11 for the rooftop canopy, giving three clear blocks above
  its floor. Furniture does not occupy the central rooftop route.
- Use stair-shaped sofas with a half-block seat, and beds/sunbeds with a 9/16-block
  sleeping surface (plus a small pillow). Do not retain the earlier one-block-high
  seating from the first furnishing pass.
- Normalize overlapping solid block joins to eliminate coincident faces and fitting
  intersections. Water intentionally overlaps the submerged pool steps and is not
  exported as solid collision.

Cabins are bedrooms with a washstand rather than tiny inaccessible bathroom scenery.
Door openings are two wide and three high; paired leaves are shown **open** for
circulation review. There is no door-control implementation. The central lower
corridor is two clear blocks wide. Furnished access routes are measured with normal
0.6 × 1.8 player AABBs; these are static geometric tests, not game movement tests.

## Editing and regeneration

The **BBModel, including its embedded textures and `practicemod` metadata, is the
canonical source**. The external PNGs mirror the embedded pixels. After editing an
external PNG, reimport/update that texture in Blockbench before exporting. Keep
`practicemod` metadata when saving; adding/removing cubes also requires corresponding
material/collision metadata. Export fails on missing metadata rather than guessing.

Outliner hierarchy: `WHITE_HORIZON / deck / category / section / optional fixture`.
Deck groups are main, lower, machinery, swim, upper and roof. Categories separate
structure, hull, glazing, rails, furniture, doors, water, lighting and interaction.
Section groups follow 16-block longitudinal intervals; the export contains 19 occupied
deck/section bounds. Those envelopes are **render/broadphase bounds only**, never
solid enclosing collision. Rooms are hollow.

Door leaf groups have hinges at the local jamb and intended Y rotation metadata.
Keep their authored open pose for this static exporter. The captain is **not baked
into the model**: `captain_standing` is (-2.5, 4, 17) and `captain_interaction` is
(-0.5, 4, 17), in block coordinates. Additional anchors mark seats, beds, storage,
boarding and machinery access. Review mannequins and the ocean plane are excluded
from model, OBJ and collision output.

Run from repository root with Python 3; the authoring/review assembly scripts need
Pillow. The exporter and geometry checker use the Python standard library.

```sh
python3 tools/yacht/export_white_horizon.py
python3 tools/yacht/check_white_horizon.py
blender --background --python tools/yacht/render_white_horizon.py -- all
python3 tools/yacht/assemble_review.py
```

The renderer uses Blender 4.5, Cycles and nearest-neighbor pixel sampling. Individual
views can be regenerated by name, for example `-- deck_roof` or `-- salon_interior`.
Use `-- structural` only with a structural-stage source/export; the retained structural
review was produced before furnishing. The current final model should normally use
`-- all`.

`tools/yacht/build_white_horizon.py` is the **initial reconstruction builder**, not
the exporter. It refuses to overwrite the canonical project without `--replace`.
Do not run it to export manual edits. Its `--structural` mode is for deliberate
structural reconstruction and will remove furnishing if used with `--replace`.

## Validation and limits

The initial structural stage was checked and visually inspected before furnishing.
The corrected structural report recorded **32 routes / 3,038 samples**. The final
furnished pass recorded **54 routes / 3,707 samples**, including stair traversal,
bedroom access, seated-furniture approach, bar access, pool exit and the captain.
The checker excludes furniture as a walkable support surface, so climbing onto a
chair cannot conceal a blocked circulation route.

The final static checks establish:

- AABB body clearance along the sampled routes in the authored open-door pose;
  support can step at most 0.6 blocks relative to the route height.
- No positive-volume intersections between exported solid cuboids.
- An unobstructed central salon void above standing-player height.
- Complete, unique outliner coverage; six cabin definitions; finite coordinates;
  1/16 lattice coordinates; axis-aligned cubes only; 16-pixel/block UV density.

`./gradlew verifyProject --offline` passed on 2026-10-14: build, PAL playback
validation, and **all 53 existing GameTests**. These are the current workspace's
existing tests and do not contain a yacht gameplay implementation. Unrelated ongoing
umbrella changes were not included in the yacht source checkpoint.

Exterior, elevation, every deck, salon/cabin/bridge interiors and cutaway renders were
inspected. They use actual exported cubes and textures. Cutaways remove geometry for
visibility, and deck views omit levels above the one being examined. A Blender
render is not Minecraft first-person review or user visual approval. Native Blockbench
reopening was attempted but not confirmed: its Open dialog kept the model-selection
button disabled. The JSON/outliner/texture checks and Blender mesh renders passed;
no native Blockbench save/reopen round-trip is claimed.

The pool is a modeled contained water volume with basin and entry steps. Its fluid
behavior, infinity overflow, glass sorting and dry-room/ocean exclusion are not
implemented. The proposed dry volumes are metadata, not an air system. Rails use
visible fence/pane cuboids; a future Minecraft backend must provide continuous
vanilla-like fence collision rather than treating visible gaps as safe barriers.
Static geometry cannot establish moving contacts, jump momentum, reboarding,
multiplayer synchronization, server performance, or native block palette conversion.
The Sable moving-floor prototype remains a prerequisite for runtime yacht integration.
