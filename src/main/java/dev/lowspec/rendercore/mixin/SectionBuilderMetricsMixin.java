package dev.lowspec.rendercore.mixin;

import com.mojang.blaze3d.systems.VertexSorter;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import dev.lowspec.rendercore.ChunkMeshMetrics;
import dev.lowspec.rendercore.ChunkRegionStateCache;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.BlockRenderLayers;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.client.render.chunk.EmptySectionFastPath;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Adds metrics and a low-allocation non-empty section meshing pipeline.
 * The pipeline keeps vanilla model/fluid rendering semantics, but replaces the
 * iterator-driven block walk with a worker-local scratch state and a single
 * mutable position reused for all 4096 cells.
 */
@Mixin(SectionBuilder.class)
public abstract class SectionBuilderMetricsMixin {
    @Shadow @Final private BlockRenderManager blockRenderManager;
    @Shadow @Final private BlockEntityRenderManager blockEntityRenderDispatcher;

    @Unique private final ThreadLocal<Long> lowspec$meshBuildStartNanos = ThreadLocal.withInitial(() -> 0L);
    @Unique private static final ThreadLocal<lowspec$MeshScratch> LOWSPEC$MESH_SCRATCH =
            ThreadLocal.withInitial(lowspec$MeshScratch::new);

    @Inject(
            method = "build(Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/client/render/chunk/ChunkRendererRegion;Lcom/mojang/blaze3d/systems/VertexSorter;Lnet/minecraft/client/render/chunk/BlockBufferAllocatorStorage;)Lnet/minecraft/client/render/chunk/SectionBuilder$RenderData;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lowspec$beginMeshBuild(ChunkSectionPos sectionPos, ChunkRendererRegion region,
                                        VertexSorter sorter, BlockBufferAllocatorStorage storage,
                                        CallbackInfoReturnable<SectionBuilder.RenderData> cir) {
        boolean diagnostics = LowSpecRenderCoreClient.CONFIG != null
                && LowSpecRenderCoreClient.CONFIG.chunkDiagnosticsEnabled;
        long start = diagnostics ? System.nanoTime() : 0L;
        lowspec$meshBuildStartNanos.set(start);

        if (LowSpecRenderCoreClient.CONFIG != null
                && LowSpecRenderCoreClient.CONFIG.chunkMeshStateCacheEnabled) {
            ChunkRegionStateCache.beginSection(region,
                    sectionPos.getSectionX(), sectionPos.getSectionY(), sectionPos.getSectionZ());
        }

        if (LowSpecRenderCoreClient.CONFIG != null
                && LowSpecRenderCoreClient.CONFIG.chunkEmptySectionFastPathEnabled) {
            SectionBuilder.RenderData empty = EmptySectionFastPath.tryBuildEmpty(sectionPos, region);
            if (empty != null) {
                if (start != 0L) ChunkMeshMetrics.record(System.nanoTime() - start);
                lowspec$meshBuildStartNanos.remove();
                ChunkRegionStateCache.clear(region);
                cir.setReturnValue(empty);
                return;
            }
        }

        // For non-empty sections, use the custom low-allocation mesh build.
        cir.setReturnValue(lowspec$buildNonEmpty(sectionPos, region, sorter, storage));
    }

