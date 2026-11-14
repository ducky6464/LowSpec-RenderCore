package dev.lowspec.rendercore;

import dev.lowspec.rendercore.mixin.ChunkRendererRegionInvoker;
import dev.lowspec.rendercore.mixin.RenderedChunkAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.chunk.ChunkOcclusionData;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.RenderedChunk;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.PalettedContainer;

import java.util.function.Predicate;

/**
 * Conservative fast path for sections whose immutable render snapshot contains
 * no non-air block states and no block entities. Debug worlds are excluded.
 */
public final class EmptySectionFastPath {
    private static final Predicate<BlockState> HAS_RENDERABLE_BLOCK_STATE = state -> !state.isAir();

    private EmptySectionFastPath() { }

    public static SectionBuilder.RenderData tryBuildEmpty(
            ChunkSectionPos sectionPos, ChunkRendererRegion region) {
        ChunkRendererRegionInvoker regionInvoker = (ChunkRendererRegionInvoker) (Object) region;
        RenderedChunk renderedChunk = regionInvoker.lowspec$invokeGetRenderedChunk(
                sectionPos.getSectionX(), sectionPos.getSectionY(), sectionPos.getSectionZ());
        if (renderedChunk == null) return null;

        RenderedChunkAccessor access = (RenderedChunkAccessor) (Object) renderedChunk;
        if (access.lowspec$isDebugWorld() || !access.lowspec$getBlockEntities().isEmpty()) return null;

        PalettedContainer<BlockState> palette = access.lowspec$getBlockPalette();
        // A missing palette is not proof of emptiness; fall back to vanilla in that case.
        if (palette == null || palette.hasAny(HAS_RENDERABLE_BLOCK_STATE)) return null;

        SectionBuilder.RenderData data = new SectionBuilder.RenderData();
        ChunkOcclusionData occlusion = new ChunkOcclusionData();
        occlusion.fill(true);
        data.chunkOcclusionData = occlusion;
        return data;
    }
}
