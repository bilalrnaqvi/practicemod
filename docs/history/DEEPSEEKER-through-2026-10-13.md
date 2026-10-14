> ARCHIVE: historical decisions and reports through 2026-10-13. Not active instructions or safe current rebuild commands. Paths in the original text are repository-relative.

# Walkable cabin controls

The remappable **Leave submarine pilot seat** action defaults to N. It parks the hull
at its current position and attitude, reverses the final chair animation, and releases
WASD movement into the cabin aisle. The empty chair remains facing backward.
Right-click the chair to approach, sit, and replay the original final swivel into the
controls. The key does not return the player to the chair. J still exits the submarine.

Right-click the rear cases for shared 27-slot submarine storage, the workbench for a
3×3 crafting menu, and the bunk to sleep. Storage keeps slot positions across saves
and drops if the submarine is destroyed. The bunk uses vanilla sleep timers and
multiplayer night voting; normal night, dimension, and nearby-monster restrictions
apply. Waking leaves the player in the parked cabin. The mobile bunk does not set a
world-block respawn point.

Cabin movement remains attached to the vehicle, with local aisle bounds, breathing,
server-validated furniture targeting, and client movement prediction. No ocean blocks
are replaced. Existing model geometry and authored animation tracks are reused.

# Current model revision — restored outline-ladder exterior

The current canonical model uses the old round hatch and painted exterior guide,
with current cabin furniture. The indoor ladder is restored to its original side-wall
X/Z placement; its rails and fittings are trimmed below the low roof. The hatch
opens aft 90 degrees about the raised rear hinge. The model builds successfully.
Boarding now plays the 6.4-second Blender hatch-start sequence with synchronized
player, hatch, chair and cinematic camera. Walking thigh twists are corrected.
Latest runtime source: `src/main/blender/deepseeker_boarding_runtime.blend`.
This supersedes conflicting layout/boarding statements in the historical notes below.

# Approved expedition cabin — integrated 2026-10-08

The approved 688-element interior is now the runtime source in `src/main/blockbench/deepseeker.bbmodel`, synchronized with `deepseeker_expedition.bbmodel`. It includes the teal bunk, combined map/repair bench, deeper stacked expedition cases, twin air bottles, simplified overhead conduits, and starboard ladder/hatch. The old cockpit divider and sliding door are removed. This section supersedes the older model/layout descriptions below.

The exporter packs both embedded texture sheets into a 4096×2048 runtime atlas without scaling or changing pixels/alpha, exports all cuboids and quad meshes, and binds the hatch by its authored group. `DeepseekerCabinRig.java` derives hatch, handle and gauge pivots from the model. The air needle gently oscillates ±2 degrees every 2.4 seconds in game. Existing pilot, controls, fan rotation and live screen attachment positions are retained.

Boarding now follows the exterior starboard ladder, transfers through the side hatch, turns toward the interior ladder, descends to the landing, and walks down the open aisle to the swivel chair. The hatch grip is placed toward the outer side of its lid for access. The route and hand poses are fitted to this model. The interior observation camera is forward of the storage; the water exclusion volume includes the raised hatch trunk.

Validation: the build and all 46 GameTests passed; 1,130 standard/slim player and clothing poses plus the chair sweep had no intersections beyond the existing 0.008-block contact tolerance. Static furniture, ladder landing, gauge, storage opening, fan and joystick checks pass. The export validator verifies texture pixels/alpha, UVs, normals and moving-part membership; regeneration is idempotent. `build/submarine-boarding-poses.png` is a software preview of the actual exported geometry, not a live game capture. Live visual playback, multiplayer presentation, armor clearance and shader appearance still need in-game review.

Rebuild with `python3 tools/author_deepseeker_expedition.py`, then `python3 tools/generate_deepseeker_geometry.py` using Python with Pillow, then `./gradlew build --offline`. The authoring script reads the retained pre-expedition model in `src/main/blockbench/archive/` and writes both current model files. Do not rerun the old compaction, entry or cabin-dressing scripts on the approved model. Validation scripts: `validate_deepseeker_expedition.py`, `validate_deepseeker_runtime.py` (Pillow), and `validate_submarine_boarding.py` after compilation.

Storage groups and front-opening pivots are prepared for later interaction. Free walking and usable inventories are still future work. Restart the development client to load the regenerated model; rebuilding does not update an already-running client.

---

# DEEPSEEKER cockpit

The current authored model exports **461 cuboids / 5,524 triangles**. Sources are `src/main/blockbench/deepseeker.bbmodel` and `deepseeker_screen_bindings.json`; run `python3 tools/compact_deepseeker.py` and `python3 tools/generate_deepseeker_geometry.py` to rebuild. The existing 2048² atlas and cube-only Blockbench format remain in use.

