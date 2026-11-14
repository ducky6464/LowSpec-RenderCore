package dev.lowspec.rendercore.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.lowspec.rendercore.ChunkGpuBufferPool;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;
import net.minecraft.client.render.chunk.Buffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Buffers.class)
public abstract class BuffersGpuPoolMixin {
    @Shadow private GpuBuffer vertexBuffer;
    @Shadow private GpuBuffer indexBuffer;

    @Inject(method = "close()V", at = @At("HEAD"), cancellable = true)
    private void lowspec$returnChunkBuffersToPool(CallbackInfo ci) {
        if (LowSpecRenderCoreClient.CONFIG == null
                || !LowSpecRenderCoreClient.CONFIG.chunkGpuBufferPoolEnabled) return;
        GpuBuffer retiredVertex = vertexBuffer;
        GpuBuffer retiredIndex = indexBuffer;
        vertexBuffer = null;
        indexBuffer = null;
        ChunkGpuBufferPool.release(retiredVertex);
        if (retiredIndex != retiredVertex) ChunkGpuBufferPool.release(retiredIndex);
        ci.cancel();
    }
}
