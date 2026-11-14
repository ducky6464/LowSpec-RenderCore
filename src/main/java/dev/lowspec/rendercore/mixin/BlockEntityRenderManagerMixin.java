package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.LowSpecRenderCoreClient;
import dev.lowspec.rendercore.RenderCoreConfig;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.render.block.entity.BlockEntityRenderManager")
public abstract class BlockEntityRenderManagerMixin {
    @Inject(
            method = "getRenderState(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)Lnet/minecraft/client/render/block/entity/state/BlockEntityRenderState;",
            at = @At("HEAD"),
            cancellable = true
    )
    private <E extends BlockEntity> void lowspec$cullDistantBlockEntities(
            E blockEntity, float tickProgress, ModelCommandRenderer.CrumblingOverlayCommand overlay,
            CallbackInfoReturnable<BlockEntityRenderState> cir
    ) {
        RenderCoreConfig config = LowSpecRenderCoreClient.CONFIG;
        if (config == null || !config.blockEntityDistanceCullingEnabled) return;
        if (blockEntity instanceof BeaconBlockEntity) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.gameRenderer == null) return;
        // Use the active camera, not the player entity: spectator/free-camera views
        // must cull relative to what is actually being rendered.
        var cameraPos = client.gameRenderer.getCamera().getCameraPos();
        double dx = blockEntity.getPos().getX() + 0.5 - cameraPos.getX();
        double dy = blockEntity.getPos().getY() + 0.5 - cameraPos.getY();
        double dz = blockEntity.getPos().getZ() + 0.5 - cameraPos.getZ();
        if (dx * dx + dy * dy + dz * dz
                > (double) config.blockEntityRenderDistance * config.blockEntityRenderDistance) {
            cir.setReturnValue(null);
        }
    }
}
