# Jetski — Compact Racer

Get it from the Traversal Gadgets / Tools & Utilities creative tabs, or use
`/give @s practicemod:jetski`. Place it on water and right-click the hull to ride.
Attack an empty jetski to collect it (creative removes it without a drop).

Default controls: **W** accelerates, **S** brakes then reverses, **A/D** steer,
**Ctrl** (Sprint) boosts, and **Shift** dismounts. On-screen riding instructions
and boost/recharge text are hidden. The cyan 13-segment half-ring shows remaining
boost charge with a pixel wave symbol, using the jetpack meter's size and position.
It drains while boosting and refills afterward. Pale cyan lamps mark active boost;
coral lamps mark exhausted/locked boost. While mounted, this meter replaces the
jetpack/rocket-boots meter; the equipment HUD returns on dismount.
The camera follows hull turns while
still allowing mouse free-look.
Steering rotates the rider during passenger positioning, after Minecraft records
the previous head rotation, so camera yaw interpolates between ticks. Applying
that rotation during the hull tick erased the interpolation history and caused
A/D camera judder. Bank interpolation also keeps its own previous displayed value
instead of reading newly arrived metadata as both ends of the frame.

The boost lasts three seconds from a full meter and refills over five seconds.
After emptying it, release Sprint and recover at least 35% charge to rearm.
The hull retains earned speed when boost or throttle is released. Steering turns
the bow before momentum catches up, with a looser stern under boost.

At planing speed the hull physically skips off the surface, then compresses into
the water on landing. Hops are driven by distance traveled and speed, rather than
an idle animation. Rider attachment, hull pitch, bank, splashes and landing sounds
follow that movement. There is no airborne thrust or land propulsion. The current
tuning reaches around 13.5 blocks/second cruising and 26.7 boosting on open water;
boosted hop crests approach one block above the surface.

The rear nozzle emits a directed, animated foam-and-droplet stream while driving,
with stronger spray under boost and a weak wake while coasting. The emitter uses
the Blockbench nozzle position and follows hull yaw, pitch and bank. Underwater
emissions become bubbles; above-water foam animates through vanilla bubble-pop
sprites. Idle/beached hulls and long airborne gaps do not emit propulsion spray.
`JetskiWaterEffects` emits particles locally, capped at ten foam sprites and two
droplets per tick; only a quantized pump-strength byte is synchronized. Vanilla
particle settings and distance culling still apply.

The white Compact Racer is authored in `src/main/blockbench/compact_racer.bbmodel`.
It has 47 cuboids in seven named groups and an embedded 128×128 texture. The world
and item renderers share `JetskiModel` / `JetskiGeometry` and the exported
`textures/entity/compact_racer.png`. Its stepped white shell, graphite bumper,
single charcoal saddle and compact handlebars follow concept 02. The authored
geometry is 1 block wide, about 2.05 long and .91 high. `JetskiEntity.HULL_SCALE`
applies a 1.425× world scale (95% of the previous size): 1.425 blocks wide, about
2.92 long and 1.29 high. The seat scales to local `(0, .9405, -.285)`;
collision dimensions, water samples, shadow and
wake placement scale with the hull. The bow faces local +Z. Inventory/hand models
retain their existing display size so the icon continues to fit its slot.

To edit, open the `.bbmodel` in Blockbench, save the project with its embedded
texture, then run:

```sh
python3 tools/generate_jetski_geometry.py
./gradlew build --offline
```

The exporter bakes cuboids, element/group rotations, pivots and per-face UVs into
the shared renderer. Keep geometry as cuboids with no inflate; use the single
embedded texture. `tools/author_compact_racer.py` recreates the initial design and
overwrites the editable source, so use it only when intentionally resetting it.
`tools/PreviewJetski.java` renders four views of the actual exported geometry for
visual inspection:

```sh
javac -d build/jetski-preview src/main/java/com/bilal/practicemod/JetskiGeometry.java tools/PreviewJetski.java
java -Djava.awt.headless=true -cp build/jetski-preview com.bilal.practicemod.PreviewJetski
```

The preview is written to `build/compact-racer-preview.png`. The model was opened
and saved in Blockbench; exported normals, UV bounds, texture agreement and seat
height were checked. Build and all 28 game tests pass, including steering history,
free-look preservation, yaw wrapping, duplicate positioning and scaled rider fit.
A client restart is required to load the new size and camera fix; a fresh in-game
feel pass is still needed after that restart.

The approved HUD texture is `textures/gui/jetski_boost_meter.png`, rendered by
`JetskiHudOverlay`. Regenerate it with:

```sh
javac -d build/jetski-hud tools/BuildJetskiBoostMeterTexture.java
java -Djava.awt.headless=true -cp build/jetski-hud BuildJetskiBoostMeterTexture src/main/resources/assets/practicemod/textures/gui/jetski_boost_meter.png
```

`JetskiDynamics` holds the fixed 20 Hz tuning. `JetskiEntity` owns server movement,
water sampling, collision resolution, rider validation and ten-tick input expiry.
Only intent crosses the input packet; charge, attitude and movement are synced.

Validation:

```sh
./gradlew build runGameTestServer --offline
javac -d build/jetski-validation src/main/java/com/bilal/practicemod/JetskiDynamics.java tools/ValidateJetskiDynamics.java
java -cp build/jetski-validation com.bilal.practicemod.ValidateJetskiDynamics
```

For a feel pass, ride across a broad lake, boost into a long carve, release the
throttle, then brake. Compare the regular skips with boosted skips in first and
third person. The collision box is a compact square approximation of the longer
visual hull; bow and stern visuals can overlap banks before the box stops.
