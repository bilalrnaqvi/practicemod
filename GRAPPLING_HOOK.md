# Grappling hook

Minecraft 1.21.1 / NeoForge 21.1.235 / Java 21.

## Selected direction

The user selected the original [Grapple / Momentum Concept 01](docs/art-references/grapple-momentum.png).
Graphite launcher, steel folding claw, exposed side reel, brass accents and dark wooden grip.
The current model is a simple gameplay blockout, not the final approved geometry.

## Playable prototype

Find **Grappling Hook** in the Traversal Gadgets creative tab, or use
`/give @s practicemod:grappling_hook`. Put an umbrella in the offhand with
`/give @s practicemod:umbrella_glider`. Use survival mode or disable creative flight.

- Aim generally toward an exposed roof, tree-canopy or block top edge 3–48 blocks away.
  A pale circle marks the eligible edge nearest the crosshair within a 14-degree cone;
  it turns gold while latched. Hold use/right-click to pull to that edge.
- The owner preview and server firing path use the same collision-shape-aware search.
  Partial blocks use their real surface height. Hidden faces, internal roof seams and exits
  without player-sized headroom are excluded. Search covers the entire cone, including
  isolated distant blocks, rather than requiring a direct crosshair hit.
- The winch approaches outside the face, carries the player's feet at least 0.85 blocks above
  the selected edge, then automatically launches upward and across it. The final upward
  speed is 0.62–0.64 blocks/tick with at most 0.50 blocks/tick horizontally: a roughly
  51–53 degree launch that rises another 2.5–2.7 blocks after release. Completed grapples
  produce a useful airborne arc
  even when starting near the edge, level with it, falling, or above it.
- Releasing use early still cancels deliberately and preserves velocity; it does not force
  completion. Changed terrain, a broken anchor or newly obstructed headroom can cancel a
  pull safely. The automatic launch guarantee applies to completed, unobstructed grapples.
- Keep the umbrella in hand. Its existing automatic descent/fall-distance gate opens it;
  the grapple does not create hover, a cape or gliding lift.
- A recent grapple release permits excess horizontal glide speed, reduced gradually toward
  the normal umbrella cap. Landing, invalid movement modes and expiry clear the allowance.
- Obstruction, removed anchor, changing the item, incompatible movement modes or an eight-second
  pull timeout cancel the cable. Vanilla collision remains responsible for player movement.
- The hook protects against accumulated falling distance only while actively pulling.
  Released flight without a deployed umbrella retains normal fall risk.

Prototype tuning: steering acceleration 0.16 blocks/tick² and pull speed limit 1.65 blocks/tick.
The approach point is 1.5 blocks outside and 1.35 blocks above the lip. Automatic release
requires feet clearance and horizontal proximity; the launch supplies about 0.48–0.50 blocks/tick
across the edge with 0.62–0.64 blocks/tick upward speed. These revised values need in-game feel review. No crafting recipe yet; use the creative tab or give command.

## Ownership

- `GrapplingHookItem.java`: vanilla hold/release input, no custom firing packet.
- `GrappleHandler.java`: shared owner prediction/server simulation, world raycast validation,
  cancellation and finite umbrella handoff allowance.
- `GrappleState.java`: transient synchronized anchor for owner and remote cable rendering.
- `GrapplePhysics.java`: outside-edge approach, feet-clearance release and launch impulse.
- `GrappleTargeting.java`: shared cone search, exposed edges, visibility and body-clearance checks.
- `GrappleCableRenderer.java`: taut line and camera-facing target/latched circles. The preview refreshes every two client ticks.
- `GrappleRenderCheck.java`: opt-in Minecraft shader/depth rendering fixture.
- `src/main/resources/assets/practicemod/models/item/grappling_hook.json`: editable blockout.
  Direct runtime source; vanilla texture references, no derived asset exporter.

## Validation and next review

`./gradlew verifyProject --offline` checks the build, PAL playback validator and 61 GameTests.
The grapple tests include off-crosshair and distant single-block acquisition, slab heights,
blocked launch headroom, anchor removal/obstruction, early release and umbrella handoff.
Ten full handler trajectories use vanilla collision movement over stone roofs and leaf canopies
from low, near-level, elevated, close and oblique starts; they assert feet clearance, upward/across
launch velocity, bounded completion time, no terrain clipping and the glide speed allowance.
A separate test simulates the free launch arc under vanilla gravity/drag and switching the
main-hand item to the umbrella after 0.6 seconds. It verifies 2–3 blocks of additional rise,
a launch angle over 50 degrees, and automatic deployment before returning to release height.
The latest isolated run passed all 61 tests; the far-edge search averaged 1.45 ms in its test fixture
(this is not a general client performance benchmark).

The model has 12 non-degenerate cuboids with valid coordinates and verified texture paths.
The Minecraft renderer fixture was visually inspected: a pale target circle and gold latched
circle sit on the block edge with depth testing. Review images: `output/grapple-edge-review/target.png`
and `output/grapple-edge-review/locked.png`. This is a shader/depth fixture, not an in-world playtest.
Run `./gradlew runClient --offline --no-configuration-cache -I tools/grapple_review.init.gradle`
to reproduce it. The same init script with `verifyProject` isolates build/test outputs from
concurrent feature work. Normal development gameplay still uses plain `./gradlew runClient`.
Other existing vehicle inventory models reported missing-texture warnings; they are outside this change.
These checks do not establish movement feel, rooftop clearance for every geometry, first-person
model alignment, animation quality or two-client latency behavior.

Next review: check circle readability and target choice at roofs/trees, then test automatic
launch and umbrella deployment from different heights and repeated grapple/glide chains. Tune that performance before final
Blockbench art, projectile travel, claw/reel animation, hand animation and sound polish.
