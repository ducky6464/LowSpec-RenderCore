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
   - Backend-neutral interfaces for render capabilities, entity batches, reusable geometry, per-instance data, and resource lifetime. The first API skeleton and vanilla fallback now exist under `dev.lowspec.rendercore.aether`.
   - Explicit render-thread ownership for GPU resources.
   - Use Minecraft 1.21.11's Blaze3D / RenderPipeline / GpuBuffer / RenderPass path for custom drawing. Keep raw OpenGL calls limited to capability inspection; do not build a parallel legacy GL submission path.
   - No direct GL calls from worker threads.

2. **Capability Detection**
   - Detect actual OpenGL version/extensions at runtime. The current detector reads the context's major/minor version and checks instancing/SSBO feature flags.
   - Tier A: vanilla-compatible fallback.
   - Tier B: instanced attributes where supported.
   - Tier C: advanced buffer/shader features only when explicitly supported.
   - Never assume SSBO support on the Chromebook's Linux graphics stack.

3. **Entity Extraction and Profiling**
   - Measure entity render-state extraction separately from entity render-command enqueue time. Enqueue time is not GPU draw time and must never be reported as such.
   - The profiler is wired into `EntityRenderManager#getAndUpdateRenderState` and `EntityRenderManager#render`; it records player and total counts plus rolling per-frame CPU timings when general diagnostics are enabled.
   - Counters are thread-safe and per-invocation start timestamps are thread-local. These metrics remain CPU-side and do not measure actual GPU execution.
   - Added a RenderDispatcher CPU dispatch timer and a non-blocking GPU timer-query sampler. GPU queries are only started when Minecraft's shared GlTimer is idle; results are polled later and never synchronously waited on. If another system owns the timer or results are pending, Aether skips sampling. The GPU query measures only the RenderDispatcher call scope, not guaranteed total entity GPU cost because later render passes may execute deferred work.
   - Record player and total entity extraction/enqueue counts, average and p95 CPU time, dispatch CPU time, and GPU-query sample availability. Unit tests cover separate extraction/enqueue/dispatch accounting and the disabled-profiler path. A custom-render fallback counter is deferred until a custom renderer actually exists.
   - Use fixed-size/low-allocation metrics.

4. **Adaptive Entity Visibility (AEV)**
   - The first controller uses entity-candidate volume and whole-frame time as rough pressure signals, then scales configured render distances with hysteresis.
   - It can reduce the visibility distance of remote players and non-player entities; it preserves the local player and camera entity.
   - This is not yet a strict visible-entity budget or a distance-prioritized scheduler. Candidate count is approximate, and whole-frame time can include unrelated chunk, GPU, and GUI costs.
   - Unit tests now cover distance scaling bounds, minimum-distance preservation, disabled/zero distances, and out-of-range scale clamping.
- Next: prioritize nearby players and camera-relevant entities, add stable minimum hold times, and validate visual behavior in crowded scenes.
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
- Add entity extraction/render-command timing and entity counts. (Initial implementation added; current revision is undergoing CI validation.)
- Capture baseline in an empty area, normal world, 5-10 player scene, and crowded lobby. (Not yet captured on the target Chromebook.)
- Record average FPS, 1% lows, frame-time p95, CPU render time, and GPU/driver capability where measurable.
- Keep the existing renderer unchanged.

### Phase 1: AEV
- Initial adaptive distance-scaling controller added. It is not yet the full priority-based entity budget described above.
- Verify that nearby players and the camera target remain visible.
- Benchmark visual impact and frame-time changes on the target Chromebook.

### Phase 2: Aether API foundation
- Backend interfaces, capability detection, render-thread assertions, frame lifecycle, and a vanilla backend added.
- A real custom GPU entity pipeline is not implemented yet.

