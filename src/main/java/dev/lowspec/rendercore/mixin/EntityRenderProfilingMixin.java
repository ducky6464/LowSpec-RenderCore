package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.EntityRenderMetrics;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderManager.class)
public abstract class EntityRenderProfilingMixin {
    @Unique private long aether$extractionStart;
    @Unique private long aether$renderStart;
    @Unique private boolean aether$extractionPlayer;
    @Unique private boolean aether$renderPlayer;

    @Inject(method = "getAndUpdateRenderState(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/entity/state/EntityRenderState;",
            at = @At("HEAD"))
    private <E extends Entity> void aether$beginExtraction(E entity, float tickProgress,
                                                           CallbackInfoReturnable<EntityRenderState> cir) {
        aether$extractionStart = EntityRenderMetrics.beginExtraction();
        aether$extractionPlayer = entity.getType() == EntityType.PLAYER;
    }

    @Inject(method = "getAndUpdateRenderState(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/entity/state/EntityRenderState;",
            at = @At("RETURN"))
    private <E extends Entity> void aether$endExtraction(E entity, float tickProgress,
                                                         CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderMetrics.endExtraction(aether$extractionStart, aether$extractionPlayer);
        aether$extractionStart = 0L;
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/render/state/CameraRenderState;DDDLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V",
            at = @At("HEAD"))
    private void aether$beginRender(EntityRenderState state,
                                    net.minecraft.client.render.state.CameraRenderState cameraState,
                                    double offsetX, double offsetY, double offsetZ,
                                    net.minecraft.client.util.math.MatrixStack matrices,
                                    net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
                                    CallbackInfo ci) {
        aether$renderStart = EntityRenderMetrics.beginEnqueue();
        aether$renderPlayer = state.entityType == EntityType.PLAYER;
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/EntityRenderState;Lnet/minecraft/client/render/state/CameraRenderState;DDDLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;)V",
            at = @At("RETURN"))
    private void aether$endRender(EntityRenderState state,
                                  net.minecraft.client.render.state.CameraRenderState cameraState,
                                  double offsetX, double offsetY, double offsetZ,
                                  net.minecraft.client.util.math.MatrixStack matrices,
                                  net.minecraft.client.render.command.OrderedRenderCommandQueue queue,
                                  CallbackInfo ci) {
        EntityRenderMetrics.endEnqueue(aether$renderStart, aether$renderPlayer);
        aether$renderStart = 0L;
    }
}
