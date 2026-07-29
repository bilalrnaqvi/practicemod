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
