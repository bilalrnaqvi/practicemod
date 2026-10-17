# White Horizon runtime handoff

White Horizon now has a full-size Sable-backed runtime in `src/yacht/java`, enabled
with **`-Pyacht`**. The canonical model is unchanged: the runtime exporter partitions
its cuboids into 6,639 native block cells, with separate solid/translucent materials,
shape collision, climbable swim ladders, light-emitting fixtures and door variants.
The native package now contains Windows, Linux and macOS libraries for both x64
and ARM64. A dedicated-server test with two actual Mac clients has passed moving
stairs, jumping, captain travel, reconnect and swim boarding/disembarking. Native-host CI results are
recorded in [platform verification](CROSS_PLATFORM.md): all six native hosts passed
server tests, and Linux x64 and Mac ARM64 passed real two-client tests.

## Build and use

Target: Minecraft 1.21.1, NeoForge 21.1.235, Java 21. The ordinary build excludes the
yacht and Sable. Both client and server need the yacht build and the same patched
Sable dependency; Sable is not shaded into the mod. The profile checks the locally
built artifact against `build/yacht/sable-f64-local.json`, the source patch and its
upstream lock. `./gradlew yachtDistribution -Pyacht` creates a portable ZIP containing
both required JARs; it refuses a package missing any of the six native libraries.
See the [archived dependency investigation](../history/WHITE_HORIZON_RUNTIME-prototype.md)
for pinned source revisions, native build commands and the official f32 failure.

```sh
./gradlew verifyProject --offline -Pyacht
./gradlew runClient --offline -Pyacht
```

1. Obtain **White Horizon Deed** from Tools & Utilities in creative mode, with
   operator command `/yacht deed`, or craft `DID / INI / DID`: D diamond block,
   I iron block, N nether star.
2. Right-click the deed while looking horizontally toward loaded open water.
   The hull origin is placed 42 blocks ahead. Validation requires a 68-block
   turning area, at least five blocks of depth, no overhead terrain or living
   occupants, world-border clearance and separation from other yachts. Failed
   placement keeps the deed. Four simultaneously loaded yachts per dimension
   are the current limit.
3. Board using either aft swim ladder and platform, then use the modeled stairs.
   Players are never required to mount passenger seats. Wait for preparation to end.
4. Right-click the captain in the upper forward bridge. White marks the yacht;
   orange marks the selected destination; gold marks route waypoints. Blue cells
   indicate surveyed water, green terrain/shallow water, gray unloaded territory.
   The map is a local world-coordinate chart, with a 128-block destination radius.
   A blue pixel does not alone establish clearance for the whole yacht.
5. Owner, designated navigator or operator may select destinations while stopped.
   The server validates reach, permissions, range, depth and the swept turning
   envelope before accepting travel. Cruise is 0.8 blocks/second with acceleration,
   braking and slow turns. Stay aboard while the captain sails.
6. Use the chart's stop button or `/yacht stop` to cancel. Any aboard passenger
   can request an emergency stop. `/yacht status` reports the nearby yacht.

Owner commands `/yacht navigator <player>` and `/yacht revokeNavigator` manage
captain access. `/yacht steward <player>` and `/yacht revokeSteward` manage service
lockers. Right-click modeled lockers to access persistent 27-slot storage. Cabin
doors toggle their canonical open/closed block variants and refuse to close over
living occupants. Guest beds reset the rest timer without changing land respawn.
Seating, bars, dining, machinery and entertainment furniture otherwise remain
social/decorative spaces; no engine economy or unrelated systems were introduced.
Structural breaking and additional block placement aboard are protected.

## Movement, persistence and safety

Sable provides sub-level collision, ordinary entity motion, embedded chunk
rendering and its player movement protocol. The yacht controller applies velocity
feedback and holds the authored waterline; it does not move passengers or teleport
voyages. A one-time assembly correction aligns Sable's initial center of mass with
the validated placement point. Native buoyancy is disabled for yacht cells to avoid
competing with the waterline controller. Other vehicles' code is unchanged.

Route search is bounded A* over eight-block cardinal steps, with every intervening
block sampled against a 34-block-radius conservative turning envelope. Column
surveys are cached only during planning; live movement rechecks present and ahead
clearance. Terrain changes, unavailable water chunks, nearby yachts or external
players stop travel. This deliberately rejects narrow channels rather than promise
a tight docking maneuver. Long-distance routing and new ocean generation are not
implemented. Planning is capped at 1,024 expanded nodes.

