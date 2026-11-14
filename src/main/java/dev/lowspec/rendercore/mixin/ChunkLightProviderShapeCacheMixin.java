package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.LightingShapeCache;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkLightProvider.class)
public abstract class ChunkLightProviderShapeCacheMixin {
    @Inject(
            method = "shapesCoverFullCube(Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Direction;)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lowspec$lookupLightingShape(BlockState source, BlockState target, Direction direction,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (LowSpecRenderCoreClient.CONFIG == null
                || !LowSpecRenderCoreClient.CONFIG.lightingShapeCacheEnabled) return;
        Boolean cached = LightingShapeCache.lookup(source, target, direction);
        if (cached != null) cir.setReturnValue(cached);
    }

    @Inject(
            method = "shapesCoverFullCube(Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Direction;)Z",
            at = @At("RETURN")
    )
    private void lowspec$storeLightingShape(BlockState source, BlockState target, Direction direction,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (LowSpecRenderCoreClient.CONFIG != null
                && LowSpecRenderCoreClient.CONFIG.lightingShapeCacheEnabled) {
            LightingShapeCache.store(source, target, direction, cir.getReturnValue());
        }
    }
}
