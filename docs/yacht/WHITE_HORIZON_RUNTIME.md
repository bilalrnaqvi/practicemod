# White Horizon runtime investigation

The completed yacht remains a static authoring asset. The runtime integration is
**not complete**. The initial dependency spike and an opt-in moving-floor
reproduction are implemented; the captain, map, ownership and full-size yacht must
not be presented as usable gameplay until the movement gate passes.

## Reproduce the bounded fixture

Use a disposable development world under `build/yacht/run`. Normal builds exclude both the prototype source
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

The fixture is smaller than the planned complete prototype: pool, gate, doorway,
player-input/packet telemetry and dock transitions remain to be added after
the remaining client and boundary checks pass. An armor stand is an initial contact probe, not a substitute
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
| Unmounted mock-player staircase ascent during motion | Passed five walking waypoints and upper-landing height check |
| Full candidate verification | 67 required GameTests passed (61 existing + six yacht tests), build and PAL checks passed |

These are server-side fixture tests. The armor stand probes are not players. The
stair test applies movement to a mock player and checks collision; it does not
exercise client inputs, prediction or network packets. The turn probe is a bounded
physics test, not a destination navigation controller. Support checks cover about
32 seconds, not ten-minute stability. The final candidate revision repeated the
67-test pass with the same travel/drift results.

The contact measurements use the embedded plot center and `Pose3d.transformPosition`
and its inverse, not the moving center of mass. Initial fixed-time startup produced
intermittent drift because probes had not landed. Tests now preload their bounded
terrain, hold motion until 20 consecutive grounded ticks, and fail if contact is not
ready within 300 ticks. The measured run settled at ticks 23–98. Motion starts after
this baseline, so initial falling is neither counted as grounded drift nor hidden
by taking a baseline after travel starts. Two-region motion overlaps, but begins
independently when each probe settles. Test callbacks are registered in advance to
avoid mutating Minecraft's pending-task map during iteration.

## Client review status

The f64 client launched and reached completed shader upload. The available desktop
control inventory did not expose the Java window and selecting `java` returned
`Invalid app`; no in-world or first-person review was performed. Local startup log:
`/private/tmp/yacht-f64-client.log`. To review manually, use the isolated client
command above, create a disposable creative world with cheats, find loaded empty
12 × 8 × 32 air space, run `/yachtprototype spawn`, board, and run
`/yachtprototype start`. This is the small test structure, not White Horizon.

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

Local logs: `/private/tmp/yacht-f64-final-verify.log` records the final 67-test candidate pass;
`/private/tmp/yacht-f64-final-build.log` records the locked native rebuild. These are
local diagnostics, not shared source. The normal `./gradlew verifyProject --offline` also passed all 61 required tests
(`/private/tmp/yacht-normal-final-verify.log`). The earlier unrelated grappling compile
failure has been resolved by concurrent work; it no longer blocks this prototype.
