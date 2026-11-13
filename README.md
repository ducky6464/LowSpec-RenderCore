# LowSpec RenderCore

A standalone, client-side Fabric optimization project for Minecraft 1.21.11. It is separate from MC-Tiers and does not require Sodium or OptiFine.

## Current status

**Early development. Not a finished optimizer or a replacement renderer.**

Implemented:
- Fabric Loom project targeting Minecraft 1.21.11 and Java 21.
- Persistent, player-owned configuration.
- Basic average client tick-time measurement.
- Mixin instrumentation around vanilla `LightingProvider.doLightUpdates()`, recording call count, total time, and vanilla-reported update count.
- GitHub Actions workflow intended to compile the mod and publish the JAR as a workflow artifact.

The lighting instrumentation only measures vanilla work. It does not alter light propagation, skip updates, or claim a lighting speedup. Metrics are exposed in code for the upcoming diagnostics display.

Not implemented yet:
- Lighting scheduling or propagation optimization.
- Chunk mesh/rebuild optimization.
- Entity render-distance controls wired into rendering.
- In-game configuration screen or HUD.
- Verified successful build and in-game compatibility testing.

## Build

Requires Java 21 and Gradle 9.2.1. Run `gradle build`, or use the GitHub Actions workflow. A successful workflow uploads the JAR as an artifact.

## Engineering rules

- No automatic changes to player settings.
- Render-distance controls must affect drawing only, never server simulation or entity existence.
- Lighting optimizations must preserve vanilla light propagation correctness. Required updates are never discarded.
- Performance claims require repeatable before/after benchmarks on named hardware.

## Roadmap

1. **Foundation and instrumentation:** project setup, config model, client tick timing, lighting timing.
2. **Lighting optimization:** use measurements to identify bottlenecks, then test safe scheduling improvements against correctness tests.
3. **Chunk renderer:** investigate rebuild scheduling, mesh generation, face/occlusion culling, and draw-call costs.
4. **Entity distance:** add independent, manually controlled distances by entity category.
5. **UI and benchmarks:** settings screen, diagnostics overlay, frame-time percentiles, and reproducible tests.
