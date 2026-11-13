package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.LightingMetrics;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightingProvider.class)
public abstract class LightingProviderMixin {
    @Unique private long lowspec$lightUpdateStartNanos;

    @Inject(method = "doLightUpdates()I", at = @At("HEAD"))
    private void lowspec$beginLightUpdateTiming(CallbackInfoReturnable<Integer> cir) {
        if (LowSpecRenderCoreClient.CONFIG != null
                && LowSpecRenderCoreClient.CONFIG.diagnosticsEnabled
                && LowSpecRenderCoreClient.CONFIG.lightingDiagnosticsEnabled) {
            lowspec$lightUpdateStartNanos = System.nanoTime();
        } else {
            lowspec$lightUpdateStartNanos = 0L;
        }
    }

    @Inject(method = "doLightUpdates()I", at = @At("RETURN"))
    private void lowspec$finishLightUpdateTiming(CallbackInfoReturnable<Integer> cir) {
        long start = lowspec$lightUpdateStartNanos;
        if (start != 0L) {
            LightingMetrics.record(System.nanoTime() - start, cir.getReturnValueI());
            lowspec$lightUpdateStartNanos = 0L;
        }
    }
}
