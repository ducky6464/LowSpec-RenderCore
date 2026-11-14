package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.ChunkMeshMetrics;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.util.math.ChunkSectionPos;
import com.mojang.blaze3d.systems.VertexSorter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionBuilder.class)
public abstract class SectionBuilderMetricsMixin {
    @Unique private long lowspec$meshBuildStartNanos;

    @Inject(
            method = "build(Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/client/render/chunk/ChunkRendererRegion;Lcom/mojang/blaze3d/systems/VertexSorter;Lnet/minecraft/client/render/chunk/BlockBufferAllocatorStorage;)Lnet/minecraft/client/render/chunk/SectionBuilder$RenderData;",
            at = @At("HEAD")
    )
    private void lowspec$beginMeshBuild(ChunkSectionPos sectionPos, ChunkRendererRegion region,
                                        VertexSorter sorter, BlockBufferAllocatorStorage storage,
                                        CallbackInfoReturnable<SectionBuilder.RenderData> cir) {
        lowspec$meshBuildStartNanos = LowSpecRenderCoreClient.CONFIG != null
                && LowSpecRenderCoreClient.CONFIG.chunkDiagnosticsEnabled ? System.nanoTime() : 0L;
    }

    @Inject(
            method = "build(Lnet/minecraft/util/math/ChunkSectionPos;Lnet/minecraft/client/render/chunk/ChunkRendererRegion;Lcom/mojang/blaze3d/systems/VertexSorter;Lnet/minecraft/client/render/chunk/BlockBufferAllocatorStorage;)Lnet/minecraft/client/render/chunk/SectionBuilder$RenderData;",
            at = @At("RETURN")
    )
    private void lowspec$finishMeshBuild(ChunkSectionPos sectionPos, ChunkRendererRegion region,
                                         VertexSorter sorter, BlockBufferAllocatorStorage storage,
                                         CallbackInfoReturnable<SectionBuilder.RenderData> cir) {
        long start = lowspec$meshBuildStartNanos;
        if (start != 0L) {
            ChunkMeshMetrics.record(System.nanoTime() - start);
            lowspec$meshBuildStartNanos = 0L;
        }
    }
}