    @Unique
    private SectionBuilder.RenderData lowspec$buildNonEmpty(
            ChunkSectionPos sectionPos, ChunkRendererRegion region,
            VertexSorter vertexSorter, BlockBufferAllocatorStorage allocatorStorage) {
        SectionBuilder.RenderData renderData = new SectionBuilder.RenderData();
        ChunkOcclusionDataBuilder occlusionBuilder = new ChunkOcclusionDataBuilder();
        MatrixStack matrices = new MatrixStack();
        lowspec$MeshScratch scratch = LOWSPEC$MESH_SCRATCH.get();
        Map<BlockRenderLayer, BufferBuilder> builders = scratch.builders;
        List<BlockModelPart> modelParts = scratch.modelParts;
        Random random = scratch.random;
        BlockPos.Mutable pos = scratch.pos;
        builders.clear();
        modelParts.clear();

        BlockModelRenderer.enableBrightnessCache();
        try {
            int minX = sectionPos.getMinX();
            int minY = sectionPos.getMinY();
            int minZ = sectionPos.getMinZ();

            // Same x-fastest, then y, then z traversal as BlockPos.iterate(min, max).
            // One mutable BlockPos is reused, avoiding iterator and per-cell position allocation.
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldZ = minZ + localZ;
                for (int localY = 0; localY < 16; localY++) {
                    int worldY = minY + localY;
                    for (int localX = 0; localX < 16; localX++) {
                        pos.set(minX + localX, worldY, worldZ);
                        BlockState state = region.getBlockState(pos);

                        if (state.isOpaqueFullCube()) {
                            occlusionBuilder.markClosed(pos);
                        }

                        if (state.hasBlockEntity()) {
                            BlockEntity blockEntity = region.getBlockEntity(pos);
                            if (blockEntity != null) {
                                BlockEntityRenderer<BlockEntity, ?> renderer =
                                        blockEntityRenderDispatcher.get(blockEntity);
                                if (renderer != null && !renderer.rendersOutsideBoundingBox()) {
                                    renderData.blockEntities.add(blockEntity);
                                }
                            }
                        }

                        FluidState fluidState = state.getFluidState();
                        if (!fluidState.isEmpty()) {
                            BlockRenderLayer fluidLayer = BlockRenderLayers.getFluidLayer(fluidState);
                            BufferBuilder fluidBuffer = lowspec$bufferFor(builders, allocatorStorage, fluidLayer);
                            blockRenderManager.renderFluid(pos, region, fluidBuffer, state, fluidState);
                        }

                        if (state.getRenderType() == BlockRenderType.MODEL) {
                            BlockRenderLayer blockLayer = BlockRenderLayers.getBlockLayer(state);
                            BufferBuilder blockBuffer = lowspec$bufferFor(builders, allocatorStorage, blockLayer);
                            long renderingSeed = state.getRenderingSeed(pos);
                            if (LowSpecRenderCoreClient.CONFIG != null
                                    && LowSpecRenderCoreClient.CONFIG.chunkMeshModelPartsCacheEnabled
                                    && scratch.addCachedModelParts(state, renderingSeed, modelParts)) {
                                // Cached deterministic model variant selected for this block state and position seed.
                            } else {
                                random.setSeed(renderingSeed);
                                blockRenderManager.getModel(state).addParts(random, modelParts);
                                if (LowSpecRenderCoreClient.CONFIG != null
                                        && LowSpecRenderCoreClient.CONFIG.chunkMeshModelPartsCacheEnabled) {
                                    scratch.cacheModelParts(state, renderingSeed, modelParts);
                                }
                            }
                            matrices.push();
                            matrices.translate(localX, localY, localZ);
                            blockRenderManager.renderBlock(state, pos, region, matrices, blockBuffer, true, modelParts);
                            matrices.pop();
                            modelParts.clear();
                        }
                    }
                }
            }

            for (Entry<BlockRenderLayer, BufferBuilder> entry : builders.entrySet()) {
                BlockRenderLayer layer = entry.getKey();
                BuiltBuffer builtBuffer = entry.getValue().endNullable();
                if (builtBuffer == null) continue;
                if (layer == BlockRenderLayer.TRANSLUCENT) {
                    renderData.translucencySortingData = builtBuffer.sortQuads(
                            allocatorStorage.get(layer), vertexSorter);
                }
                renderData.buffers.put(layer, builtBuffer);
            }

            renderData.chunkOcclusionData = occlusionBuilder.build();
            return renderData;
        } finally {
            long start = lowspec$meshBuildStartNanos.get();
            if (start != 0L) ChunkMeshMetrics.record(System.nanoTime() - start);
            lowspec$meshBuildStartNanos.remove();
            BlockModelRenderer.disableBrightnessCache();
            builders.clear();
            modelParts.clear();
            ChunkRegionStateCache.clear(region);
        }
    }

    @Unique
    private static BufferBuilder lowspec$bufferFor(
            Map<BlockRenderLayer, BufferBuilder> builders,
            BlockBufferAllocatorStorage allocatorStorage,
            BlockRenderLayer layer) {
        BufferBuilder buffer = builders.get(layer);
        if (buffer == null) {
            BufferAllocator allocator = allocatorStorage.get(layer);
            buffer = new BufferBuilder(allocator, VertexFormat.DrawMode.QUADS,
                    VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL);
            builders.put(layer, buffer);
        }
        return buffer;
    }

    @Inject(
            method = "build(Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/client/render/chunk/ChunkRendererRegion;Lcom/mojang/blaze3d/systems/VertexSorter;Lnet/minecraft/client/render/chunk/BlockBufferAllocatorStorage;)Lnet/minecraft/client/render/chunk/SectionBuilder$RenderData;",
            at = @At("RETURN")
    )
    private void lowspec$finishMeshBuild(ChunkSectionPos sectionPos, ChunkRendererRegion region,
                                         VertexSorter sorter, BlockBufferAllocatorStorage storage,
                                         CallbackInfoReturnable<SectionBuilder.RenderData> cir) {
        long start = lowspec$meshBuildStartNanos.get();
        if (start != 0L) ChunkMeshMetrics.record(System.nanoTime() - start);
        lowspec$meshBuildStartNanos.remove();
        ChunkRegionStateCache.clear(region);
    }

    @Unique
    private static final class lowspec$MeshScratch {
        private final EnumMap<BlockRenderLayer, BufferBuilder> builders = new EnumMap<>(BlockRenderLayer.class);
        private final ObjectArrayList<BlockModelPart> modelParts = new ObjectArrayList<>();
        private final Random random = Random.create();
        private final BlockPos.Mutable pos = new BlockPos.Mutable();
        private static final int MODEL_CACHE_SIZE = 256;
        private final BlockState[] cachedModelStates = new BlockState[MODEL_CACHE_SIZE];
        private final long[] cachedModelSeeds = new long[MODEL_CACHE_SIZE];
        private final BlockModelPart[][] cachedModelParts = new BlockModelPart[MODEL_CACHE_SIZE][];

        private boolean addCachedModelParts(BlockState state, long seed, List<BlockModelPart> target) {
            int slot = modelCacheIndex(state, seed);
            BlockModelPart[] parts = cachedModelParts[slot];
            if (parts == null || cachedModelStates[slot] != state || cachedModelSeeds[slot] != seed) {
                return false;
            }
            for (BlockModelPart part : parts) target.add(part);
            return true;
        }

        private void cacheModelParts(BlockState state, long seed, List<BlockModelPart> parts) {
            int slot = modelCacheIndex(state, seed);
            cachedModelStates[slot] = state;
            cachedModelSeeds[slot] = seed;
            cachedModelParts[slot] = parts.toArray(new BlockModelPart[0]);
        }

        private static int modelCacheIndex(BlockState state, long seed) {
            long value = seed ^ (long) System.identityHashCode(state) * 0x9E3779B97F4A7C15L;
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdL;
            value ^= value >>> 33;
            return (int) value & (MODEL_CACHE_SIZE - 1);
        }
    }
}