### Phase 3: Player batch prototype
- In progress: a bounded per-frame player render-state snapshot queue now captures player identity, skin, transforms, animation inputs, lighting, visibility flags, skin-layer visibility, cape visibility, and flight pose values.
- A mixin captures only PlayerEntityRenderState instances at the LivingEntityRenderer extraction entry point.
- The snapshot queue is render-thread confined and does not retain live player entities.
- Player snapshots now use a reusable fixed-capacity generic grouping index to group equal `SkinTextures` metadata. It preserves stable player order within each group, handles empty/null keys, validates bounds, and reuses preallocated arrays without per-frame collection allocation. Unit tests cover grouping, ordering, clearing/rebuilding against a different key array, null keys, all-equal/all-unique keys, capacity boundaries, and invalid bounds.
- Added a conservative player eligibility classifier for the future base-model path. Spectators, invisible players, special poses/effects, outlines, and extra geometry stay on vanilla. Diagnostics count ordinary base-model candidates separately from vanilla fallbacks. This is routing groundwork only; vanilla remains the active renderer.
- The custom player geometry builder, texture-compatible GPU batch submission, RenderPipeline submission, and GPU draw pass are still not implemented.
- Retain vanilla fallback for every unsupported visual.
- Validate on 1.21.11 and the Chromebook's actual OpenGL stack before expanding.

### Phase 4: Parallel preparation and shared data
- Not started. Begin only after profiling identifies preparation as a bottleneck.
- Ensure thread safety and compare worker counts on the 2-core/4-thread CPU.

### Phase 5: Expansion and hardening
- Not started. Includes armor, capes, held items, name tags, other entity classes, mod compatibility, resource reloads, world changes, and disconnect/shutdown cleanup.
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
- 3x is an aspiration, not a promise.

## Key technical references

- Polarium source and README: https://github.com/brksfrb/polarium
- Rentities: https://modrinth.com/mod/rentities
- Yarn 1.21.11 EntityRenderState: https://maven.fabricmc.net/docs/yarn-1.21.11+build.6/net/minecraft/client/render/entity/state/EntityRenderState.html
- Yarn 1.21.11 EntityRenderManager: https://maven.fabricmc.net/docs/yarn-1.21.11+build.6/net/minecraft/client/render/entity/EntityRenderManager.html
- Fabric 1.21.11 rendering concepts: https://github.com/FabricMC/fabric-docs/blob/main/versions/1.21.11/develop/rendering/basic-concepts.md
- Fabric 1.21.11 world extraction/drawing and custom pipeline example: https://github.com/FabricMC/fabric-docs/blob/main/versions/1.21.11/develop/rendering/world.md

## Current implementation status (2026-11-16)

- Aether API/lifecycle foundation and vanilla fallback exist.
- AEV currently scales configured visibility distances under rough entity/frame pressure; it is not a full priority-based budget.
- Entity extraction/enqueue profiling, RenderDispatcher CPU dispatch timing, and asynchronous GPU timer queries are implemented. The GPU query is explicitly scoped to the RenderDispatcher call and is not a complete measurement of all deferred entity draw work.
- Capability detection now queries the active context's actual OpenGL major/minor version.
- Commit `1de3e751e63494e4c9fa9c9700b2da003c313c89` passed the Gradle build and headless Minecraft startup/settings-screen smoke test.
- The settings-screen smoke test was failing because xdotool could not match the GLFW window title even though Minecraft logged the screen-ready marker. The workflow now captures the Xvfb root window directly and validates the rendered text.
- A bounded player render-state queue and LivingEntityRenderer capture mixin have been added on branch `aether-engine-prototype`.
- The queue now retains the player skin, player ID, base transforms/animation inputs, lighting/outline, visibility state, skin overlay visibility, cape visibility, and gliding pose values. It does not retain the live player object.
- GitHub Actions for the player-state capture revision passed: Gradle build and client startup/settings-screen smoke test. This validates compilation and initialization, not in-world GPU rendering.
- The queue captures player snapshots, builds stable CPU-side skin groups using the tested `AetherGroupIndex`, and reports conservative custom-base candidates versus vanilla fallback cases. It does not generate player mesh vertices, submit a custom RenderPipeline, or issue a custom GPU draw. Vanilla remains the only active renderer.
- The `/rendercore aether upload-test` command now schedules a single 72-byte identity-record upload on the render thread. This tests staging-to-GPU-buffer mapping and ring/fence bookkeeping without drawing anything.
- `/rendercore stats` reports upload self-test results, ring errors, retained memory, staging capacity/overflow, and profiler sample counters.
- `EntityRenderMetricsTest` verifies extraction, enqueue, dispatch, p95 aggregation, and that disabled profiling returns no start timestamp.
- Next: implement and test minimal base-player geometry extraction and a custom draw pass using the 1.21.11 RenderPipeline/RenderPass APIs, while leaving unsupported layers on vanilla. The new skin groups are only an input to that later stage. Do not claim performance gains until measured on the target Chromebook.


