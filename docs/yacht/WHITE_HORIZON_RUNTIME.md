# White Horizon runtime investigation

The completed yacht remains a static authoring asset. The runtime integration is
**not complete**. The initial dependency spike and an opt-in moving-floor
reproduction are implemented; the captain, map, ownership and full-size yacht must
not be presented as usable gameplay until the movement gate passes.

## Reproduce the bounded fixture

Use a disposable development world under `build/yacht/run`. Automated GameTests use
`build/yacht/game-tests`, separately from manual review worlds. Normal builds exclude both the prototype source
and Sable. No yacht spawn item is registered.

```sh
python3 tools/yacht/fetch_sable.py
./gradlew verifyProject --offline -PyachtPrototype
./gradlew runClient --offline -PyachtPrototype
```

The download is pinned and checked by SHA-512 both by the fetcher and the Gradle
configuration. [Dependency lock](SABLE_PROTOTYPE_LOCK.json) records the release,
source inspection commit, and nested dependencies. A separately installed runtime
Sable JAR is required for anyone running a prototype artifact outside Gradle; it is
not shaded into Practice Mod. Never distribute the opt-in build as a finished yacht.

In a creative disposable world with cheats, `/yachtprototype spawn` allocates a
12 × 9 two-floor sub-level three blocks ahead in world +Z. The checked empty test
corridor is 12 × 8 × 32 blocks. This is an air-space physics fixture, not the final
water placement flow. It contains a U stair, upper rails, a chair and chest. The
fixture holds until `/yachtprototype start`; the script then holds for two seconds, accelerates to 0.8 blocks/second, cruises, brakes and
holds. The command permits one trip per fixture so it cannot leave its checked
corridor on a repeated start. It uses Sable's velocity API and leaves occupants unmounted. It does not
teleport the platform each tick. Test fixtures are removed on normal server shutdown;
crash recovery and retained inventories are deliberately not implemented here.

`/yachtprototype dock` instead creates a four-block-wide stationary review dock
beside a held fixture. It requires a loaded, empty 17 × 9 × 36 region ahead, rejects
obstructions before placing anything, and removes its owned stone dock on fixture
disposal. Fly onto the dock in creative mode, switch to survival, and walk across;
use `/yachtprototype start` to run the bounded trip. It is an air-space fixture,
not a water-qualified acquisition flow. Do not reuse manual worlds from failed
older experiments as clean measurement baselines.

Before starting a held fixture, `/yachtprototype pool` adds the 3 × 3 contained
pool and entry/exit steps. Its overhead floor is removed for headroom. This modifies
only the disposable test fixture, not the canonical yacht. Pool entry/exit, exterior
water/ladder transitions and a submerged dry room now have server-side tests.
The fixture still lacks the complete gate and player-input/packet telemetry;
real-client boundary checks remain. Door changes and dock transitions are covered
by server-side tests. An armor stand is an initial contact probe, not a substitute
for real player movement or multiplayer validation.

## Backend audit