## Compact stern and closed machinery section

Overall length drops from **16.887 to 10.484 blocks (37.9% shorter)**. Geometry aft of model Z = -58.5 uses half its former longitudinal length. Tilted tail pieces and the circular top hatch translate rigidly to retain their shapes. Width and height are retained. The cockpit, seat, console sizes/positions, pilot eye, forward canopy, front lamps, and live instrument bindings remain unchanged; all 136 protected source elements match their previous transformed geometry and UVs. Generated pilot and panel definitions are byte-for-byte unchanged.

The utility room is removed: no workbench, bottles, storage cases, emergency kit, secondary displays, service wheel, or rear floor aisle. A sealed octagonal bulkhead just behind the seat carries two louver banks, a narrow drive cover, and two short conduits. Its 38 cuboids present shallow fixed equipment rather than a room or doorway. Machinery stays visible at every render distance and in the inventory model. The rear 120-degree horizon is closed within 2.25 blocks of the pilot eye.

The water-exclusion shader applies the inverse aft compression before its existing pressure-shell test, preventing the old long dry volume from extending past the stern. Boarding rays and chase-camera fitting automatically use the regenerated geometry. The boarding regression now targets the shorter aft side.

Validation: build and all **21 GameTests** pass; 4,095 camera poses pass; the pressure shell passes all 20,480 enclosure rays; forward visibility remains **166/181** with identical blocked angles. Standard/slim player meshes clear both controls and the new machinery. Compaction is idempotent. The geometry/UV preview is `build/deepseeker-compact-preview.png`; it is a software render, not an in-game screenshot. Restart the client to inspect the changed model in-game.

Current validation commands:

```sh
python3 tools/compact_deepseeker.py
python3 tools/generate_deepseeker_geometry.py
python3 tools/validate_deepseeker_cabin.py
python3 tools/validate_deepseeker_enclosure.py
./gradlew build runGameTestServer --offline
python3 tools/validate_deepseeker_player_mesh.py
javac -d build/deepseeker-preview src/main/java/com/bilal/practicemod/DeepseekerGeometry.java src/main/java/com/bilal/practicemod/DeepseekerPilot.java tools/PreviewDeepseeker.java tools/PreviewDeepseekerCompact.java
java -Djava.awt.headless=true -cp build/deepseeker-preview com.bilal.practicemod.PreviewDeepseekerCompact build/deepseeker-compact-preview.png
```

The remaining sections record earlier revisions. The former utility cabin and its clear-aisle requirements below are superseded. `dress_deepseeker_cabin.py` now forwards to the compact machinery authoring tool.

## Previous rear utility cabin (superseded)

Implements the original rear-cabin concept: twin strapped air bottles and pressure dial, two vented power cabinets, a repair bench with drawers/tools and a static systems display, three latched storage cases, a red emergency kit, an aft service panel with octagonal handwheel, and ceiling pipes and warm lamp lenses. The original pilot station, external shell, glass, camera and controls are retained. These are decorative fittings; the cabinet indicators and diagnostic screen use the existing atlas's offline tiles, and the service panel is not an interactive door.

`tools/dress_deepseeker_cabin.py` authors the `rear_cabin` group in the Blockbench source deterministically. It replaces 43 sparse legacy fixtures with 95 cuboids, including the extended rear deck. Then run `tools/generate_deepseeker_geometry.py`. Small features use simple panels; hidden faces are omitted. The existing 2048² atlas is unchanged, with no extra textures or render passes. Lamp lenses use full-bright vertices in the existing opaque pass; no new dynamic lights, particles, world scans, entities or ticking systems are added.

Render budget, counted from exported faces (two triangles per quad):

| | Before | After, nearby | After, beyond 48 blocks / item |
|---|---:|---:|---:|
| Cuboids submitted | 495 | 547 | 452 |
| Quads | 2,966 | 3,061 | 2,708 |
| Triangles | 5,932 | 6,122 | 5,416 |
| Vertices submitted | 11,864 | 12,244 | 10,832 |

The net nearby increase is **190 triangles / 3.2%**, including interior and exterior. Geometry can reduce FPS: this renderer transforms and submits every selected vertex on the CPU each frame, and the GPU rasterizes the resulting faces. Costs multiply with visible vehicles and also depend on screen coverage, transparency, lighting and hardware. These counts bound the incremental mesh cost; they are not an FPS measurement or proof of no lag. The 48-block cutoff skips new fittings before vertex transformation; the glass/exterior remain unchanged. Rear dressing uses the same opaque buffer, not a draw call per prop.

