# ATV — off-road prototype

Get it from **Traversal Gadgets** or **Tools & Utilities**, or run
`/give @s practicemod:atv`. Use the item on the top of solid ground, then
right-click the ATV to ride. It seats two players side by side: the first drives,
the second rides as passenger. A third player cannot board. If the driver leaves,
the remaining occupant moves to the driver seat and can take control with fresh
input. Attack an empty ATV to collect it. Creative
placement keeps the item; creative pickup removes the vehicle without a drop.

Default controls: **W** accelerates, **S** brakes then reverses, **A/D** steer,
and **Shift** dismounts. Mouse free-look stays available while the camera follows
steering, using the same passenger-history approach as the jetski.

The first tuning pass cruises at about **10.2 blocks/second**. Acceleration builds
progressively, coasting retains momentum, and cornering leaves some tire slip.
Reverse is slower and reverses steering direction. There is no boost or jump key:
the terrain produces the jumps.

The collision body climbs slabs, stairs and full blocks up to 1.05 blocks high.
At crawling speed it stays planted. Above 3.6 blocks/second, an obstacle rise
produces a physical upward impulse; a full block at cruise gives roughly half a
block of extra hop height. Leaving a ledge preserves horizontal momentum under
gravity, with no mid-air steering or propulsion. Water slows the vehicle and
disables propulsion.

Each tire uses five collision-shape samples over its circular footprint so support
builds as its leading edge reaches a block and releases at the trailing edge.
Unclipped contact heights determine terrain attitude; missing contacts do not
force the chassis level. A damped chassis spring compresses on climbs and
landings, rebounds, then settles. Acceleration pitches the nose up, braking dives
it, and cornering rolls the body.

Pitch and roll retain angular velocity across climbs, takeoff and landing.
Block step-up supplies a small signed impulse instead of instantly adopting the
collision box's near-vertical travel angle. Filtered terrain and airborne trajectory
targets apply spring torque, with pitch/roll changes bounded to 4/3 degrees per
tick. Reverse climbs raise the rear. Pitch targets stop at 35 degrees with a
40-degree mechanical limit.

All four wheels inherit chassis heave, pitch and roll. Independent damped travel
is calculated in that same local frame and limited to 0.14 blocks of droop and
0.12 blocks of compression, keeping hubs attached during jumps and uneven terrain.
Pitch shares the same synchronized/interpolated pose as roll, heave and wheel
travel, including the rider rendering, rather than using a separate rotation
packet interpolation timeline.
Both bucket seats and seated riders follow the sprung chassis. Tires spin with signed
travel; the front wheels and steering wheel steer. Ground-colored dust and landing
sounds reinforce contact. There is no artificial idle/flat-ground bounce.

The implemented art is **CRAG**, based on the approved B / CRAG reference:
green fenders and hood based on `#1F4700`, two charcoal bucket seats, an open square-bar roll cage,
steering wheel, warm headlights, steel bumpers, and oversized treaded tires.
The editable source is `src/main/blockbench/crag.bbmodel` (534 cuboids, eleven
groups, embedded 1024×1024 atlas with individually painted face islands). The item and world renderers share
`AtvModel`, with separately animated wheel groups and steering wheel. The
sloped bucket cushions are about 0.87 blocks high at the hips, with the rider hip
pivots at 0.99 blocks and clearance above both standard and slim player heads.

After editing in Blockbench, run `python3 tools/generate_atv_geometry.py` to
export the baked per-face UV mesh and `textures/entity/crag.png`. The initial
authoring recipe is `tools/author_crag.py`; rerunning it replaces artist edits.
The current actual-renderer scale/fit preview is `output/crag/two-seat-preview.png`.
`output/crag/steve-scale-preview.png` records the previous single-seat revision.
`output/crag/model-preview.png` and `detail-preview.png` show the earlier approved art.

The world collision box is a 2.2-block square, 2.4 blocks tall; the longer visible
bumpers can overlap obstacles slightly. The chassis attitude and wheel travel
are visual suspension over that upright collision box, not four separate rigid
wheel colliders.

