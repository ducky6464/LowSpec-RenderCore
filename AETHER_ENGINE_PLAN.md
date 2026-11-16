# Aether Engine: Engineering Plan

## Target and constraints
Minecraft 1.21.11, Fabric, Java 21; target device is Lenovo Yoga 11e Chromebook with Intel Celeron N3150, Intel HD Graphics (Braswell), 3 GB RAM, and 16 GB storage. No Vulkan, no greedy meshing, no particle work, no texture-resolution reductions. Keep vanilla fallback for unsupported visuals and modded renderers. Performance claims require repeatable target-device measurements.

## Implemented foundation
- Backend-neutral Aether lifecycle, capability detection, render-thread ownership checks, and vanilla fallback.
- Entity extraction/enqueue profiling, RenderDispatcher CPU timing, and bounded asynchronous GPU timer sampling. GPU samples cover only the RenderDispatcher call scope.
- Adaptive Entity Visibility with conservative distance scaling under approximate entity pressure; not yet a priority-based entity budget.
- Bounded player render-state capture containing identity, skin, transforms, animation inputs, lighting, visibility flags, skin-layer visibility, cape visibility, and gliding pose. No live player entities are retained.
- Fixed-capacity stable grouping of player snapshots by skin metadata.
- Conservative eligibility classifier that leaves spectators, invisible players, unsupported poses/effects, outlines, and extra geometry on vanilla.
- New bounded CPU-side player batch planner: eligible player indices are packed into stable per-skin batches, groups with no eligible players are omitted, and fallback players are counted separately. Fixed arrays are reused without per-frame collection allocation; count/capacity mismatches are rejected.
- Unit tests cover grouping, routing, batch order, fallback exclusion, empty groups, reuse, and invalid counts.

## Still required for actual custom rendering
1. Extract reusable base-player geometry using exact 1.21.11 model APIs.
2. Convert snapshots and batch-plan entries into vertex/instance data.
3. Implement compatible RenderPipeline and RenderPass submission.
4. Connect draw completion to the GPU buffer ring's fence lifecycle.
5. Preserve unsupported layers and visual states on vanilla.
6. Build, startup-test, join-world test, and benchmark on the target Chromebook.

The batch planner is preparatory CPU data only. It does not generate model vertices, submit a RenderPipeline, issue GPU draws, or replace vanilla rendering. No FPS gain is claimed until measured.
