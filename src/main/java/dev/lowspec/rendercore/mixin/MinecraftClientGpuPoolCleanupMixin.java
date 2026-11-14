package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.ChunkGpuBufferPool;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientGpuPoolCleanupMixin {
    @Inject(method = "close()V", at = @At("HEAD"))
    private void lowspec$closePooledGpuBuffers(CallbackInfo ci) {
        ChunkGpuBufferPool.clear();
    }
}