`AtvDynamics` contains the 20 Hz handling and spring tuning. `AtvEntity` owns
collision resolution, terrain sampling, rider placement, and state sync. Clients
send only throttle/steering intent. The server validates the rider, entity ID and
finite input, clamps input, expires it after ten ticks, and clears it when the driver dismounts. Passenger input is rejected, and a
passenger leaving does not interrupt driver control.
Use matching current client/server mod builds so both sides share the same
seat layout and collision dimensions.

Validation:

```sh
./gradlew build runGameTestServer --offline
javac -d build/atv-validation src/main/java/com/bilal/practicemod/AtvDynamics.java tools/ValidateAtvDynamics.java
java -cp build/atv-validation com.bilal.practicemod.ValidateAtvDynamics
```

Build and all 35 game tests passed, including six ATV tests for placement,
mounting, persistence, pickup, slabs/stairs/full blocks, fast hops, tall walls,
landing compression, unauthorized/invalid/stale inputs, airborne momentum, and
passenger camera interpolation. The standalone handling checks also passed.
The development client loaded the ATV resources without ATV errors. A four-view
preview captured from the actual model renderer is at `build/atv-preview.png`.
Live first/third-person driving still needs a human feel pass; the UI automation
tool could not access the Java game window.

For that pass, compare a slow crawl and a fast run over the same grass-block
steps, drive off a short ledge, then brake into a turn on flat ground. Watch the
wheels and saddle in third person, then check the vertical movement and free-look
in first person. The vehicle should compress and rebound on impacts, settle on
flat ground, and never accelerate while airborne.

CRAG revision validation (2026-10-07): the revised model has a wider stance,
chunkier tires, lower cage/cockpit, layered fenders, recessed lamps and a green
palette based on `#1F4700`. Wheel centers, spin radius, terrain roll baseline and
rider height follow the revised mesh. `./gradlew build runGameTestServer --offline`
passed with all 40 required tests. The four-view preview captures the actual
renderer geometry under neutral daylight. Live riding still needs a human
first/third-person feel pass.

Current paint palette: base `#1F4700`, highlights `#336310`, shadows `#132E00`,
with charcoal mechanics, steel trim and warm lamps.

Detail pass: rebuilt wheel lips around recessed hubs with lug bolts; replaced
oversized cage corner blocks with fitted clamps; added layered fender plates,
hood vents/latches, dashboard instruments and bucket-seat padding. Per-face
pixel islands supply local edge highlights, sparse wear and material variation
at roughly two texels per model unit. Geometry and driving dimensions remain
compatible with the existing animations. Build passed and geometry/UV validation
passed. The larger inspection render is `output/crag/detail-preview.png`.

The user approved this detail pass for implementation. The approved Blockbench
source is exported to `AtvGeometry`, and both the world and inventory renderers
use its `crag.png` texture. The existing item ID remains `practicemod:atv`.
Packaged output: `build/libs/practicemod-1.0.0.jar`.