Validation: build and 19 GameTests passed; final mesh passes the 6,250-triangle whole-model and 750-triangle cabin budgets, inset hull bounds and a 1.875-block-wide clear aisle. Forward visibility remains 166/181 clear horizon rays; the seated-player mesh audit and all 20,480 shell-enclosure rays pass. Authoring is idempotent and all 454 retained source elements plus the atlas are byte-for-byte equivalent as JSON values. `PreviewDeepseekerCabin` renders the actual exported mesh to `build/deepseeker-rear-cabin-preview.png`; this is a software geometry/UV preview, not an in-game screenshot. Client FPS and moving-camera lighting still require an in-world playtest.

Validation commands:

```sh
python3 tools/dress_deepseeker_cabin.py
python3 tools/generate_deepseeker_geometry.py
python3 tools/validate_deepseeker_cabin.py
python3 tools/validate_deepseeker_visibility.py
python3 tools/validate_deepseeker_enclosure.py
python3 tools/validate_deepseeker_player_mesh.py
./gradlew build runGameTestServer --offline
javac -d build/deepseeker-preview src/main/java/com/bilal/practicemod/DeepseekerGeometry.java src/main/java/com/bilal/practicemod/DeepseekerPilot.java tools/PreviewDeepseeker.java tools/PreviewDeepseekerCabin.java
java -Djava.awt.headless=true -cp build/deepseeker-preview com.bilal.practicemod.PreviewDeepseekerCabin
```

## Boarding the enlarged hull

Right-click boarding now tests the exported model's faces, including glass, instead of relying on the small original boat box. A use-key event sends a boarding request only when a nearby face is hit. The server repeats the ray using the player's normal entity interaction reach, checks blocks and intervening pickable entities, then uses the existing single-pilot mount interaction. Both hands' item actions are canceled for a hull click so a held block or submarine item is not accidentally placed. Sneak-use retains vanilla behavior. The gameplay movement/collision box is unchanged.

The boarding payload increments the network version to 12; restart the client and update both sides for multiplayer. Build and all 21 GameTests pass, including mesh rays from six sides at multiple yaw/trim angles, boarding the aft side beyond the old box, blocked and out-of-reach attempts, wrong entity ids, and occupied-seat rejection. Actual mouse interaction still needs an in-game check after restarting the client.

## Third-person visibility update

The piloted F5 view now has a minimum hull/glass block light of 14 independently of the headlight switch. Directional shading and brighter environmental lighting remain. Underwater chase fog starts beyond the hull (camera-to-center distance plus 12 blocks), retaining water color and distant fog; blindness and darkness keep their normal fog. Other vehicles and the cockpit retain their existing lighting.

The desired boom is 95% of the previous perspective-fit distance, subject to the existing hull-clearance minimum. Near an exposed water surface, the orbit angle lowers and the full model is refitted, keeping the camera 0.75 blocks below the actual fluid height. Bounded column checks cover both the hull and the desired camera column. Terrain probes still run after adjustment; if terrain contraction pushes the eye back across the surface, the existing dry cockpit fallback applies.

Validation: the build and all 20 GameTests pass, including exposed/stacked water, just-above-surface detection, solid ceilings and terrain collision. The projection validator checks 4,095 poses, including the lower surface orbit angles and the closer boom, with no mesh or screen-edge clipping at the tested steady poses. Final in-game lighting and moving surface transitions still need a visual playtest.

## Third-person camera

F5 now uses a vehicle-centered chase camera while piloting. The target is **32.5% of screen height**, interpreting the requested 30–35% as height rather than pixel area. Full exported geometry is fitted to the current perspective, aspect ratio and hull attitude; a 70% width cap keeps broad side views visible. The hull sits below center (its vertical midpoint at 62% of screen height), with an 18-degree downward viewing bias and a level horizon. Mouse look remains immediate, while camera distance eases independently of frame rate. F5 rear/front views and ordinary first person remain available.

Nine terrain probes shorten the camera boom; water does not obstruct it. When terrain would force the camera into the hull, it temporarily uses the cockpit, with an on-screen message and recovery hysteresis. The 32.5% target applies in unobstructed steady views; collision contraction and transitions can change apparent size, and wide side views can be smaller. This changes the camera only, not the authored submarine size or its gameplay collision.

