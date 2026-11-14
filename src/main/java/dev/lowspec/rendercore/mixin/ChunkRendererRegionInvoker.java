package dev.lowspec.rendercore.mixin;

import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.RenderedChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkRendererRegion.class)
public interface ChunkRendererRegionInvoker {
    @Invoker("getRenderedChunk")
    RenderedChunk lowspec$invokeGetRenderedChunk(int sectionX, int sectionY, int sectionZ);
}
