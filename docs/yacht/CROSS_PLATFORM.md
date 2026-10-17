# White Horizon platform and network verification

The yacht model and gameplay are Java. Sable supplies moving sublevels, collision,
player movement integration, and networking. Its Rapier physics library is native
Rust, so the same Java mod needs a matching native library for each OS/CPU. The
precision patch changes numerical precision, not the platform architecture.

`native_platforms.py` defines all six upstream loader targets: Windows, Linux, and
macOS, each on x64 and ARM64. The packager checks binary headers, records source and
patch provenance, and replaces the entire native archive. It never silently leaves
an upstream f32 library on an unbuilt platform. Gradle checks the current host's
entry; `yachtDistribution` requires all six entries.

## Rebuild on a target host

Install Java 21, Python 3, Git, and Rust 1.90.0 with the desired target. Resolve the
ordinary Java dependencies first with `./gradlew classes`, then:

```sh
python3 tools/yacht/fetch_sable.py
python3 tools/yacht/build_sable_f64.py --platform linux-x64
python3 tools/yacht/package_sable_f64.py --native linux-x64=build/yacht/sable-f64-source/sable_rapier/src/main/rust/target/x86_64-unknown-linux-gnu/release/libsable_rapier.so
./gradlew verifyProject -Pyacht
```

Use `--source PATH` to reuse an inspected checkout. The builder requires the exact
pinned commit and an already-applied patch; it never resets existing source edits.
For cross-compilation, `--cross` uses cargo-zigbuild for Linux (glibc 2.17) and
cargo-xwin for MSVC Windows targets. Local tooling versions: cargo-zigbuild 0.23.4,
cargo-xwin 0.23.1, Zig 0.14.1, Rust 1.90.0. CI uses Zig with the glibc 2.17 target for Linux, and the host linker for Windows/macOS.
See [cargo-zigbuild](https://github.com/rust-cross/cargo-zigbuild) and
[cargo-xwin](https://github.com/rust-cross/cargo-xwin) for cross-toolchain setup.

Package multiple libraries by repeating `--native PLATFORM=PATH`; add
`--require-all` for a distribution. Run `./gradlew yachtDistribution -Pyacht` to
create a ZIP with both mod JARs, native provenance, patch, license and usage guide.
Both client and server must install both JARs. Do not also install official Sable.

## Verification workflow

[The CI workflow](../../.github/workflows/yacht.yml) builds and runs Minecraft
server tests on each of the six native hosts. Only successful matrix jobs feed the
combined distribution. Linux x64 additionally runs two real graphical clients
under Xvfb, connected to a dedicated server through TCP and UDP latency proxies.

Locally reproduce the network fixture with an active graphical desktop:

```sh
./gradlew prepareYachtMultiplayerReview -Pyacht --no-configuration-cache
python3 tools/yacht/review_multiplayer.py --latency-ms 50 --timeout 420
```

This creates a new disposable world under `build/yacht/multiplayer/`, binds only
loopback ports 25574/25575, and uses offline test identities. No normal server
settings or saved worlds are modified. Preparation positions players only after
initial hull replication; subsequent stairs and jumps use ordinary client input.
Captain requests use normal gameplay packets. The guest disconnects and reconnects
after climbing the moving staircase. JSON results and screenshots go under
`output/yacht/multiplayer/<timestamp>/`.

The proxy adds 50 ms in each direction, plus actual processing/queueing time; it
is not an exact measured 100 ms ping. Position-step telemetry is a discontinuity
check, not an exact count of server corrections. Two clients do not establish a
16-player performance guarantee or compatibility with every other mod.

## Recorded local results

- All six native libraries compiled successfully and were packaged together. The
  package passed local Minecraft verification (66 required tests), including
  boarding the moving full-size hull from water. Boundary-mounted ladder contact
  uses the authored rung/rail boxes; normal vanilla climbing supplies motion.
- [Run 39078807863](https://github.com/bilalrnaqvi/practicemod/actions/runs/39078807863)
  passed on all six native hosts: baseline 61, bounded movement prototype 76,
  and full yacht runtime 66 required tests per platform. Linux used the portable
  glibc 2.17 native build. An earlier Linux graphical startup failure was resolved
  by renaming the reserved GLSL identifier `packed` in the existing headlight
  shader; shader calculations and vehicle behavior are unchanged.
- The same run's Linux x64 graphical two-client test passed the full sequence:
  captain travel/arrival, moving stairs, jumps, mutual replication, reconnect,
  swim boarding/disembarking and safe stopping. It recorded 591 moving ticks,
  jump heights of 1.252 blocks and maximum sampled local steps of 0.584/0.420.
  Downloaded evidence: `output/yacht/linux-multiplayer-review/20261017-232802/`.
  The bridge/landing render was visually inspected. A successful headless native
  server test is not a Windows graphical review.
- Two actual Mac ARM64 clients and a dedicated server passed a TCP-fallback voyage
  with 50 ms proxy delay each way: captain arrival, guest main-to-upper stair,
  independent jumps/landings, no mounted seats, and mutual player replication.
  Both jump heights were approximately 1.252 blocks; largest sampled local steps
  were 0.584 (walking stairs) and 0.420 (jumping). Review directory:
  `output/yacht/multiplayer/20261016-144246/`.
- A separate two-real-client moving-hull boarding test passed with TCP/UDP latency
  in `output/yacht/multiplayer/20261016-195220/`: ordinary swim/jump input climbed
  the authored aft ladder, reached the platform, and returned to water. The yacht
  stopped for the swimmer outside its hull. This bounded test does not include
  the earlier voyage/reconnect sequence.

- The complete UDP/TCP latency run passed in
  `output/yacht/multiplayer/20261017-192101/`: captain arrival, moving staircase,
  independent jumps, mutual replication, disconnect/reconnect aboard, aft-ladder
  boarding, disembarking and safe stopping for the swimmer. The server recorded
  563 moving ticks; jump heights were 1.252 blocks and maximum sampled local steps
  0.584/0.420 blocks. Reconnected guest position remained on the upper deck.
  Screenshots of the landing and captain chart were inspected. Platform screenshots
  face away from the hull; JSON position/contact evidence establishes that step,
  not those screenshots alone.

Cross-compiled binaries alone do not establish native-host execution. Windows and
Mac graphical rendering has not been reviewed on every CPU target; native-host
server tests and the Linux graphical run are separate evidence. Crash recovery
from abrupt process/power loss and 16-player party load remain untested; normal
save/reload and client reconnect were exercised.
