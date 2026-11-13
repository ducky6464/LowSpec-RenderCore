# LowSpec RenderCore

A standalone, client-side Fabric optimization project for Minecraft 1.21.11. It is separate from MC-Tiers and does not require Sodium or OptiFine.

## Current status

**Early development. Not a finished optimizer or a replacement renderer.**

Implemented:
- Fabric Loom project targeting Minecraft 1.21.11 and Java 21.
- Persistent, player-owned configuration.
- Basic average client tick-time measurement.
- Mixin instrumentation around vanilla `LightingProvider.doLightUpdates()`, recording call count, total time, and vanilla-reported update count.
- Optional client-side entity render-distance culling for players, hostile mobs, passive mobs, villagers, dropped items, projectiles, experience orbs, and armor stands.
- Independent distance values for each supported entity category.
- GitHub Actions workflow that successfully compiled the initial project and uploaded its JAR artifact. Newer changes are still being validated by CI.

Entity culling is **off by default**. To enable it, edit `.minecraft/config/lowspec-rendercore.properties` while the game is closed:

```properties
render.entity_distance_culling=true
render.players=128
render.hostile_mobs=96
render.passive_mobs=96
render.villagers=64
render.dropped_items=48
render.projectiles=48
render.experience_orbs=32
render.armor_stands=64
```

The config file is generated automatically on first launch. Distances are in blocks. This only prevents selected entities from being drawn beyond the chosen distance. It does not remove entities, change server simulation, or stop entity AI. The local player and active camera entity are preserved. Unknown entity categories retain vanilla rendering behavior.

The lighting instrumentation only measures vanilla work. It does not alter light propagation, skip updates, or claim a lighting speedup. Metrics are exposed in code for the upcoming diagnostics display.

Not implemented yet:
- Actual lighting scheduling or propagation optimization.
- Chunk mesh/rebuild optimization.
- In-game configuration screen or HUD.
- Frame-time percentiles and GPU metrics.
- In-game compatibility and performance testing across hardware.

## Build

Requires Java 21. Run `gradle build`, or use the GitHub Actions workflow. A successful workflow uploads the JAR as an artifact.

## Engineering rules

- No automatic changes to player settings.
- Render-distance controls must affect drawing only, never server simulation or entity existence.
- Lighting optimizations must preserve vanilla light propagation correctness. Required updates are never discarded.
- Performance claims require repeatable before/after benchmarks on named hardware.

## Roadmap

1. **Foundation and instrumentation:** project setup, config model, client tick timing, lighting timing.
2. **Lighting optimization:** use measurements to identify bottlenecks, then test safe scheduling improvements against correctness tests.
3. **Chunk renderer:** investigate rebuild scheduling, mesh generation, face/occlusion culling, and draw-call costs.
4. **Entity distance:** expand categories and validate behavior in-game.
5. **UI and benchmarks:** settings screen, diagnostics overlay, frame-time percentiles, and reproducible tests.
