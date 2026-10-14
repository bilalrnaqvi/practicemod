# Grappling hook concept 01

Status: user selected the original concept on 2026-10-14. The first playable movement prototype is documented in `../../../GRAPPLING_HOOK.md`; final art and gameplay feel remain subject to review.

Review image: [concept-01.png](concept-01.png). Generated with the built-in imagegen tool, then visually inspected. Illustrative projections and the umbrella vignette are concept art, not authoritative geometry or a runtime preview.

Graphite and steel handheld launcher, brass accents, dark wooden grip, visible cable reel and folding three-prong grapnel. Follow the existing chunky Minecraft / Blockbench visual language.

Movement intent: latch to a high ledge, accelerate along the cable, release into upward/forward momentum, then deploy the existing umbrella for the glide. The hook provides no hover or glide lift. Preserve useful incoming momentum rather than stopping the player at the anchor. Collision clearance and release timing need a playable prototype.

Suggested approval stages:
1. Original visual and movement concept selected.
2. Prototype pull acceleration, release timing, momentum retention and ledge clearance with simple art; review the feel in game.
3. Tune the umbrella handoff and repeated grapple/glide traversal. The existing UmbrellaGliderPhysics.HORIZONTAL_SPEED_CAP is 0.45 blocks/tick; immediate clamping could erase fling speed. Decide on a controlled speed transition while preserving ordinary umbrella behavior.
4. Build canonical Blockbench art, export assets, add cable/reel/claw animation and sound, and review first person, third person and multiplayer behavior.

The original concept pass changed no runtime files. The subsequent playable prototype and its verification are recorded in `../../../GRAPPLING_HOOK.md`. Umbrella carry and Compact Racer images supplied the concept visual references.
