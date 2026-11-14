package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.LightingStateCache;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkLightProvider.class)
public abstract class ChunkLightProviderStateCacheMixin {
    @Inject(method = "doLightUpdates()I", at = @At("HEAD"))
    private void lowspec$beginLightingCachePass(CallbackInfoReturnable<Integer> cir) {
        LightingStateCache.beginPass();
    }

    @Inject(method = "doLightUpdates()I", at = @At("RETURN"))
    private void lowspec$endLightingCachePass(CallbackInfoReturnable<Integer> cir) {
        LightingStateCache.endPass();
    }

    @Inject(method = "doLightUpdates()I", at = @At("THROW"))
    private void lowspec$abortLightingCachePass(CallbackInfoReturnable<Integer> cir) {
        LightingStateCache.endPass();
    }

    @Inject(
            method = "getStateForLighting(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lowspec$lookupLightingBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (LowSpecRenderCoreClient.CONFIG == null || !LowSpecRenderCoreClient.CONFIG.lightingStateCacheEnabled) return;
        BlockState cached = LightingStateCache.lookup(pos);
        if (cached != null) cir.setReturnValue(cached);
    }

    @Inject(
            method = "getStateForLighting(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;",
            at = @At("RETURN")
    )
    private void lowspec$storeLightingBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (LowSpecRenderCoreClient.CONFIG != null && LowSpecRenderCoreClient.CONFIG.lightingStateCacheEnabled) {
            LightingStateCache.store(pos, cir.getReturnValue());
        }
    }
}
