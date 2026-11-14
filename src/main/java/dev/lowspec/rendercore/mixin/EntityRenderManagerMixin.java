package dev.lowspec.rendercore.mixin;

import dev.lowspec.rendercore.AdaptiveEntityVisibility;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;
import dev.lowspec.rendercore.RenderCoreConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
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
    private <E extends Entity> void lowspec$applyEntityVisibilityPolicy(
            E entity, Frustum frustum, double cameraX, double cameraY, double cameraZ,
            CallbackInfoReturnable<Boolean> cir
    ) {
        AdaptiveEntityVisibility.recordCandidate(entity instanceof PlayerEntity);
        RenderCoreConfig config = LowSpecRenderCoreClient.CONFIG;
        if (config == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (entity == client.player || entity == client.getCameraEntity()) return;

        // AEV deliberately preserves player visibility. It first reduces distant
        // non-player rendering when the previous frame was overloaded.
        double adaptiveScale = config.adaptiveEntityVisibilityEnabled
                ? AdaptiveEntityVisibility.getDistanceScale() : 1.0;
        int maxDistance = -1;
        if (entity instanceof PlayerEntity) {
            if (!config.entityDistanceCullingEnabled) return;
            maxDistance = config.playerRenderDistance;
        } else if (entity instanceof VillagerEntity) {
            if (!config.entityDistanceCullingEnabled) return;
            maxDistance = config.villagerRenderDistance;
        } else if (entity instanceof HostileEntity) {
            if (!config.entityDistanceCullingEnabled) return;
            maxDistance = config.hostileMobRenderDistance;
        } else if (entity instanceof PassiveEntity) {
            if (!config.entityDistanceCullingEnabled) return;
            maxDistance = config.passiveMobRenderDistance;
        } else if (entity instanceof ItemEntity) {
            if (!config.entityDistanceCullingEnabled) return;
            maxDistance = config.droppedItemRenderDistance;
        } else if (entity instanceof ProjectileEntity) {
            if (!config.entityDistanceCullingEnabled) return;
            maxDistance = config.projectileRenderDistance;
        } else if (entity instanceof ExperienceOrbEntity) {
            if (!config.entityDistanceCullingEnabled) return;
            maxDistance = config.experienceOrbRenderDistance;
        } else if (entity instanceof ArmorStandEntity) {
            if (!config.entityDistanceCullingEnabled) return;
            maxDistance = config.armorStandRenderDistance;
        }

        if (maxDistance < 0) return;
        if (!(entity instanceof PlayerEntity)) {
            maxDistance = (int) Math.max(8, Math.floor(maxDistance * adaptiveScale));
        }
        if (entity.squaredDistanceTo(cameraX, cameraY, cameraZ) > (double) maxDistance * maxDistance) {
            cir.setReturnValue(false);
        }
    }
}