Design references: [Subnautica's Cyclops hull cameras and proximity sensors](https://unknownworlds.com/en/news/machinery-update-released) emphasize awareness around a large hull; [UBOAT's orbit-camera revision](https://store.steampowered.com/news/posts/?appids=494840&enddate=1575193707&feed=steam_community_announcements) improves presentation of vessel bobbing and elevation. The Minecraft adaptation is an elevated hull-centered orbit, stable horizon, visible travel space and a cockpit fallback for confined terrain. These are design inferences, not reproductions of either game's camera implementation.

Validation: `./gradlew build runGameTestServer --offline` passes all 19 tests, including water-transparent and corner-probe terrain collision. `ValidateSubmarineCamera` passes 2,925 projection cases across aspect ratios, FOVs, orbit angles and hull pitch, plus easing and fallback hysteresis checks. Height ranges from 24.66–32.50%; 11 cases fall below 30% because the width cap takes priority. `PreviewSubmarineCamera` produces `build/deepseeker-third-person-preview.png`, a software render of rear, quarter, side and front framing. Steering feel and tight-space transitions still need an in-world playtest.

## Closer controls without window obstruction

The seat/eye remains `[0,61,-75]`. Each console's display-area center moves one-third of its fore/aft distance toward the pilot. Console geometry and its bound live display planes are 80% of their previous size, centered 1.5 model units lower; side consoles move 2.5 units outward to leave space beside the legs. The pedestal/support bottoms still meet the deck. The preview shows the controls below the forward horizon.

Build passes. Visibility remains 166/181 clear horizon samples with both side endpoints clear. The actual standard/slim player mesh audit passes, with conservative player/control AABB separations of 0.150/0.166 blocks. The clearance audit now checks spatial separation in all three axes, accommodating consoles beside the knees rather than requiring every side-console corner to be ahead of the toes. Software preview inspected; in-game comfort requires restarting and playtesting.

## Seat-height refinement

Lowered the seat and authored pilot eye by **2 model units / 0.125 blocks**, from `[0,63,-75]` to `[0,61,-75]`, to sit more naturally behind the controls. The pedestal still meets the deck. The generated reference lowers the gameplay rider and keeps hull placement stable. The horizontal visibility check passes (166/181 horizon rays clear, both ±90-degree endpoints clear); a larger drop was rejected because it aligned the eye with a side frame.

## Seated-player alignment and control clearance

The latest cabin shell is 1.5× the preceding revision (3× original), while the seat and control dimensions retain their preceding size. The pilot reference is `[0,63,-75]`. All control groups and live display bindings move **18 model units / 1.125 blocks farther forward relative to the pilot**, and support columns extend down to the enlarged deck. The visibility arc retains 168/181 clear horizon samples with both side endpoints clear.

`SubmarinePilotPose` anchors the rendered pilot to the interpolated hull eye, applies the hull's yaw/pitch/damage roll about that eye, and explicitly restores seated leg and pants transforms. `SubmarinePlayerMixin` retains standing dimensions while mounted, preventing swim/crouch eye-height changes from shifting the rendered hull. The seated renderer bypasses swimming/gliding body rotations and crouch render offsets. Normal poses resume after dismount. This fixes missing render alignment that the earlier envelope-only checks did not exercise; it does not establish which pose caused the reported screenshot-free clipping.

`python3 tools/validate_deepseeker_player_mesh.py` uses Minecraft's actual baked standard/slim player cubes and outer clothing, the runtime seated-leg helper, and vanilla render scaling/translation. Both meshes have 1.455 blocks of minimum deck clearance, remain inside the inset pressure volume, and have at least 0.421 blocks of fore/aft separation from every transformed control cube. This is a mesh check, not an in-game screenshot or a multiplayer interpolation test. `./gradlew build runGameTestServer --offline` passes 18 GameTests, including swim/crouch pose rejection and dismount recovery. Client startup loaded the new mixins and completed resource initialization. The UI tool does not expose the Java window, so in-world rendering and comfort remain unverified. Restart any previously loaded client for the final geometry.

## Earlier full-size cockpit revision

The entire Blockbench project is now authored at **2× the previous linear size** (all cube bounds and rotation pivots are scaled, UVs stay unchanged). The pilot reference is `[0,42,-50]`; eye-to-deck clearance increases from 0.86875 to **1.7375 blocks**. The seat attachment follows the generated eye height so the cabin floor remains below the player, rather than enlarging around an unchanged boat seat. The floor extends beneath the controls and their supports meet it.

The controls use 85% of the enlarged cabin scale and sit another 6 model units forward, opening space around the pilot while preserving the 180-degree horizon view. Live panel dimensions/bindings follow the controls, including the attitude card. `hull_scale_from_original` in the bindings records the shell scale for the canonical water-exclusion volume and shell-repair utility. The inventory miniature is normalized to retain its display size; headlight origins follow the enlarged exported model and culling bounds include the longer hull. Vanilla boat gameplay collision remains unchanged.

Validation: build and all 17 GameTests pass, including real mounted-eye/deck alignment and at least 10cm of clearance beneath the unposed player's feet. `validate_deepseeker_player_fit.py` checks padded seated torso/head and leg envelopes through ±24 degrees of pitch: minimum deck clearance is 0.275 blocks, and both envelopes remain within the inset pressure volume. The 20,480-ray enclosure audit and 168/181 clear horizon samples still pass. Software previews were inspected. Client-side player rendering and comfort still need a fresh in-game check after restarting the development client; an already-running client keeps its previously loaded classes.


The same exporter bakes physical screen bases from the bindings using the surface cube's complete parent chain. Fixed mount rotations occur once. The final entity pose applies yaw and the existing damage wobble to both hull and panels. There are no animated parent bones in this source; future authored bone animation must update geometry and attachment transforms together. Glass uses a separate translucent pass after the opaque hull and instrument surfaces. The exporter identifies all 35 panes through their authored `cockpit_glass` ancestor: 32 canopy facets, two side ports and the forward pane. Previously only the cube named `forward_glass` was classified as glass; the other panes went through the cutout pass, which made their blue tint opaque despite the atlas's existing alpha. Both the entity and item renderers share the corrected classification. Frames, longerons, ribs and hull remain opaque. Text uses Minecraft's normal depth-tested font pipeline; full-bright pixel quads use nearest sampling and write depth. Nothing uses a see-through render type or modifies the shared atlas. There are no dynamic texture allocations to release; ordinary resource reloads reload the PNGs.

Before the full-size revision, the pilot station was raised two model units and moved 21.5 units forward into the glass canopy: the authored `pilot_camera` origin was `[0,21,-25]`. The seat and its pedestal follow that move; the consoles and live screen bindings move 13 units forward, and the forward deck extends under the station. Front outer/inner rim thickness is halved to reduce blind spots. All 495 exported cubes and 35 glass elements remain.

`generate_deepseeker_geometry.py` exports `DeepseekerPilot` from the camera group. Passenger attachment rises 2/16 block on both client and server. Hull, lamps, shadow origins and water exclusion share the generated reference, translating by its negative around the mounted pilot eye; pitch and damage roll pivot around that eye. The model's longitudinal alignment is recentered on the new pilot station. Empty vehicles use the same raised standing-player defaults. Vanilla boat steering and collision remain; its existing ±105-degree mouse-look range already covers the requested 180-degree left-to-right arc. Camera lens FOV is unchanged. Minecraft 1.21.1's LevelRenderer includes the mounted vehicle in first person, so the cockpit renders once.

Pilot-relocation validation: `./gradlew build runGameTestServer --offline` passes all **17 GameTests**, including actual raised passenger attachment, eye alignment, stable empty/mounted height, and ±90-degree look at a nonzero vehicle heading. `python3 tools/validate_deepseeker_visibility.py` traces all exported cubes with their full parent transforms: 168/181 one-degree horizon samples are unobstructed through glass, including both side endpoints. Remaining occlusion is three narrow groups of structural frames (at most five degrees each). The enclosure audit passes 20,480 rays, including the new eye. The six-view software preview shows exterior, instruments, and forward/left/right pilot views in `build/deepseeker-enclosure-preview.png`. The source was opened in Blockbench. In-game client transparency, moving-camera appearance, and multiplayer visuals still require playtesting.


## Depth and history

Client entity ticks measure the loaded water column at the pilot reference at 5 Hz. Each search reads at most 65 cells at one X/Z, never loads a chunk, and never uses assumed sea level. The terminal water cell uses its own fluid surface height after the cell above has been checked. One block is one meter. A pilot eye outside water is 0; missing chunks, build-height limits, a 64-block search cap, or a solid roof produce NaN / `-- M` and `NO DATA`. Lava does not count as water. A waterlogged block with collision anywhere in the column makes the result unknown. Open air directly above the water establishes a surface; an air cavity above cave water is valid, but a roof directly on the water is not.

This is local observational telemetry, not server gameplay state. Each client's view can legitimately be unknown while that client's terrain is unloaded. No extra network payload is needed. No world queries occur in the renderer. A `SubmarineHistory` belongs to each entity, stores at most 120 timestamped samples at 2 Hz, and is cleared on mount/dismount transitions and dimension changes. Despawn releases it with the entity. Rendering never adds samples. World-tick timestamps preserve gaps; paused game time pauses the chart. A jump in ticks leaves a gap instead of compressing samples. The range starts at 10 m, rounds upward by 10 m, expands on sampling, and shrinks only after the entire visible window requires at most half the range for 10 seconds.

The chart occupies the existing 128×64 panel. Oldest is left, NOW right; zero is at the top. Invalid samples and tick discontinuities break the trace. Empty history is not filled.

## Other instruments

Sonar has an animated standby sweep with no contacts. Power and systems read OFFLINE. `SubmarineClientHandler.setInstrumentProvider` is the client integration hook. Its provider must return cached telemetry (no world scans in rendering); sonar coordinates are normalized right/forward within the unit circle, with at most 128 contacts rendered. Only genuine obstacle data should enable sonarOnline.

Minecraft positive pitch is nose-down, so nose-up = `-getXRot()`. Nose-up moves the horizon down inside the aperture. The card counter-rotates about local Z relative to the mounted housing. The boat has no gameplay roll, so the normal reading is zero; its existing damage wobble is reflected. The runtime raster masks the pitch ladder/horizon to the aperture, fully covers the fallback enamel with an opaque background, and leaves the fixed silhouette and housing untouched. No square card is translated outside the bezel.

## Validation

Earlier glass-material validation (before pilot relocation): `./gradlew build --offline` passed. Regeneration is deterministic; all 453 cubes were checked against the authored hierarchy, with 35 translucent panes and 418 opaque structural cubes. Every textured pane face already contains partial alpha in the runtime atlas. The Blockbench project was inspected from `[0,19,-3.5]`; the eye clears the console and window frames, so the seat and camera were retained. This authoring preview does not validate Minecraft's in-world translucent ordering; an in-game first-person check is still needed.

Standalone semantics checks:

```sh
mkdir -p build/instrument-tests
javac -d build/instrument-tests src/main/java/com/bilal/practicemod/SubmarineDepth.java src/main/java/com/bilal/practicemod/SubmarineHistory.java src/main/java/com/bilal/practicemod/SubmarineAttitude.java tools/ValidateSubmarineInstruments.java
java -cp build/instrument-tests ValidateSubmarineInstruments
./gradlew build runGameTestServer --offline
```

The standalone harness checks fractional surfaces, negative world coordinates, lava, roofs, waterlogged obstruction, unknown/capped scans, constant/descent/ascent/surface traces, gaps, range changes, timestamps, buffer isolation and attitude signs. The additional GameTest checks the adapter against real water, stone, lava and waterlogged slab blocks.

Results: `build` and all 12 dedicated-server GameTests passed. All 32 standalone instrument checks passed. The development client reached its render/resource initialization successfully, but the computer-use tool could not attach to its Java window. First-person text readability/alignment while moving, third-person hull occlusion and glass ordering, live mounting/dismounting, and a two-client multiplayer visual check remain unverified. The source-level transform and fluid checks do not substitute for those visual checks.

## Headlights

Press **L** while piloting to toggle the authored bow and shoulder lamp lenses. The binding is configurable in Controls and takes priority over Advancements while piloting when both share a key. Holding the key does not repeatedly toggle. The server validates the pilot, synchronizes the switch, and saves it with the submarine; older saves default off. Lights remain on when parked.

Terrain illumination is now a per-fragment spotlight in the solid, cutout, cutout-mipped, and translucent terrain passes. It uses camera-relative coordinates and the submarine's frame-interpolated position, yaw, pitch, and damage roll. The origin follows the exported bow lenses. Smooth cone and distance falloffs replace the old integer light levels, block-cell field, 5 Hz updates, and chunk rebuilds. The intensity multiplier is **1.2**, a 20% increase over the new shader's baseline. The beam fades out at 24 blocks. Vanilla textures, tint, ambient occlusion, existing brighter lights, transparency, and fog remain in use. Entity and block-entity materials are not replaced; the authored lamp lenses still render full-bright.

Each of up to four nearby lights has a 32×32 perspective shadow map built from collision-shape rays, with 16-bit axial depth. Water transmits light; solid surfaces occlude it. Moving lamps recast at the interpolated render pose every frame; stationary shadow maps refresh at 5 Hz for terrain changes. The fragment shader uses bilinear percentage-closer filtering and receiver-plane depth comparisons to soften shadow edges and avoid self-shadow speckles. This bounded shadow resolution can soften or miss very thin distant blockers. It does not place blocks or change server lighting/spawning. Dynamic shadow textures are released when no lights remain, when the world changes/disconnects, and on shader reload. Shader resources reload through NeoForge's normal shader registration lifecycle.

Validation: `./gradlew build runGameTestServer --offline` passed all 14 tests. Tests cover pilot authorization, toggling, save/legacy state, water transmission, exact wall intersections, fractional shadow-ray movement, and removing an obstruction. The development client compiled/loaded the GLSL shader and lighting mixin successfully. In-world visual quality, frame-rate profiling, resource reload during gameplay, and a two-client visual check remain unverified because the UI tool does not expose the Java game window. Alternate terrain renderers and third-party shader pipelines have not been tested; the substitution only targets vanilla terrain shader names.

### Wider beam and exterior lamp treatment

The searchlight's soft cone now spans tangent radii .44–.68 (previously .27–.42), roughly 62% wider at the same distance. The shadow frustum expands from ±.5 to ±.8 in both CPU sampling and shader projection. Range remains 24 blocks and intensity stays at 1.2×.

With headlights enabled, the hull and glass receive a minimum block-light level of 10 while retaining brighter environmental light and normal directional shading. Each authored bow lens also has a small blue-white additive halo and a faint, expanding shaft fading over 5.5 blocks. Both effects use the hull's transforms; collision rays shorten the center and rim of each shaft at walls. Effects depth-test normally, do not write depth, and disappear with the headlight toggle. Expanded culling bounds include the shafts. No textures or per-frame GPU resources are allocated by these effects.

The build passes. Visual balance from first/third person and transparency ordering still require an in-game check.

## Weighted depth control and trim

Rise/descend now command damped pitch and depth control instead of assigning an instant ±0.12 vertical velocity. At forward cruising speed the hull gradually trims toward ±24 degrees, and the vertical motion approaches the path implied by forward flow and hull pitch. Angular speed is capped at .85 degrees/tick and vertical acceleration at .004 blocks/tick². Trim has damping, so reversals take time; release levels the hull and settles vertical momentum into neutral-buoyancy depth hold. At rest, ballast-style control provides a slower .035-block/tick depth change with about 8 degrees of trim. Astern flow reverses the dive-plane trim so ascent still means upward travel. This is a tuned gameplay approximation, not a full hydrodynamic simulation.

Vanilla horizontal steering, collision-aware movement, gravity outside water, and vehicle movement/pitch synchronization remain in use. Only the controlling simulation instance applies trim; remote boats interpolate normal vehicle pitch. The cockpit, attitude display, searchlight, and lamp shafts already share the pitched hull transform. The existing upright boat collision box remains unchanged.

Validation: standalone `ValidateSubmarineDynamics` checks acceleration and pitch-rate bounds, ascent/descent symmetry, slow stationary ballast, inertia on reversal, settling on release, and astern ascent. GameTests cover gradual depth control/depth hold and actual tilted diagonal movement at a nonzero yaw. In-game motion feel and two-client interpolation require playtesting.

## Enclosed pressure cabin

The Blockbench source remains cube-only and Bedrock-editable. `tools/seal_deepseeker_hull.py` reproducibly repairs the missing side shell, adds a continuous overlapping pressure liner, seals both observation-window surrounds, fills the rear pressure bulkhead, and thickens/overlaps the tapered canopy facets. The old opaque plates behind the side windows are disabled so the sealed glass windows remain usable. The exporter emits 495 cubes, including the same 35 glass elements. The shell repair itself retains the original lights, instruments, UV atlas and overall silhouette; the later canopy pilot relocation is described above.

The mounted pilot's water eye-fluid state is exposed as cabin air on both sides, suppressing underwater HUD/audio state and breathing water; dismount restores water detection immediately. The first-person camera suppresses only water immersion, and the screen water overlay is canceled while piloting. Third-person cameras outside the hull retain underwater rendering. Water-colored distance fog starts outside the cockpit; blindness/darkness fog remains respected.

The existing terrain shader additionally discards water-atlas fragments inside up to four nearby moving pressure hulls, including observation-window recesses. Inverse hull transforms follow interpolated translation, yaw, pitch, and damage roll. This keeps surface water from slicing through the interior while surfacing/diving, with headlights either on or off. Only water textures in the translucent terrain pass are clipped. Ocean blocks and fluid simulation remain unchanged. This is an enclosed entity cabin, not a world-block air pocket or a new rotated collision mesh.

Validation: the build and all **16 GameTests** pass, including water detection before boarding, cabin air while riding, unchanged ocean blocks, and immediate water detection after exit. `python3 tools/validate_deepseeker_enclosure.py` checks 20,480 rays from five interior locations against only structural shell/glass elements, excluding props that could hide holes; all rays hit the repaired enclosure. Both the repair script and geometry regeneration are idempotent. `tools/PreviewDeepseeker.java` renders a software exterior/cockpit preview to `build/deepseeker-enclosure-preview.png`; it was inspected for sealed panels and cockpit clearance. Sampled rays and software previews do not replace in-game visual checks of the waterline, transparency, and third-person transition. Third-party terrain renderers are untested.


### Approved shoulder lamps and tail refinement

`src/main/blockbench/deepseeker.bbmodel` is the source used by the runtime exporter. Reopen this file after external edits; an already-open Blockbench tab retains its older model. The project now uses Blockbench Generic Model format so the tail can be a true tapered mesh with a continuous side face. Existing hull/cockpit cubes and groups remain intact. The exporter supports planar quad meshes alongside cuboids.

The upper shoulder lights use shallow dark housings, inset warm lenses and protective brows. The tail has one tapered yellow panel, narrow dark edges, a compact base and a complete, planar-mapped `03` on both sides. `tools/refine_deepseeker_lights_tail.py` applies this design and synchronizes the embedded Blockbench atlas with the runtime PNG (requires Pillow). Then run `python3 tools/generate_deepseeker_geometry.py` and `./gradlew build --offline`. Restart Minecraft to load regenerated Java geometry. Do not rerun the older hull-compaction workflow on the mesh model.

Validation: build and fan-animation checks passed; unrelated model elements were compared against the pre-edit backup and remained unchanged. Software previews verify both tail markings and shoulder lamp geometry (`build/deepseeker-refinement-preview.png`). In-game appearance still requires a restarted client.

Attachment correction: the complete tail assembly is shifted forward 20 model units onto the compact hull roof; its mounting shoe penetrates the roof and contains the fin bottom. Shoulder lights now follow the 45-degree shoulder with an upward/outward/forward lens normal, instead of retaining the original upright box rotation. `python3 tools/validate_deepseeker_attachments.py` checks full base support and 81 unobstructed lens samples per lamp.

Concept lamp matching: shoulder lamps now use a beveled mesh housing with a recessed well, compact nearly square warm-white lens, amber perimeter pixels and a broad roofward protective brow. Dedicated atlas tiles avoid stretching the original small lamp pixels or rib textures. Attachment validation includes mesh occlusion as well as cubes. Tail geometry remains unchanged from the supported-roof correction.

Forward-lamp revision: both upper lenses now have an exact model-space -Z optical axis, with no upward or outward tilt. Each shoulder rib is split around a compact lamp socket, retaining a continuous rear attachment spine. A tapered housing joins the forward lens to that spine. The attachment validator asserts forward normals and unobstructed lenses. See `build/deepseeker-forward-lamps.png` for the actual exported front and quarter views.

Uniform bezel revision: separate upper brow and lower sill removed. Both forward-facing lamps now have a continuous 2.4-unit border before the lamp scale, with offset bevels giving equal width on every side. Socket lift clears the canopy glass so it no longer masks the lower border. Attachment checks now include the four bezel edges.

Approved final lamp design: compact forward-facing lenses with uniform bezels set into a continuous widened canopy brow and shoulder frame. This replaces the separate raised lamp pods. The approved design is saved in `src/main/blockbench/deepseeker.bbmodel`, exported to `DeepseekerGeometry.java`, and verified by the build and attachment checks. Actual-model views are in `build/deepseeker-forward-lamps.png` and `build/deepseeker-full-current.png`.

## Animated boarding

Right-clicking any visible part of the submarine reserves the pilot seat and starts a 14.1-second, server-timed sequence at one fixed starboard boarding point. Small, broken ochre marks sit flush against the hull to indicate the route; there is no physical ladder or rail assembly. The player climbs with alternating limb motion, keeps both feet at the painted side route while the torso leans inward, braces with one hand and lifts the side-offset hatch grip with the other. The player then pulls upward, tucks both legs together and vaults directly inward without walking around the front or reversing heading. After descending, the player crosses the sliding doorway and steps up onto the rear-facing chair. The chair first swivels 180 degrees toward the door, then carries the seated player through a second 180-degree turn into the driving position. The door closes after the seated limbs clear it.

Explicit headings replace the old movement-derived heading and vanilla mount-yaw reset. Boarding preserves the selected perspective. First person follows the bent torso and renders the animated skin arms, body and legs in world space, omitting the head. Third person retains the three shots outside, below the hatch and at the chair; F5 can switch perspectives during boarding. Held items are hidden during the sequence. Propulsion stays locked until completion; J cancels at any stage. The actual passenger follows the same route used by the renderer. Water breathing starts after descent into the cabin.

The doorway is taller to clear the chair step. The cushion, pedestal and armrests are fitted around the actual seated player mesh; the pilot eye reference and driving position remain unchanged. `tools/author_deepseeker_entry.py` authors the initial openings; `tools/refine_deepseeker_boarding.py` applies the approved painted guide, hatch grip, fitted chair and taller doorway. Both authoring scripts and `tools/generate_deepseeker_geometry.py` are idempotent. Do not rerun the older compaction script, which would restore the old wall.

Validation: `./gradlew build runGameTestServer --offline` passes all 35 current GameTests, including fixed boarding start, reservation, propulsion lock, timed completion, chair/rider heading agreement and cancellation. `python3 tools/validate_deepseeker_cabin.py` checks the approved markings and clear openings. After compiling, `python3 tools/validate_submarine_boarding.py` sweeps 1,130 actual baked standard/slim skin and clothing poses at half-tick intervals against all exported geometry, including the moving hatch, door and chair. It also sweeps the chair against fixed furniture. No intersections exceed the 0.008-block contact tolerance. `build/submarine-boarding-poses.png` is a six-stage software preview, not an in-game capture. The Minecraft window is not exposed to the UI tool, so live playback, two-client presentation, armor/equipment outside the tested skin layers and arbitrary terrain around a parked submarine remain unverified. The older enclosure audit reports 29 pre-existing forward-shoulder leaks.
