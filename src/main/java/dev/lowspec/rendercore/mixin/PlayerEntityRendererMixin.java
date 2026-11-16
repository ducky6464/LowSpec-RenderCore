package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.aether.AetherEngine;
import dev.lowspec.rendercore.aether.AetherPlayerRenderEligibility;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Shadow protected EntityModel<?> model;

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD")
    )
    private void aether$captureBasePlayerState(LivingEntityRenderState state,
                                                MatrixStack matrices,
                                                OrderedRenderCommandQueue queue,
                                                CameraRenderState cameraState,
                                                CallbackInfo ci) {
        if (state instanceof PlayerEntityRenderState playerState) {
            AetherEngine.playerRenderQueue().capture(playerState);
        }
    }

    /**
     * Capture at the base-model queue submission point, while the renderer's
     * entity translation/rotation/scale are still on the supplied matrix stack.
     * Feature-layer submissions happen in their own renderers and stay vanilla.
     */
    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/command/RenderCommandQueue;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/RenderLayer;IIILnet/minecraft/client/texture/Sprite;ILnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)V",
                    shift = At.Shift.BEFORE
            )
    )
    private void aether$capturePosedBaseModel(LivingEntityRenderState state,
                                               MatrixStack matrices,
                                               OrderedRenderCommandQueue queue,
                                               CameraRenderState cameraState,
                                               CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState playerState)) return;
        if (!(model instanceof PlayerEntityModel playerModel)) return;
        var snapshots = AetherEngine.playerRenderQueue();
        int index = snapshots.size() - 1;
        if (index < 0 || snapshots.playerId(index) != playerState.id) return;
        if (!AetherPlayerRenderEligibility.canUseCustomBaseModel(snapshots.flags(index))) return;
        AetherEngine.capturePlayerGeometry(index, playerModel.getRootPart(), matrices);
    }
}
