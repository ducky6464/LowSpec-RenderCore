package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.LowSpecRenderCoreClient;
import dev.lowspec.rendercore.RenderFrameMetrics;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMetricsMixin {
    @Unique private long lowspec$renderStartNanos;

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("HEAD"))
    private void lowspec$beginRenderTiming(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        if (LowSpecRenderCoreClient.CONFIG != null
                && LowSpecRenderCoreClient.CONFIG.diagnosticsEnabled) {
            lowspec$renderStartNanos = System.nanoTime();
        } else {
            lowspec$renderStartNanos = 0L;
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("RETURN"))
    private void lowspec$finishRenderTiming(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        long start = lowspec$renderStartNanos;
        if (start != 0L) {
            RenderFrameMetrics.record(System.nanoTime() - start);
            lowspec$renderStartNanos = 0L;
        }
    }
}
