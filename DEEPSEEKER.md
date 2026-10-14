# Deepseeker — current implementation

Canonical model: `src/main/blockbench/deepseeker.bbmodel`.
The submarine retains its ochre/charcoal exterior and low octagonal hatch, now at the
aft starboard entry corner. The forward-opening lid clears the continuous ladder;
flat one-pixel rungs preserve arm clearance and furniture access.

Right-click to board. The server-owned animation takes **100 ticks / five seconds**:
vault, ladder slide, chair approach and swivel. First person stays first person;
third person uses the separate outside/cabin camera. The complete hull stays visible.
Seat reservation, cancellation and propulsion lock are server-owned. J exits.

## Walkable cabin controls

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


## Driving and systems

The cockpit retains live depth/history and attitude instruments, damped pitch/depth
control, enclosed-cabin breathing and water exclusion. L toggles synchronized headlights.
Sonar/power provider hooks and standby displays do not imply implemented world sonar.
Terrain headlights target the vanilla renderer; alternative renderers remain unverified.

## Editing and validation

Export an edited canonical model with:

```sh
python3 tools/generate_deepseeker_geometry.py
./gradlew verifyProject
```

Python image tools require Pillow. Restart the client after Java geometry changes.
For player animation, use [the boarding workflow](docs/animation/BOARDING.md) and
[animation direction](ANIMATION_DIRECTION.md). The source map lists related inputs.
Do not run old expedition/compaction/entry authoring scripts against the canonical model.
They reconstruct previous designs rather than exporting the current one.

On 2026-10-13, the audit ran build, PAL playback validation and all 53 GameTests successfully.
This is a dated result, not a claim about future changes. Visual comfort, complete armor
clearance, arbitrary parked terrain and multiplayer presentation still need targeted review.

Historical layouts, commands and validation counts are retained in
[the archive](docs/history/DEEPSEEKER-through-2026-10-13.md). Their “current” labels are obsolete.
