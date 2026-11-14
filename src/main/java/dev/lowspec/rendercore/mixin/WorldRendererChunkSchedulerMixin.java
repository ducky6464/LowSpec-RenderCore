package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.ChunkRenderScheduler;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererChunkSchedulerMixin {
    @Inject(method = "scheduleChunkRender(IIIZ)V", at = @At("HEAD"), cancellable = true)
    private void lowspec$queueChunkRebuild(int x, int y, int z, boolean important, CallbackInfo ci) {
        if (ChunkRenderScheduler.isReplaying()) return;
        if (LowSpecRenderCoreClient.CONFIG == null || !LowSpecRenderCoreClient.CONFIG.chunkSchedulingEnabled) return;
        if (ChunkRenderScheduler.enqueue(x, y, z, important)) ci.cancel();
    }
}
