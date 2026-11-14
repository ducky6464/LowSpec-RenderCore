# Aether Engine: Engineering Plan

## Project target

Aether is a specialized rendering engine/API inside the existing LowSpec RenderCore Fabric client mod. The first target is Minecraft 1.21.11, Java 21, on the Lenovo Yoga 11e Chromebook:

- Intel Celeron N3150: 2 physical cores / 4 threads
- Intel HD Graphics (Braswell)
- 3 GB RAM
- 16 GB storage

Hard constraints:
- No Vulkan backend.
- No greedy meshing.
- No particle optimization work.
- Do not lower texture resolution or silently remove important player visuals.
- Preserve vanilla rendering as a fallback for unsupported entity layers, shader packs, and modded renderers.
- All performance claims require repeatable measurements on the target Chromebook.

## Research findings that shape the design

### Existing work to study, not copy

- Polarium (https://github.com/brksfrb/polarium) combines player GPU instancing, shared skin atlas work, parallel render-state extraction, parallel player ticking, and reuse of player render state. Its published builds target Minecraft 26.x, not 1.21.11. It is GPL-3.0-or-later, so this project must not copy its implementation.
- Rentities (https://modrinth.com/mod/rentities) demonstrates a 1.21.11 entity-instancing approach using GPU-side animation and SSBOs, but its published requirements include an NVIDIA GTX 10-series-or-newer GPU. That makes it unsuitable as a drop-in solution for this Intel Chromebook. It is useful as evidence and a design reference, not as code to transplant.
- Minecraft 1.21.11 already uses EntityRenderState and a newer extraction/drawing split. Aether should integrate with those phases rather than assume the older single-phase renderer.
- Fabric's 1.21.11 rendering documentation warns that the modern pipeline has changed substantially since 1.21.6. Custom rendering must respect the active RenderPipeline/RenderState architecture and Minecraft's render-thread rules.

## Architecture

Aether is not a replacement GPU driver or a new hardware-level API like Vulkan. It is a Minecraft-specialized rendering API over Minecraft's existing graphics context, initially using OpenGL. Its public API should hide backend details and expose safe operations for batching, resource lifetime, visibility policy, and capability selection.

Proposed modules:

1. **Aether API**
   - Backend-neutral interfaces for render capabilities, entity batches, reusable geometry, per-instance data, and resource lifetime.
   - Explicit render-thread ownership for GPU resources.
   - No direct GL calls from worker threads.

2. **Capability Detection**
   - Detect actual OpenGL version/extensions at runtime.
   - Tier A: vanilla-compatible fallback.
   - Tier B: instanced attributes where supported.
   - Tier C: advanced buffer/shader features only when explicitly supported.
   - Never assume SSBO support on the Chromebook's Linux graphics stack.

3. **Entity Extraction and Profiling**
   - Measure entity render-state extraction separately from entity render-command submission.
   - Record player and non-player counts, average and p95 CPU time, and fallback counts.
   - Use fixed-size/low-allocation metrics.
   - Establish baseline before changing rendering behavior.

4. **Adaptive Entity Visibility (AEV)**
   - Enforce a configurable visible-entity budget only when measured CPU/GPU pressure warrants it.
   - Prioritize camera target, nearby players, and entities close to the camera.
   - Hide low-priority distant entities client-side only.
   - Use hysteresis and minimum hold times to prevent flicker.
   - Preserve the local player and camera entity.
   - Never change server state or entity simulation.

5. **Parallel Preparation**
   - Move only immutable or safely snapshotted preparation data to workers.
   - Begin conservatively: one helper worker by default on this 2-core/4-thread device; test a second worker only if profiling proves it helps rather than competing with Minecraft's main/render work.
   - Keep all GPU resource creation, upload, and submission on the render thread unless Minecraft explicitly supports another route.
   - Preserve ordering for sounds, particles, world mutation, and other side effects. Particle optimization itself is out of scope.

6. **GPU Entity Pipeline**
   - First prototype: player body/model geometry batching with a strict vanilla fallback.
   - Reuse compatible model geometry and per-player instance data.
   - Consider GPU animation only after measuring shader and buffer support on the target device.
   - Keep skin, armor, cape, held-item, name-tag, and modded-layer compatibility explicit. Unsupported layers fall back to vanilla rather than disappearing.
   - Batch only compatible materials/render states; do not force unlike translucent or ordered layers into unsafe batches.

7. **Memory Manager**
   - Bounded pools with explicit ownership, size limits, device/context identity, and shutdown cleanup.
   - Avoid unbounded skin/model atlas growth.
   - Track allocations, reuse hits, evictions, and upload bytes.

8. **Adaptive Scheduler**
   - Select vanilla, batched, or hybrid paths based on measured capability and workload.
   - Avoid over-parallelizing a two-core CPU.
   - Use stable thresholds and hysteresis so modes do not oscillate every frame.

## Implementation sequence

### Phase 0: Establish truth
- Add entity extraction/render-command timing and entity counts.
- Capture baseline in an empty area, normal world, 5-10 player scene, and crowded lobby.
- Record average FPS, 1% lows, frame-time p95, CPU render time, and GPU/driver capability where measurable.
- Keep the existing renderer unchanged.

### Phase 1: AEV
- Implement a safe client-only entity budget with priorities, hysteresis, and user settings.
- Verify that nearby players and the camera target remain visible.
- Benchmark visual impact and frame-time changes.

### Phase 2: Aether API foundation
- Add backend interfaces, capability detection, render-thread assertions, and bounded resource abstractions.
- Add a vanilla backend first; it must preserve behavior.

### Phase 3: Player batch prototype
- Prototype one narrowly scoped player-rendering path.
- Start with simple compatible body geometry; retain vanilla fallback for every unsupported visual.
- Validate on 1.21.11 and the Chromebook's actual OpenGL stack before expanding.

### Phase 4: Parallel preparation and shared data
- Add only after profiling identifies preparation as a bottleneck.
- Ensure thread safety and compare worker counts on the 2-core/4-thread CPU.

### Phase 5: Expansion and hardening
- Armor, capes, held items, name tags, other entity classes, mod compatibility, resource reloads, world changes, and disconnect/shutdown cleanup.
- Run startup, join-world, resource reload, crowded-scene, and long-session tests.

## Acceptance criteria

- Builds against Minecraft 1.21.11 and Java 21.
- Does not crash when joining a world or server.
- No Vulkan dependency.
- No particle work and no greedy meshing.
- No texture-resolution reduction.
- Vanilla fallback works when advanced GL features are missing.
- AEV never changes server state.
- Visual compatibility is tested rather than assumed.
- Performance reports compare the same scene, view, settings, and hardware.
- 3x is an aspiration, not a promise. The project reports measured results only.

## Key technical references

- Polarium source and README: https://github.com/brksfrb/polarium
- Rentities: https://modrinth.com/mod/rentities
- Yarn 1.21.11 EntityRenderState: https://maven.fabricmc.net/docs/yarn-1.21.11+build.6/net/minecraft/client/render/entity/state/EntityRenderState.html
- Yarn 1.21.11 EntityRenderManager: https://maven.fabricmc.net/docs/yarn-1.21.11+build.6/net/minecraft/client/render/entity/EntityRenderManager.html
- Fabric rendering concepts: https://docs.fabricmc.net/develop/rendering/basic-concepts
- Fabric world extraction/drawing phases: https://docs.fabricmc.net/develop/rendering/world