### Player batch-plan follow-up
- Added AetherPlayerBatchPlan, a fixed-capacity CPU-side planner that packs eligible player snapshot indices into stable batches keyed by the existing skin-group index.
- Groups with no eligible players are omitted. Unsupported player states are counted as vanilla fallbacks and are never placed in custom batches.
- The player snapshot queue now builds and exposes these batches after skin grouping. The planner uses preallocated arrays and rejects mismatched counts/capacity.
- Unit tests cover stable ordering, fallback exclusion, omitted empty groups, rebuild/reset behavior, and invalid inputs.
- This is still preparation data only. It does not generate model vertices, submit a RenderPipeline, issue GPU draws, or replace vanilla rendering.


### Reusable base-model geometry groundwork
- Added an allocation-free cuboid triangle-list writer for model-space geometry. It emits 36 vertices per cuboid with position, per-face UVs, and outward-facing normals into caller-owned arrays.
- The writer validates finite bounds, positive dimensions, UV input, and destination capacity. It does not retain arrays or allocate per cuboid.
- Unit tests cover vertex count, face normals, output offsets/bounds, and invalid input.
- This is a generic geometry primitive for the upcoming player-model extraction stage. It is not yet wired to Minecraft's PlayerEntityModel/ModelPart and is not a complete player mesh or renderer.


### Live ModelPart geometry extraction
- Added AetherModelGeometryBuffer, which captures the current posed cuboids from Minecraft's ModelPart.forEachCuboid traversal.
- Each Minecraft face quad is expanded to two triangles with transformed model-space positions, original UV coordinates, and transformed normals.
- Uses a caller-owned MatrixStack and reusable fixed-capacity float storage; overflow is bounded and reported instead of writing beyond the buffer.
- Tests cover cuboid/quad/vertex counts, finite output, matrix translation, capacity overflow, and invalid capacity.
- This is now a real CPU-side ModelPart geometry extraction primitive, but it is not yet connected to PlayerEntityRenderer or the GPU draw pipeline. The caller must pass the base player model root and preserve vanilla feature layers.


### Multi-player CPU geometry arena
- Added a fixed-capacity contiguous arena that stores multiple players' extracted base-model meshes in one reusable float array.
- Each player slot records a stable offset, float count, and vertex count for the current frame; gaps are allowed and duplicate/out-of-range slots are rejected.
- Reuses one bounded ModelPart extractor as scratch storage, then copies successful meshes into the arena. Per-frame reset clears slot metadata without reallocating arrays.
- Tests cover multi-player packing, per-player offsets, duplicate/out-of-range rejection, total-capacity overflow, and frame reuse.
- This creates CPU mesh storage for later per-skin GPU upload. It is not yet wired to live PlayerEntityRenderer model instances or a GPU draw pass.


### Live player model capture integration
- Connected the player-render mixin to the actual LivingEntityRenderer model after its render method has prepared and queued the current pose.
- The mixin checks that the most recent snapshot belongs to the same player and that the conservative eligibility classifier permits the ordinary base-model route.
- Eligible player model roots are traversed into a reusable, bounded per-frame geometry arena. The geometry remains in model-local coordinates; the existing player snapshot supplies identity, transforms, skin, and lighting metadata.
- Geometry capture is render-thread-only, uses a reusable MatrixStack and fixed arrays, and does not cancel or replace Minecraft's vanilla render commands. Unsupported states remain on vanilla.
- Added /rendercore stats reporting for captured geometry, arena capacity, and overflow/rejection counters.
- Corrected two geometry-arena test capacities that were smaller than the two test meshes / even smaller than one mesh. The tests now exercise the intended successful packing and total-capacity overflow cases.
- This closes the live CPU model-capture gap. It does not yet submit a custom GPU draw. A GPU vertex upload path, shader/pipeline compatibility, draw ordering, and safe vanilla/custom handoff remain required before visual testing of a custom renderer.

- The live geometry hook is restricted to Minecraft's PlayerEntityModel class. Unknown or modded player model implementations are not captured and remain entirely on vanilla rendering.