Ownership, navigator/steward permissions, captain UUID, model revision, locker
contents and door state are stored in Sable's vessel user data. Block cells and pose
are persisted by Sable itself. Reloaded controllers start STOPPED and require a new
route; they do not blindly resume stale travel. Sable supplies saved player tracking
points for reconnects. Force-load tickets are held while occupied, moving, preparing
or planning, and released when idle or stopping. Empty vessels cancel travel.

Dry volumes use the authored lower/machinery masks and exclude the pool. The server
replicates masks to nearby clients; clients replace them on changes and expire stale
masks after three seconds without snapshots. Pool cells expose vanilla source-water
fluid behavior with the canonical translucent surface. Partial visual water height
and vanilla fluid height are not identical. Rendering uses native section rebuilds,
block atlas textures and separate transparent child models, not a single giant
entity bounding box. This has not been benchmarked for a 16-player party.

## Validation and review

- Canonical static geometry: 3,707 clearance samples across 54 routes pass.
- `./gradlew verifyProject --offline -Pyacht`: **66 required tests passed**.
  The normal profile separately passed **61 required tests**.
- Full-runtime GameTests exercise complete-hull player contact, moving authored
  stairs, deep-water route survey, changed terrain rejection, moving full-hull swim-ladder boarding and
  ownership serialization, in addition to the existing mod tests.
- The bounded prototype separately covers jumping, doorways, docks, water/ladder
  transitions, dry rooms, distant coordinates and a simulated ten-minute soak.
  See the archived investigation for its 76-test result and exact scope.
- [Local runtime review gallery](../../output/yacht/runtime-review.html).
- Native baked-model renders: `output/yacht/native-review/`.
- In-world integrated-client screenshots: `output/yacht/world-review/`. The client
  stair harness sends ordinary forward input along the main-to-upper U stair;
  its report records the actual local landing position. The end-to-end client run
  opened the captain interaction, sent a destination request and reached ARRIVED
  at approximately (1000, 185.065, 1015.423). Review fixtures may set
  initial camera/player positions; that is not the gameplay travel mechanism.
- `-PyachtModelReview` renders baked models at the title screen. `-PyachtWorldReview`
  opens the disposable `White Horizon Review` save and modifies its test basin;
  never point that harness at a valued world. It requires an existing review save.

Restart review reloaded the same hull UUID, preserved its owner and position,
started STOPPED, and reopened the persisted captain's chart. Actual block right-clicks
also opened the service locker and toggled a guest cabin door. Review output is under
`output/yacht/reload-review/`; reproduce with `-PyachtWorldReview -PyachtReloadReview`
after the initial disposable-world run. Automated startup failures now fail the yacht
verification task even if Minecraft itself exits with a misleading zero code.

The full two-client dedicated-server sequence passed with added TCP/UDP latency,
moving stairs, jumping/landing, mutual player replication, reconnect aboard,
final-hull swim boarding/disembarking and safe stopping. See the platform report
for measurements and native-host CI results. Remaining validation includes abrupt
crash recovery, full-hull dock edge cases and performance under 16-player party load. The native runtime remains an explicit yacht
build profile; ordinary builds retain the existing dependency footprint.

Existing saved vessels are not automatically migrated after a canonical model/palette revision; such revisions require a deliberate migration before deployment.

## Changed source locations

- [Runtime Java](../../src/yacht/java/com/bilal/practicemod/yacht/runtime/): block/item
  registration, hull controller, navigator, captain/network interactions, map,
  furniture, tests, review harnesses and the yacht-only fluid-render mixin.
- [Runtime exporter](../../tools/yacht/export_runtime.py): derives block models,
  collision shapes, door variants, textures, recipe and physics properties from
  the unchanged canonical Blockbench project.
- [Gradle](../../build.gradle), [mod metadata](../../src/main/templates/META-INF/neoforge.mods.toml)
  and [English names](../../src/main/resources/assets/practicemod/lang/en_us.json):
  opt-in profile, dependency declaration, generation, test guard and item names.
- Shared design/model/runtime guidance updated in place; the prototype investigation
  is retained under `docs/history/`. Review frames and logs are not source checkpoints.
