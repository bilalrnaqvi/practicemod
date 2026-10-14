# Mechanical Dolphin

A rideable blue-and-white Panorama submersible with slim cyan sensor eyes,
a transparent framed canopy, a prone cockpit and vertically animated flukes.

Get it from Traversal Gadgets / Tools & Utilities, or:

```mcfunction
/give @s practicemod:mechanical_dolphin
```

Place on water, then right-click to mount. Hit an empty dolphin to collect it.
Default controls:

- **W** swims forward; **S** brakes and reverses slowly.
- **Mouse** steers and pitches the dolphin underwater. Look down to dive.
- **Space / Jump** swims upward and performs a powered breach near the surface.
- **Shift / Sneak** dismounts.

The vehicle supplies air while mounted. Forward cruising reaches about 16.4
blocks/second. Swim toward the surface and hold Jump to make a strong tail stroke:
the dolphin launches several blocks above the water (about seven in the isolated
physics check), keeps forward momentum, arcs under gravity, pitches nose-down,
and splashes back in. Upward swimming can also breach naturally without Jump.
Release Jump and spend at least twelve ticks back in water to rearm the powered
stroke, after its 35-tick cooldown. Holding Jump continues upward swimming but
cannot repeatedly trigger the powered stroke. There is no air or land thrust.

The server validates rider input and owns movement. Missing input expires after
ten ticks. Collision clears blocked velocity components; unloaded chunks are not
searched for water. The prone rider and first-person eye follow the pitched cockpit through the arc. Empty
craft drift upward for retrieval. The collision box is a compact axis-aligned
approximation; the snout, fins, and tail extend beyond it.

Implementation: `MechanicalDolphinDynamics` holds fixed-step physics;
`MechanicalDolphinEntity` samples water, resolves movement, maintains the rider,
and emits splash/bubble effects. The renderer and inventory item share the
219-part `MechanicalDolphinModel`, generated from the approved Blockbench source.
Glass uses a separate translucent pass; cyan lamps remain bright in darkness.
The item renderer uses the same geometry at inventory scale.

Validation commands:

```sh
./gradlew build runGameTestServer --offline
javac -d build/dolphin-validation src/main/java/com/bilal/practicemod/MechanicalDolphinDynamics.java tools/ValidateMechanicalDolphinDynamics.java
java -cp build/dolphin-validation com.bilal.practicemod.ValidateMechanicalDolphinDynamics
```

Five GameTests cover placement/overlap, mounting/air/persistence/pickup,
actual-water breach/re-entry, input validation/walls, and input expiry.
The breach fixture preserves real vertical movement and water sampling while
recentering horizontally within its small water column. The standalone physics
validator additionally checks natural breaches, rearming, underwater turning,
reverse, and absence of air/land thrust.

Actual runtime-mesh preview: `output/mechanical-dolphin/runtime-preview.png`,
rendered by `tools/PreviewMechanicalDolphin.java` with the actual vanilla wide/slim
player meshes. `tools/validate_dolphin_fit.py` checks their closed-canopy envelope;
pose restoration is checked by the Java harness; fin/tail animation has its own validator.
The build and all 48 registered GameTests passed on 2026-10-11. The development
client launches successfully. Interactive camera comfort and armor/held-item
clearance still need an in-game visual pass; the automated preview is not a screenshot.

## Integrated Panorama art

`src/main/blockbench/mechanical_dolphin.bbmodel` is the canonical art source.
The original slim cyan eyes are preserved, without pupils or pronounced brows.
Export changes with `python3 tools/generate_mechanical_dolphin_geometry.py`.
This extracts the embedded 1024px texture and bakes the geometry into separate
opaque, illuminated, glass and tail groups. It does not rewrite the artist source.
`output/blockbench/mechanical-dolphin/runtime-export.json` records source hashes,
part counts and the runtime tail pivot.

