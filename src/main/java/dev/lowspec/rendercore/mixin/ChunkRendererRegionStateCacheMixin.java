package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.ChunkRegionStateCache;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FluidState;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkRendererRegion.class)
public abstract class ChunkRendererRegionStateCacheMixin {
    @Inject(
            method = "getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lowspec$lookupCachedBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (LowSpecRenderCoreClient.CONFIG == null
                || !LowSpecRenderCoreClient.CONFIG.chunkMeshStateCacheEnabled) return;
        ChunkRendererRegion region = (ChunkRendererRegion) (Object) this;
        BlockState cached = ChunkRegionStateCache.lookupInSection(region, pos);
        if (cached == null) cached = ChunkRegionStateCache.lookup(region, pos);
        if (cached != null) cir.setReturnValue(cached);
    }

    @Inject(
            method = "getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/BlockState;",
            at = @At("RETURN")
    )
    private void lowspec$storeBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (LowSpecRenderCoreClient.CONFIG == null
                || !LowSpecRenderCoreClient.CONFIG.chunkMeshStateCacheEnabled) return;
        ChunkRendererRegion region = (ChunkRendererRegion) (Object) this;
        if (ChunkRegionStateCache.isInActiveSection(region, pos)) {
            ChunkRegionStateCache.storeInSection(region, pos, cir.getReturnValue());
        } else {
            ChunkRegionStateCache.store(region, pos, cir.getReturnValue());
        }
    }

    @Inject(
            method = "getFluidState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/fluid/FluidState;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void lowspec$lookupCachedFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (LowSpecRenderCoreClient.CONFIG == null
                || !LowSpecRenderCoreClient.CONFIG.chunkMeshStateCacheEnabled) return;
        ChunkRendererRegion region = (ChunkRendererRegion) (Object) this;
        BlockState cached = ChunkRegionStateCache.lookupInSection(region, pos);
        if (cached == null) cached = ChunkRegionStateCache.lookup(region, pos);
        if (cached != null) cir.setReturnValue(cached.getFluidState());
    }
}
