package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.ChunkUpdateMetrics;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererChunkMetricsMixin {
    @Unique private long lowspec$chunkUpdateStartNanos;

    @Inject(method = "updateChunks(Lnet/minecraft/client/render/Camera;)V", at = @At("HEAD"))
    private void lowspec$beginChunkUpdateTiming(Camera camera, CallbackInfo ci) {
        if (LowSpecRenderCoreClient.CONFIG != null
                && LowSpecRenderCoreClient.CONFIG.diagnosticsEnabled
                && LowSpecRenderCoreClient.CONFIG.chunkDiagnosticsEnabled) {
            lowspec$chunkUpdateStartNanos = System.nanoTime();
        } else {
            lowspec$chunkUpdateStartNanos = 0L;
        }
    }

    @Inject(method = "updateChunks(Lnet/minecraft/client/render/Camera;)V", at = @At("RETURN"))
    private void lowspec$finishChunkUpdateTiming(Camera camera, CallbackInfo ci) {
        long start = lowspec$chunkUpdateStartNanos;
        if (start != 0L) {
            ChunkUpdateMetrics.record(System.nanoTime() - start);
            lowspec$chunkUpdateStartNanos = 0L;
        }
    }
}
