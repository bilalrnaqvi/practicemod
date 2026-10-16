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
cargo-xwin 0.23.1, Zig 0.14.1, Rust 1.90.0. Native builds on CI use the host linker.
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
- Native-host CI is running at
  https://github.com/bilalrnaqvi/practicemod/actions/runs/38982624977.
- Two actual Mac ARM64 clients and a dedicated server passed a TCP-fallback voyage
  with 50 ms proxy delay each way: captain arrival, guest main-to-upper stair,
  independent jumps/landings, no mounted seats, and mutual player replication.
  Both jump heights were approximately 1.252 blocks; largest sampled local steps
  were 0.584 (walking stairs) and 0.420 (jumping). Review directory:
  `output/yacht/multiplayer/20261016-144246/`.
- The subsequent UDP/reconnect run was interrupted by an unavailable primary
  monitor during client startup. It did not produce a pass.

Cross-compiled binaries and CI configuration alone are not claims that the game
has executed on those operating systems. Update the runtime handoff with actual
matrix results before declaring a cross-platform release verified.