Full-size proportion revision (2026-10-09): the user clarified that realistic
proportions beside Steve take priority over doubling every dimension, including
changes to the seat. The approved green model now measures **2.53 blocks long,
1.57 blocks wide, and 2.32 blocks to the cage top**. The wheelbase is 1.68 blocks.
This retains the single-seat buggy/ATV hybrid; it is not a scale replica of a
straddle-seat quad. For a real-world proportion reference, the
[Polaris Sportsman manual](https://publications.polaris.com/owner/owners-manuals/9941679_R02/0000934919.XML.xml/%24/0000946043)
lists a 2.11 m length and 1.22 m width. CRAG needs additional cockpit and cage
space for Minecraft's broad torso, large head and unjointed seated legs.

The wheelbase is longer, tire assemblies remain round, and the body is narrower.
The bucket seat is wider and raised, with a sloped thigh cushion and an upright
back. Lowered footrests support the boots; the steering wheel is raised and tilted
away from the forearms. Rider limbs, hip offset, generated wheel/steering pivots,
wheel spin radius, suspension baselines, collision height and inventory display
scale follow the edited model. The original UV atlas and paint are preserved.
`tools/resize_crag.py` records the one-time geometry migration and refuses to
apply it twice; future edits belong in the bbmodel.

Validation: `./gradlew build runGameTestServer --offline` passed all **47 required
tests**, including current-size obstacle fixtures, rider attachment, side dismount
clearance and the cage's overhead collision. `python3 tools/validate_atv_fit.py`
renders the actual runtime ATV and vanilla player models and checks both standard
and slim skins (including outer clothing) against all 516 cuboids. The neutral
seated pose passes with 0.018-block padding tolerance and intentional side-grip
hand contact. The preview is a software render, not an in-game screenshot. Live
first/third-person appearance, extreme suspension poses, armor and multiplayer
presentation still require a playtest. Packaged output remains
`build/libs/practicemod-1.0.0.jar`.


Two-seat enlargement (2026-10-11): user found the single-seat version too small
and requested room for two people. CRAG now measures **3.12 blocks long × 2.58
wide**, with the cage top at **2.40 blocks**—roughly twice the previous ground
footprint. It has two fitted bucket seats spaced 1.12 blocks apart, an offset
driver steering wheel, a passenger grab rail, paired footrests, a wider hood and
cage, a 2.1375-block wheelbase and larger round tire assemblies. The existing
green paint and UV atlas are retained. `tools/expand_crag_two_seats.py` records the
one-time migration; `crag.bbmodel` remains the editable authority.

Both riders receive vehicle turns once per tick and retain free-look. Only the
first occupant sends/has driving input accepted. The server remains authoritative.
The remaining player is promoted when the driver exits; prior throttle is cleared.
Dismount clearance follows the new collision width. Inventory display scaling is
adjusted for the larger model.

Validation: build and all **48 required GameTests** pass, including occupied
boarding, third-rider rejection, separate rider positions, passenger input
rejection, camera turns for both occupants, passenger departure, driver handoff,
climbing and overhead/dismount clearance. The actual-mesh audit passes both seats
with standard and slim player skins and outer layers against all 534 cuboids,
and checks the occupants against each other. Padding tolerance remains 0.018
blocks; deliberate hand contact with the steering grips/grab rail is allowed.
`output/crag/two-seat-preview.png` shows the actual rendered model and vanilla
player meshes. Live two-client presentation and first-person feel remain unverified.
Restart the development client to load this revision.


Compact two-seat correction (2026-10-11): user found the two-seat version too
wide. The exterior is now **2.19 blocks wide**, down from 2.58 (15% narrower),
with its 3.12-block length and 2.40-block cage height retained. The shell, hood,
fenders, tire widths and track are narrowed, and the cage is slightly narrower.
Both fitted seats retain their full size and 1.12-block center spacing; controls
and footrests are unchanged. Rock sliders are trimmed around the outside boots.
The collision width is 2.2 blocks; generated wheel probes and suspension baselines
follow the narrower track. `tools/compact_crag_two_seats.py` records the migration.

Build and all **48 required GameTests** pass. The mesh fit check passes standard
and slim occupants in both seats, including outer layers and occupant-to-occupant
clearance, with the existing padding/grip tolerances. Current preview remains
`output/crag/two-seat-preview.png`. Restart the client for the new model and
collision width. Live multiplayer/first-person presentation still needs a playtest.


Terrain suspension revision (2026-10-13): removes the single-tick pitch snap and
the independent world-level wheel frame. Adds persistent angular momentum,
tire-footprint support sampling, chassis-local wheel springs with travel stops,
and a common client suspension interpolation path. The upright Minecraft
collision box still performs step-up and physical hops; this is a constrained
suspension model, not a separate rigid-body collider for every tire.

Validation includes deterministic repeated-impact, angular-continuity, reverse,
airborne-droop and settling checks, plus an in-world full-block/ledge traversal
regression. Live first/third-person driving and multiplayer appearance still
need a feel pass after restarting with the new build.