Sable 2.0.6's published NeoForge release declares Minecraft 1.21.1 and NeoForge
21.1.228 or later. The inspected source targets Java 21. The verified artifact
bundles Sable Companion 1.6.0, Sable Rapier 2.0.6 and Veil 4.3.2. Create and Flywheel
are optional; neither was added. The backend's PolyForm Shield license is not a
permissive open-source license. Its terms and required notices must accompany any
redistribution; this repository fetches the unmodified upstream artifact instead of
committing or republishing it. See [upstream license](https://github.com/ryanhcode/sable/blob/d501fb3103c3247e20cb2151a99182ad5dd61adb/LICENSE.md).

The source was inspected at commit `d501fb3103c3247e20cb2151a99182ad5dd61adb`;
reproducibility of the release JAR from that commit was not established. Native
Rapier initialization succeeded on this arm64 Mac with Temurin Java 21. Linux and
Windows were not executed. Broad movement/render mixins and the included Veil
renderer remain compatibility risks requiring actual client review.

Source API references at the inspected commit:

- `SubLevelContainer.allocateNewSubLevel(Pose3d)` and the plot's embedded accessor
  allocate and populate local blocks.
- `ServerSubLevelContainer.addForceLoadTicket` keeps the probe active without a
  player; fixture disposal removes the ticket and sub-level.
- `ForgeSablePrePhysicsTickEvent` provides each physics substep; velocities are
  adjusted through `RigidBodyHandle.addLinearAndAngularVelocity`.
- This prototype's velocity feedback is not an approved navigation controller and
  must not be used to drive a full yacht into terrain.

## Experimental double-precision candidate

The official release fails the million-block travel check. A local native patch now
uses the same pinned Rapier revision's `rapier3d-f64` package, double-precision
Marten collision data and double-precision poses. It removes `simd-nightly` (the f64
backend does not offer that feature) and retains parallel physics. Performance is
unmeasured. The lock also selects `parry3d-f64` 0.26.1 (the original f32 lock
used `parry3d` 0.26.0). This is a development candidate, **not an approved release dependency**.

Canonical changes: [native patch](../../tools/yacht/native/sable-f64.patch),
[upstream license](../../tools/yacht/native/LICENSE-SABLE.md),
[builder](../../tools/yacht/build_sable_f64.py),
[packager](../../tools/yacht/package_sable_f64.py) and
[LZ4 helper](../../tools/yacht/Lz4Pack.java). The builder pins Sable commit
`d501fb3103c3247e20cb2151a99182ad5dd61adb`, Rapier commit
`38e92f117590862481a53df6fc69a5d893e29186`, Rust 1.90.0 and Cargo.lock.
It refuses to overwrite an existing checkout. It requires Git, an existing Rust
1.90.0 toolchain, Java 21 and network access for the first source/dependency fetch.

```sh
python3 tools/yacht/fetch_sable.py
python3 tools/yacht/build_sable_f64.py
python3 tools/yacht/package_sable_f64.py build/yacht/sable-f64-source/sable_rapier/src/main/rust/target/release/libsable_rapier.dylib
./gradlew verifyProject --offline -PyachtPrototype -PyachtF64
./gradlew runClient --offline -PyachtPrototype -PyachtF64
```

The packager preserves the official JAR and writes a separate candidate and local
SHA-512 manifest. Gradle verifies that manifest and the official base hash. The
candidate contains **macOS arm64 natives only**; Linux/Windows must be built and
validated separately before any shared-server use. No native binary is committed.
Prototype JARs have a distinct `practicemod-yacht-prototype` name. Normal builds do
not include these sources or Sable. The candidate is not a reproducible rebuild of
the entire official Java release; it replaces its nested native payload only.

## Automated results, 2026-10-15

| Check | Result |
| --- | --- |
| Official Sable 2.0.6 at million-block coordinates | Failed physical forward travel; retained as a regression reproduction |
| Local f64 candidate, near origin | Passed; travel 13.900000 blocks, height error 0.064931 blocks, final local horizontal drift 0.0 |
| Local f64 at X=Z=1,000,000 | Passed; travel 13.900000 blocks, same height error, drift 0.0 |
| Ten-degree turn at X=Z=-1,000,000 | Passed; Z travel 15.039678 blocks, same height error, drift 0.0 |
| Two simultaneous platforms in opposite distant regions | Passed after readiness gating; each traveled 13.900000 blocks with final drift 0.0 |
| Support during acceleration, cruise, braking and stop | Checked every ten ticks; supported/unmounted assertions passed |
| Jump/landing contact probe during cruise | Passed: upward impulse lifted probe >0.4 block, returned to the floor, final local drift 0.0 |
| Unmounted mock-player stairs in both directions during motion | Passed ascent and descent routes and landing-height checks |
| Stationary dock → stationary vessel → dock | Passed; returned to dock at tick 312 |
| Stationary dock → moving vessel → dock | Passed; returned at tick 316 and stayed at a fixed world position through tick 600 while the vessel continued |
| Door aboard moving fixture | Closed door blocked approach; changing both halves to open admitted the unmounted player |
| Dock placement and cleanup | Obstructed corridor rejected without replacing terrain or leaving a partial dock; successful fixture disposal removed the dock |
| Moving 3 × 3 pool entry/exit | Passed; 55 ticks of water detection, return through the steps to dry supported deck space |
| Vessel → outside water → moving ladder → vessel | Passed; 47 wet ticks, 27 climbable ticks, dry supported return without mounting |
| Ten-minute simulated contact soak at million-block coordinates | Passed 12,000 ticks with repeated smooth reversals, 305.575923 blocks accumulated travel, final local drift 0.0; sampled support/drift every ten ticks |
| Submerged enclosed room | Passed dry body/eye state, full air supply, supported unmounted occupant, active outside water, and dry-mask disposal |
| Full candidate verification | 76 required GameTests passed (61 existing + fifteen yacht tests), build and PAL checks passed |

These are server-side fixture tests. The armor stand probes are not players. The stair and dock tests apply horizontal velocity input and call one normal mock-player
physics tick per step; they preserve vertical velocity/gravity and do not teleport
between waypoints. Only initial test placement uses `setPos`. They do not exercise
client inputs, prediction or network packets. The turn probe is a bounded
physics test, not a destination navigation controller. The short-trip checks cover about 32 seconds. The soak covers ten minutes of
**simulated** game time with repeated reversals in the same loaded corridor, not
ten minutes of wall time or uninterrupted long-distance navigation. GameTests run
faster than real time. No client prediction or packet delivery is involved.

The contact measurements use the embedded plot center and `Pose3d.transformPosition`
and its inverse, not the moving center of mass. Initial fixed-time startup produced
intermittent drift because probes had not landed. Tests now preload their bounded
terrain, hold motion until 20 consecutive grounded ticks, and fail if contact is not
ready within 300 ticks. The measured run settled at ticks 23–98. Motion starts after
this baseline, so initial falling is neither counted as grounded drift nor hidden
by taking a baseline after travel starts. Two-region motion overlaps, but begins
independently when each probe settles. Test callbacks are registered in advance to
avoid mutating Minecraft's pending-task map during iteration.

### Transition-test corrections

The first dock fixture overlapped the platform after its initialization offset;
failed tests also left terrain in the old shared run directory. The corrected dock
meets the settled vessel edge without overlapping it, restores/removes owned terrain
on failure, and runs in the separate GameTest directory. A subsequent failure came
from the test driver applying `player.tick()` **and** an extra `move()` each tick.
That double movement disrupted the grounded state needed for step-up. The driver
now performs one player physics tick per input. Both stair tests use the same
corrected approach. No backend collision thresholds or player step height were
relaxed to make these tests pass.

A preliminary full-model partition audit found 6,639 occupied block cells and 360
unique shape/material combinations (up to 13 pieces per cell). This is a feasibility
measurement from the canonical exported scene, not a runtime import or performance
result. The complete yacht is still not spawned by either review command.

### Dry rooms and water synchronization

The pinned Sable source exposes `WaterOcclusionContainer` and `BoundedBitVolume3i`.
The prototype creates a bounded vessel-local air region using embedded plot
coordinates; Sable transforms it with the vessel during fluid queries. The test
registers the mask explicitly—enclosing blocks alone are not being claimed to
create a dry interior. Pool and exterior-water tests run without this air mask.

Upstream `WaterOcclusionContainer` explicitly states that regions are **not
networked**. Full integration must publish authoritative dry-volume definitions to
clients on spawn/tracking/rejoin and dimension changes, remove them on unload, and
recreate them on save/load. Client masks must agree with server masks, exclude the
pool, and cover the authored dry-room cells rather than the whole hull envelope.
Camera/fog/water-render suppression needs in-world review. This checkpoint implements
and tests server-side registration and cleanup only; it does not implement that
network protocol or claim multiplayer water exclusion.

## Client review status

The f64 client launched and reached completed shader upload. The available desktop
control inventory did not expose the Java window and selecting `java` returned
`Invalid app`; no in-world or first-person review was performed. Local startup log:
`/private/tmp/yacht-f64-client.log`. To review manually, use the isolated client
command above, create a disposable creative world with cheats, find loaded empty
12 × 8 × 32 air space, run `/yachtprototype spawn`, board, and run
`/yachtprototype start`. Use `/yachtprototype dock` for the wider, dock-equipped
setup described above. These are small test structures, not White Horizon. The
desktop inventory was checked again during transition work and still exposed no
Minecraft/Java app; no new in-world review was possible.

## Remaining release gates

- Real-client first-person stairs, jumping, boarding and disembarking; stationary
  and moving dock/water transitions, doors, pool and chunk boundaries.
- Two real clients, latency and correction telemetry, distant regions, disconnect /
  reconnect, sustained travel and chunk reloads. Two server probes do not prove
  multiplayer synchronization.
- Linux server and Windows client native builds; full-yacht performance and Sable /
  Veil compatibility with the other vehicles. Existing mock-player tests can log
  oversized AABB warnings under Sable; passing assertions do not resolve those.
- Full-size model-to-block/collision integration, dry interiors and pool-water
  representation, lighting/transparency/culling review.
- Acquisition and water/clearance checks, ownership/persistence, captain, map,
  destination validation, width/draft-aware routes, safe stops and chunk lifecycle.

The full yacht is still a static asset. No finished yacht spawn item or captain is
registered. Canonical model/textures/exports and existing vehicle movement/cameras
were not changed by this investigation. Keep feature expansion behind the movement
gate; no forced seats or passenger teleportation are substitutes.

Local logs: `/private/tmp/yacht-dry-room.log` records the final 76-test candidate pass;
`/private/tmp/yacht-f64-final-build.log` records the locked native rebuild. These are
local diagnostics, not shared source. The normal `./gradlew verifyProject --offline` also passed all 61 required tests
(`/private/tmp/yacht-water-normal.log`). The earlier unrelated grappling compile
failure has been resolved by concurrent work; it no longer blocks this prototype.
