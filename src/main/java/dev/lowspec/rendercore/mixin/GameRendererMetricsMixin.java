package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.AdaptiveEntityVisibility;
import dev.lowspec.rendercore.EntityRenderMetrics;
import dev.lowspec.rendercore.aether.AetherEngine;
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
        AetherEngine.beginFrame();
        boolean diagnosticsEnabled = LowSpecRenderCoreClient.CONFIG != null
                && LowSpecRenderCoreClient.CONFIG.diagnosticsEnabled;
        lowspec$renderStartNanos = diagnosticsEnabled ? System.nanoTime() : 0L;
        AdaptiveEntityVisibility.beginFrame();
    }

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("RETURN"))
    private void lowspec$finishRenderTiming(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        AetherEngine.endFrame();
        EntityRenderMetrics.finishFrame();
        long start = lowspec$renderStartNanos;
        if (start != 0L) {
            long elapsed = System.nanoTime() - start;
            if (LowSpecRenderCoreClient.CONFIG != null
                    && LowSpecRenderCoreClient.CONFIG.diagnosticsEnabled) {
                RenderFrameMetrics.record(elapsed);
            }
            lowspec$renderStartNanos = 0L;
        }
        AdaptiveEntityVisibility.finishFrame();
    }
}
