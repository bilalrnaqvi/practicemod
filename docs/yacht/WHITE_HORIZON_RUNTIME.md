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
script holds for two seconds, accelerates to 0.8 blocks/second, cruises, brakes and
holds. It uses Sable's velocity API and leaves occupants unmounted. It does not
teleport the platform each tick. Test fixtures are removed on normal server shutdown;
crash recovery and retained inventories are deliberately not implemented here.

The fixture is smaller than the planned complete prototype: pool, gate, doorway,
turning, player-input/packet telemetry and dock transitions remain to be added after
straight motion works. An armor stand is an initial contact probe, not a substitute
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

## Results

See the accompanying test results recorded below. No full-yacht first-person or
multiplayer pass is claimed. Canonical model, textures and derived modeling exports
were not changed by this investigation. Existing vehicle movement/camera code was
not edited.

### Measured movement gate, 2026-10-15

| Check | Result |
| --- | --- |
| Pinned Sable added to existing mod, no fixture | Build and all 58 existing GameTests passed; Rapier initialized on this Mac |
| Client startup with Sable | Reached renderer/shader setup; no in-world first-person review claimed |
| Corrected near-origin fixture, 640 ticks | Unmounted armor stand remained supported through acceleration/cruise/braking; measured travel 13.899455 blocks, final deck-height error 0.064892 blocks, final grounded horizontal drift 0.0 |
| Same motion at X=Z=1,000,000 | **FAIL**: transformed local origin ended at Z offset -0.665908 blocks despite commanded forward motion; the positive travel gate failed |
| Full prototype verification | 60 tests executed: 58 existing tests and near-origin contact probe pass; far-coordinate probe fails, intentionally keeping the movement gate red |
| Full yacht, stairs/jumping, water, turning, real players and two clients | Not executed; no acceptance claimed |

The contact probe uses the embedded plot center and `Pose3d.transformPosition` /
`transformPositionInverse`, not the moving center of mass, for measurements. Grounded
drift is measured from tick 39, after initial contact settles and before motion starts.
Earlier measurements from the center of mass were invalid and are not reported as
collision failures. The test checks final contact, not continuous ten-minute stability.

The far-coordinate failure persists with explicit force-loading and in a separate
batch away from neighboring GameTests. This is consistent with upstream's open
[precision issue #604](https://github.com/ryanhcode/sable/issues/604).
A proposed [floating-origin fix #1546](https://github.com/ryanhcode/sable/issues/1546)
changes Rust/Rapier and explicitly leaves rendering and distant multiplayer regions
unresolved. It was assessed as a research candidate, not adopted as a supported fix.
The release's Rust dependency is `rapier3d`; the inspected upstream physics tests also
contain a note about awaiting 64-bit mode. No verified pinned replacement passed
this project's movement matrix.

During Sable regression runs, oversized AABB warnings also originated in existing
mock-player movement tests at the test runner's randomly distant coordinates. The
58 assertions pass, but those warnings must be investigated before claiming vehicle
compatibility in actual gameplay. Missing optional `create:flywheel` physics tags
were logged without preventing startup; Create was not added to silence them.

**Decision:** retain the source/texture design and stop feature expansion at the
failed backend gate. No acquisition item, captain, map, routes, ownership, water
handling or finished-yacht runtime rendering has been implemented. A giant rideable
entity or passenger teleporting would not resolve this failure. The next engineering
step is a precision-correct backend build (including rendering and multi-region
behavior), followed by the full movement matrix and only then full-yacht integration.

The prototype Java sources, optional Gradle configuration, fetcher and dependency
lock are the changed implementation files. Bulky logs remain local under
`/private/tmp/yacht-floor-tests-7.log`, `/private/tmp/yacht-prototype-final.log` and
`/private/tmp/yacht-normal-verify.log`. The normal `verifyProject --offline` run passed
all 58 required tests; static yacht checks still pass 54 routes / 3,707 samples.

### Final rerun limitation

After isolating the prototype run directory and adding its one-fixture cap, the final
verification attempt stopped during compilation of unrelated concurrent work:
`GrappleTargeting.java:74,81` references missing `GrapplePhysics.CLEARANCE`.
Those files were not changed for this task. Consequently the final directory/cap
revision did not reach GameTests. The measured results above are from the preceding
completed prototype run; the successful normal-build result predates that concurrent
compile failure. Rerun both verification commands once the grappling changes compile.
