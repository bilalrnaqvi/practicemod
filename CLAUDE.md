# practicemod

A Minecraft mod project.

## Target versions

- Minecraft 1.21.1
- NeoForge 21.1.x
- Java 21
- Official Mojang mappings (not Yarn/MCP)

NeoForge APIs changed heavily between versions. Always verify APIs against the 1.21.1 NeoForge docs rather than relying on memory — code that looks right for a different version will fail to compile or behave incorrectly.

## Build & test

- Build: `./gradlew build`
- Test in-game: `./gradlew runClient`
- Always run the build to verify before declaring a task done.

## Art assets

Textures come from an external pipeline (Retro Diffusion for generation, Blockbench for
editing/export), not from code generation. Do not generate, edit, or overwrite any file
under `src/main/resources/assets/practicemod/textures/` unless explicitly asked to. See
`art/ART_PIPELINE.md` for texture slots, dimensions, destination paths, and how to preview
new art in-game.
