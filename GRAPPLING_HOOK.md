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

- Aim at a solid block between 3 and 48 blocks away. Hold use/right-click to latch and pull.
- Release use to detach without zeroing velocity. Pulling close to an elevated anchor also
  releases automatically once the eye has cleared the contact height.
- Keep the umbrella in hand. Its existing automatic descent/fall-distance gate opens it;
  the grapple does not create hover, a cape or gliding lift.
- A recent grapple release permits excess horizontal glide speed, reduced gradually toward
  the normal umbrella cap. Landing, invalid movement modes and expiry clear the allowance.
- Obstruction, removed anchor, changing the item, incompatible movement modes or a five-second
  pull timeout cancel the cable. Vanilla collision remains responsible for player movement.
- The hook protects against accumulated falling distance only while actively pulling.
  Released flight without a deployed umbrella retains normal fall risk.

Prototype tuning: acceleration 0.16 blocks/tick², speed limit 1.65 blocks/tick, pull target
1.8 blocks above the anchor, small gravity compensation while attached. These values need
in-game feel review. No crafting recipe yet; use the creative tab or give command.

## Ownership

- `GrapplingHookItem.java`: vanilla hold/release input, no custom firing packet.
- `GrappleHandler.java`: shared owner prediction/server simulation, world raycast validation,
  cancellation and finite umbrella handoff allowance.
- `GrappleState.java`: transient synchronized anchor for owner and remote cable rendering.
- `GrapplePhysics.java`: velocity steering and release rules.
- `GrappleCableRenderer.java`: prototype taut line and anchor marker.
- `src/main/resources/assets/practicemod/models/item/grappling_hook.json`: editable blockout.
  Direct runtime source; vanilla texture references, no derived asset exporter.

## Validation and next review

`./gradlew verifyProject --offline` passes the build, PAL playback validator and all 58 GameTests,
including five new grapple tests for bounded acceleration, release velocity, umbrella handoff,
anchor removal/obstruction, item cancellation and automatic release criteria.
The first fixture intersected neighboring GameTest structures; moving it above those structures
made the unobstructed and deliberately obstructed cases independent.

The model has 12 non-degenerate cuboids with valid coordinates and verified texture paths.
The development client completed resource/model loading without grapple-related errors.
Other existing vehicle inventory models reported missing-texture warnings; they are outside this change.
These checks do not establish movement feel, rooftop clearance for every geometry, first-person
model alignment, animation quality or two-client latency behavior.

Next review: test manual early release, late automatic release at roofs of different heights,
umbrella deployment and repeated grapple/glide chains. Tune that performance before final
Blockbench art, projectile travel, claw/reel animation, hand animation and sound polish.
