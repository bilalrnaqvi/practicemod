# Practice Mod

Minecraft traversal equipment and vehicles: jetpack, umbrella glider, rocket boots,
Compact Racer jetski, CRAG ATV, Mechanical Dolphin, skateboard and Deepseeker submarine.

## Development

- Java 21; Minecraft 1.21.1; NeoForge 21.1.235.
- Mojang mappings with Parchment 2024.11.17 for 1.21.1.
- `./gradlew verifyProject` builds the mod, runs the dedicated-server GameTests and validates PAL playback.
- Add `--offline` for a cached local setup. A fresh checkout needs network access to resolve dependencies.
- `./gradlew runClient` launches the development game.
- `./gradlew runData` regenerates item/data resources when their providers change.
- Packaged mod: `build/libs/practicemod-1.0.0.jar`.

`verifyProject` is also the CI command. It does not require Blender, Python or a graphical
session. It does not certify visual quality, multiplayer latency, or animation clearance.
For source-art changes, Python 3 with Pillow and Blender 4.5 are used by the relevant
export/review tools; see [source map](docs/SOURCE_MAP.md) and [boarding workflow](docs/animation/BOARDING.md).
Some older standalone preview tools contain machine-specific Java paths; they are not part of portable CI.

## Project guide

- [Working instructions](AGENTS.md)
- [Art direction](ART_DIRECTION.md) and [animation direction](ANIMATION_DIRECTION.md)
- [Canonical sources and exporters](docs/SOURCE_MAP.md)
- [White Horizon yacht design and implementation plan](docs/yacht/WHITE_HORIZON.md) (planning; moving-floor prototype first)
- Vehicle controls and details: [Deepseeker](DEEPSEEKER.md), [CRAG](ATV.md),
  [jetski](JETSKI.md), [Mechanical Dolphin](MECHANICAL_DOLPHIN.md), [skateboard](SKATEBOARD.md).

## Files and checkpoints

Track editable sources, generated runtime assets, validation tools and curated references.
`build/`, `run/`, logs, Python caches and Blender recovery files are local outputs.
Under `output/`, Markdown handoffs and the Blender renderer reference JSON are retained;
generated images, movies and review intermediates are ignored. Existing local files are
not deleted. Historical output folders can contain unique experiments: archive deliberately,
never run a blanket cleanup merely because Git ignores them.

The current boarding builder's required baseline is tracked in `src/main/blender/baselines/`.
Current reference images live in `docs/art-references/`. Old revisions and their claims are
preserved in `docs/history/`; they are not current rebuild instructions.

When finishing a milestone, review the diff and save a focused local commit. Record visual
review status separately from automated checks. Avoid combining unrelated concurrent tasks.