Native `animation.mechanical_dolphin.idle` and `swim` clips animate the two
pectoral fins, dorsal fin, tail root and child flukes. They supersede the original
single-hinge prototype tail clips. The native model faces -Z; the exporter rotates
it 180 degrees into runtime +Z, so native X/Z keys have the opposite runtime sign.

The canopy is closed. Its hinge and lift struts remain prepared in the source;
opening/closing animation and boarding choreography are a separate later task.

## Third-person camera

Rear and front F5 views use an eight-block camera distance from the pilot, up
from Minecraft's default four. The 5.3-block-long, 3.5-block-wide model extends
almost four blocks behind the pilot's eye, so the wider view leaves about four
blocks behind the tail during level forward travel. This gives the flukes, turns
and breach arcs more room on screen without changing the vehicle's size or FOV.
The distance is independent of rider scale. Vanilla terrain collision still
shortens it around obstacles; first person and mouse steering are unchanged.

## First-person cockpit hands

The cockpit draws both vanilla skin arms around the authored handle centers,
using dedicated standard/slim player meshes and the player's sleeve visibility
settings. Arms render with the hull's lighting, depth and attitude, keeping the
hands attached through turns, dives and breaches. Only the first-person pilot
sees these extra arms. Normal hand/item rendering is suppressed while mounted
and returns on dismount; third-person rider rendering is unchanged.

The first-person eye sits at hull-local `(0, 1.05, 0.45)`, 0.45 blocks behind
the previous pilot eye. Both grips now fit below the forward view at the default
70-degree FOV. Mouse look and the eight-block third-person distance are retained.

Validation: `./gradlew build --offline` passes. The actual arm meshes enclose
both grip centers for standard and slim skins, and both contacts fit comfortably
inside the default perspective. `tools/PreviewMechanicalDolphinCockpit.java`
produces `output/mechanical-dolphin/cockpit-hands-preview.png`, showing both skin
models and the same view with hands omitted to inspect the grips. This is an
offline geometry/texture preview; live camera comfort still needs a playtest.

## Fin and tail animation, 2026-10-13

Resting underwater uses a slow 4.19-second cycle: a two-degree tail sweep,
one-degree fluke feathering, small pectoral adjustments and restrained dorsal
flex. Swimming increases the cadence to a 0.87-second cycle and the tail stroke
to about 18 degrees, with up to ten degrees of delayed fluke counter-pitch.
The tail drives propulsion visually; pectorals trim and stabilize, while the
dorsal fin remains comparatively steady. Turns add differential fin trim, and
pitch changes add matching pectoral adjustments.

Each client entity integrates its own phase at 20 Hz and interpolates per frame.
Speed changes blend over several ticks without resetting the stroke. Slow empty
craft retrieval drift stays in the resting range. Out of water, motion fades to
a neutral pose instead of treating falling velocity as swimming speed. These
changes are visual; vehicle dynamics, rider pose, camera and canopy are unchanged.

The exporter preserves all 219 parts, 1,604 quads and the original texture, with
separate illuminated passes following each joint. Flukes inherit the root tail
transform before applying their own local rotation.

Rebuild editable clips from runtime steady states with
`python3 tools/author_dolphin_swim_clips.py`, then export geometry and build.
This runs `ValidateMechanicalDolphinAnimation`: 10,800 transition samples cover
acceleration, stopping, turning, breaching, dry settling, re-entry, reverse,
frame-rate independence and loop closure. `tools/preview_dolphin_tail.py` uses
Python with Pillow/NumPy and the existing `build/boarding-classpath.txt` to render
runtime meshes and independently compare them with native clip geometry.
Eight mesh poses match within 0.000001 blocks. Preview GIFs are
`output/blockbench/mechanical-dolphin/idle-motion.gif` and `swim-motion.gif`,
with half-speed variants and `fin-tail-poses.png`. These are software renders,
not in-game footage; live gameplay visual review remains outstanding.
