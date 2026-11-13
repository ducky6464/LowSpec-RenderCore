package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.LowSpecRenderCoreClient;
import dev.lowspec.rendercore.RenderCoreConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderManager.class)
public abstract class EntityRenderManagerMixin {
    @Inject(
            method = "shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private <E extends Entity> void lowspec$applyEntityDistanceLimit(
            E entity, Frustum frustum, double cameraX, double cameraY, double cameraZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        RenderCoreConfig config = LowSpecRenderCoreClient.CONFIG;
        if (config == null || !config.entityDistanceCullingEnabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (entity == client.player || entity.hasPassengers() && entity == client.getCameraEntity()) return;

        int maxDistance = -1;
        if (entity instanceof PlayerEntity) {
            maxDistance = config.playerRenderDistance;
        } else if (entity instanceof VillagerEntity) {
            maxDistance = config.villagerRenderDistance;
        } else if (entity instanceof HostileEntity) {
            maxDistance = config.hostileMobRenderDistance;
        } else if (entity instanceof PassiveEntity) {
            maxDistance = config.passiveMobRenderDistance;
        } else if (entity instanceof ItemEntity) {
            maxDistance = config.droppedItemRenderDistance;
        }

        if (maxDistance >= 0
                && entity.squaredDistanceTo(cameraX, cameraY, cameraZ) > (double) maxDistance * maxDistance) {
            cir.setReturnValue(false);
        }
    }
}
