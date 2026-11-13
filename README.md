# LowSpec RenderCore

A standalone, client-side Fabric mod project for Minecraft 1.21.11. It is separate from MC-Tiers and does not depend on Sodium or OptiFine.

## Development status

**Early foundation, not a finished optimizer yet.** The repository now contains the Fabric/Loom project, Java 21 metadata, persistent player-owned configuration, basic client tick-time diagnostics, and a GitHub Actions build workflow.

The current render-distance values are configuration groundwork only; they are not yet wired into entity rendering. Lighting diagnostics and optimizations, chunk rendering changes, an in-game settings screen, and runtime benchmarks remain unfinished.

## Build

Run `gradle build` with Java 21 and Gradle 9.2.1 or use the GitHub Actions workflow. A successful workflow uploads the JAR as an artifact.

## Engineering rules

- No automatic changes to player settings.
- Render-distance controls must affect drawing only, never server simulation or entity existence.
- Lighting optimizations must preserve vanilla light propagation correctness; never drop required updates.
- Performance claims require repeatable before/after benchmarks on named hardware.

## Planned milestones

1. **Foundation:** project metadata, config model, tick diagnostics, CI build.
2. **Lighting:** instrument vanilla lighting update cost, then test safe scheduling optimizations.
3. **Chunk renderer:** investigate rebuild scheduling, mesh generation, face/occlusion culling, and draw-call costs.
4. **Entity distance:** add independent, manually controlled distances by entity category.
5. **UI and benchmarks:** in-game settings screen, frame-time percentiles, and reproducible tests.