### GPU-resident player geometry upload
- Added AetherPlayerGeometryBufferPool, a three-slot fenced GPU vertex-buffer ring sized to the bounded CPU geometry arena.
- At frame end, captured player geometry is uploaded as contiguous 8-float vertices (position, UV, normal) using the device command encoder's mapped buffer path.
- Slot reuse is non-blocking: the pool polls fences with a zero timeout, skips when all slots are busy, and quarantines slots when mapping/fence state is uncertain. It also exposes a draw-completion fence hook so a future render pass can protect a slot until its draw completes.
- The engine exposes the latest uploaded geometry buffer for the upcoming render-pass integration, closes the pool during render-thread shutdown, and reports upload/fence counters in /rendercore stats.
- This moves captured base-player mesh data from CPU memory into GPU vertex buffers. It still does not bind a RenderPipeline, create a RenderPass, issue draw calls, or alter vanilla rendering. The uploaded geometry is currently a preparatory GPU resource, not a visible custom renderer.


### Skin-grouped GPU geometry draw planning
- Added AetherPlayerGeometryDrawPlan, a fixed-capacity per-frame planner that turns eligible player batches plus captured arena metadata into explicit GPU vertex ranges.
- Each packet records the source player snapshot index, first vertex, and vertex count; packets are grouped by the existing skin-group index while preserving stable player order.
- Players with no captured mesh are omitted and counted. Misaligned offsets, inconsistent float/vertex counts, and capacity violations are rejected rather than passed to a future draw call.
- The engine builds the plan after player capture and skin grouping. /rendercore stats reports packet count, skin batches, missing meshes, and invalid ranges.
- Unit tests cover stable skin grouping, vertex offsets/counts, missing meshes, malformed ranges, and frame reset.
- This is now a complete CPU-side description of which player vertex ranges a future skin-bound draw pass must submit. It still does not bind textures, create a RenderPass, issue GPU draw calls, or suppress vanilla rendering.


### Compact GPU player vertex format
- Added a declared 24-byte vertex layout matching Minecraft's native VertexFormat API: float3 position, float2 UV, normalized-byte3 normal, and one padding byte.
- Added a reusable allocation-free writer that converts the extractor's 8-float CPU vertices into the packed layout directly inside the mapped GPU buffer.
- The GPU geometry ring now uploads 24 bytes per vertex instead of the original 32-byte float layout, reducing vertex transfer size by 25% while retaining native float UV precision and normalized-byte normals.
- The declared VertexFormat verifies its stride and attribute offsets against the writer's byte layout. Tests cover exact encoded values, clamping, multiple vertices, invalid/non-finite input, insufficient output capacity, and format offsets.
- This defines the vertex contract needed by the future custom shader/pipeline. Shader compilation, texture binding, RenderPass submission, and actual draw calls remain unfinished.

### Player shader, texture binding, and draw submission foundation
- Added the Aether base-player vertex and fragment shader resources and a dedicated RenderPipeline definition using the compact native-compatible player vertex format.
- Registered the pipeline in Minecraft's pipeline registry through a narrowly scoped Mixin invoker during client initialization. Registration failure is caught and leaves vanilla rendering active.
- Added AetherPlayerTextureBinder to resolve the captured skin's body texture through Minecraft's texture manager and bind its GPU view/sampler to Sampler0.
- Added AetherPlayerDrawSubmitter to submit validated per-player vertex ranges for a selected skin group into a caller-owned RenderPass, using the registered pipeline and caller-provided dynamic transforms.
- These are concrete shader, texture-binding, and draw-submission components. They are not yet connected to a live world-render pass, and they do not suppress vanilla player drawing. World-stage integration, safe per-player handoff, and in-world visual validation remain outstanding.


### Live world draw bridge and transformed mesh capture
- Moved posed base-player geometry capture to the base-model queue submission point inside LivingEntityRenderer.render, so the supplied renderer matrix stack still contains the current player transform. Feature-layer submissions remain outside this capture path.
- Added a WorldRenderEvents.AFTER_ENTITIES drawing-stage bridge. It prepares the frame's skin groups, and only uploads captured geometry when the explicit mirror test is enabled. The pass targets Minecraft's main framebuffer color/depth attachments, binds default projection uniforms and per-frame model-view transforms, resolves each skin texture, and submits the planned vertex ranges.
- Added /rendercore aether mirror on|off as an explicit visual test switch. It is OFF by default and deliberately leaves vanilla player drawing enabled; it redraws captured base models only to validate the custom shader/texture/depth path. It is not the final replacement renderer and should not be treated as an FPS mode.
- Added mirror-pass status and success/skip/failure counters to /rendercore stats.
- Production vanilla/custom handoff, full visual compatibility, shader-reload validation, and target-Chromebook performance testing remain outstanding.
