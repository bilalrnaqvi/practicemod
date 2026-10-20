# Claude project handoff: practicemod

## Purpose and scope
This repository is a Minecraft mod development project. The broader design direction discussed with the user is a set of playful, physics/movement-oriented gadgets with an improvised garage-built "Scav Pack" aesthetic.

Important: the design ideas below are conversation context, **not a claim that each item is implemented**. Inspect the actual source and assets before planning or changing code. Do not silently treat proposed features as working.

## Verified build configuration
From `gradle.properties` and `build.gradle` on the default branch:
- Minecraft: **1.21.1**
- NeoForge: **21.1.235**
- Java toolchain: **21**
- Mappings: Mojang official, plus Parchment mappings 2024.11.17
- Gradle mod id: `practicemod`
- Java group: `com.bilal.practicemod`
- ModDevGradle: `2.0.141`

Use the 1.21.1 NeoForge API documentation and check the code for concrete APIs. Examples from other Minecraft/NeoForge versions are unreliable.

## Getting started
1. Inspect `src/main/java`, `src/main/resources`, `src/generated/resources`, and model assets. Enumerate the registries, item classes, network packets, key bindings, tick handlers, client rendering, and any existing tests.
2. Check `git status` before edits. Preserve user work.
3. Run `./gradlew build` on Java 21. Note any dependency-download or toolchain blockers.
4. Launch the developer client with `./gradlew runClient` for behavior testing; use `./gradlew runServer` when server-side functionality matters.
5. Build after changes and report what was actually tested, including multiplayer if movement/state is involved.

## Design context from earlier planning
These are **ideas and intended behaviors**, not an audited feature list:
- **Jetpack / SCAVPACK MK.1**: improvised industrial or garage-built silhouette with twin thrusters. Prior UI explorations used bottom-center boost/heat indicators, red overheat feedback, and a cyan-ring hover motif. Exact controls, fuel/heat mechanics, numerical thresholds, and finished assets need confirmation in code and with the user.
- **Rocket Boots**: short, satisfying vertical movement boost. A roughly 3 to 4 second upward boost was discussed as a design idea, not a guaranteed specification.
- **Glider**: octagonal canopy concept.
- **Skateboard**: rideable gadget. Shift+W for extra speed was floated as a control idea. Validate the current implementation and accessibility before fixing inputs.
- **Submarine**: exploratory vehicle concept. Inspect whether any implementation exists.
- **The Walrus**: a compact rideable tunneler with a drill at the front. Early concept includes carving a practical tunnel, stopping safely on unbreakable blocks, vehicle exit/entry, and multiplayer considerations. One reference proposes ~2 blocks high, ~2 blocks wide, ~3 blocks long, with 2x2 or 3x2 tunneling. This remains concept art until validated.

## Art and workflow
- Blockbench and its MCP integration were considered for modeling. Inspect for actual `.bbmodel` assets and current MCP availability; do not assume the integration is installed or connected.
- `build.gradle` excludes `**/*.bbmodel` from built resources. Keep editable models tracked as project source assets if present and export runtime models/textures to supported resource locations.
- Art direction favors readable shapes, playful mechanical improvisation, and Minecraft-compatible pixel textures.

## Priorities for a new Claude session
1. **Audit** repository contents and produce an accurate implemented/in-progress/not-started feature inventory.
2. **Verify** a clean build and a client launch when environment permits.
3. **Document** the exact input controls, movement model, HUD behavior, and multiplayer synchronization for whatever items really exist.
4. **Identify** the most important broken or unfinished movement behavior and propose a small next implementation milestone before large refactors.
5. **Ask** for a priority only after the repository audit, when two or more plausible next tasks remain.

## Engineering principles
- Distinguish intentions, source-code implementation, and verified in-game behavior.
- Do not invent existing mechanics, completion state, test results, or API names.
- Favor small, build-verified patches and retain model/texture source files.
- Account for client/server state authority and networking when changing physics, vehicles, or flight.
- Avoid making sweeping changes to assets, controls, or balance without first checking existing behavior.
- Summarize changed files, how to reproduce/test, and unresolved issues at the end of each task.

## Handoff status
This file was prepared from verified root Gradle configuration and prior design discussions. A full source-tree audit and runtime build were **not completed** during handoff. The receiving agent should perform those steps before concluding which gadgets are implemented.
