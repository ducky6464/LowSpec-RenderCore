# LowSpec RenderCore

A standalone, client-side Fabric optimization project for Minecraft 1.21.11. It is separate from MC-Tiers and does not require Sodium or OptiFine.

## Current status

**Early development. Not a finished optimizer or a replacement renderer.**

Implemented:
- Fabric Loom project targeting Minecraft 1.21.11 and Java 21.
- Persistent, player-owned configuration.
- Optional Mod Menu 17.0.1 integration for Minecraft 1.21.11, with a graphical settings screen. Mod Menu remains optional; the mod does not force it as a dependency.
- Basic average client tick-time measurement.
- In-game `/rendercore stats` command showing measured tick, CPU-side render duration average/p95, and lighting timings.
- Rolling 120-sample CPU-side `GameRenderer.render()` timing with a fixed histogram for low-allocation percentile reporting; explicitly not presented as GPU or display-present time.
- In-game `/rendercore culling on` and `/rendercore culling off` commands that save the setting automatically.
- Graphical settings screen accessible from the LowSpec RenderCore config button in Mod Menu, including entity distance +/- controls and diagnostics toggles.
- Mixin instrumentation around vanilla `LightingProvider.doLightUpdates()`, recording call count, total time, and vanilla-reported update count.
- Optional client-side entity render-distance culling for players, hostile mobs, passive mobs, villagers, dropped items, projectiles, experience orbs, and armor stands.
- Optional per-client-tick particle admission budget to limit sudden particle bursts. This limits newly admitted particles per tick; it does not remove existing particles or change server-side effects.
- Optional block-entity render-distance culling at render-state creation time, measured from the active camera (including spectator/free-camera views), with beacon rendering preserved.
- Optional in-game performance HUD showing client tick time, CPU-side render average/p95, lighting timing, CPU time spent scheduling visible chunk updates, live chunk task/upload queue counts, free chunk buffers, and particle admissions from the current client tick. The HUD is disabled by default and can be toggled in Mod Menu.
- Independent distance values for each supported entity category.
- GitHub Actions workflow that successfully compiled the initial project and uploaded its JAR artifact. Newer changes are still being validated by CI.

Entity culling is **on by default** in fresh installations. The mod creates `.minecraft/config/lowspec-rendercore.properties` automatically. No manual setup is needed. The defaults are:

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

The lighting and chunk instrumentation only measure vanilla work. Chunk update timing covers the CPU-side visible-chunk scheduling/update method, not mesh-generation time on worker threads or GPU upload time. These metrics do not alter lighting propagation, skip updates, or claim a speedup.

Not implemented yet:
- Actual lighting scheduling or propagation optimization.
- Chunk mesh/rebuild optimization.
- Actual GPU time, display-present frame time, and GPU metrics.
- In-game compatibility and performance testing across hardware.

## Mod Menu settings screen

Install Mod Menu 17.0.1 for Minecraft 1.21.11 to get a **Configure** button for LowSpec RenderCore in the Mods list. The screen lets you adjust all eight entity distance categories, set the per-tick particle budget (50–2,000), adjust block-entity render distance (16–256 blocks), and toggle entity culling, block-entity culling, particle limiting, general diagnostics, lighting timing, chunk diagnostics, and the optional performance HUD. Changes save automatically. Mod Menu is optional; command controls remain available without it.

## In-game commands

- `/rendercore stats` displays the available client tick and lighting diagnostics. These are measurements, not FPS or GPU readings.
- `/rendercore culling on` enables entity-distance culling and saves the setting.
- `/rendercore culling off` disables entity-distance culling and saves the setting.
- `/rendercore distance players 128` changes the selected category's render distance in blocks. Categories: `players`, `hostile`, `passive`, `villagers`, `items`, `projectiles`, `orbs`, `armorstands`. Values are clamped to safe supported ranges and saved automatically.

The particle budget defaults to 400 new particle admissions per client tick and can be changed or disabled in Mod Menu. This is a burst limiter, not a cap on total live particles. Block-entity culling defaults to 96 blocks and preserves beacon render states; other distant block-entity render states are skipped beyond the selected distance. The performance HUD is disabled by default and can be enabled in the Mod Menu configuration screen. Chunk timing and queue counters are diagnostics only; they do not change chunk scheduling.

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
