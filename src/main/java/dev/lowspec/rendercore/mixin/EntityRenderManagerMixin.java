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

        // AEV preserves the local player and camera target, but may hide distant
        // remote players and other entities when measured frame pressure persists.
        double adaptiveScale = config.adaptiveEntityVisibilityEnabled
                ? AdaptiveEntityVisibility.getDistanceScale() : 1.0;
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
        } else if (entity instanceof ProjectileEntity) {
            maxDistance = config.projectileRenderDistance;
        } else if (entity instanceof ExperienceOrbEntity) {
            maxDistance = config.experienceOrbRenderDistance;
        } else if (entity instanceof ArmorStandEntity) {
            maxDistance = config.armorStandRenderDistance;
        }

        if (maxDistance < 0) return;
        if (!config.entityDistanceCullingEnabled && adaptiveScale >= 1.0) return;
        if (config.adaptiveEntityVisibilityEnabled || config.entityDistanceCullingEnabled) {
            int minimumDistance = entity instanceof PlayerEntity ? 24 : 8;
            maxDistance = (int) Math.max(minimumDistance,
                    Math.floor(maxDistance * (config.adaptiveEntityVisibilityEnabled ? adaptiveScale : 1.0)));
        }
        if (entity.squaredDistanceTo(cameraX, cameraY, cameraZ) > (double) maxDistance * maxDistance) {
            cir.setReturnValue(false);
        }
    }
}
