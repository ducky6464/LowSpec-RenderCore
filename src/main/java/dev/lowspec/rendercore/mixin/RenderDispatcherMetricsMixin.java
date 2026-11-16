package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.EntityRenderMetrics;
import dev.lowspec.rendercore.EntityGpuMetrics;
import net.minecraft.client.render.command.RenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Measures CPU time spent executing Minecraft's queued render commands.
 * This is CPU submission/dispatch time, not asynchronous GPU execution time.
 */
@Mixin(RenderDispatcher.class)
public abstract class RenderDispatcherMetricsMixin {
    @Unique private final ThreadLocal<Long> aether$dispatchStart =
            ThreadLocal.withInitial(() -> 0L);
    @Unique private boolean aether$gpuQueryStarted;

    @Inject(method = "render()V", at = @At("HEAD"))
    private void aether$beginDispatch(CallbackInfo ci) {
        aether$dispatchStart.set(EntityRenderMetrics.beginDispatch());
        aether$gpuQueryStarted = EntityGpuMetrics.beginDispatch();
    }

    @Inject(method = "render()V", at = @At("RETURN"))
    private void aether$endDispatch(CallbackInfo ci) {
        EntityRenderMetrics.endDispatch(aether$dispatchStart.get());
        aether$dispatchStart.set(0L);
        EntityGpuMetrics.endDispatch(aether$gpuQueryStarted);
        aether$gpuQueryStarted = false;
    }
}
