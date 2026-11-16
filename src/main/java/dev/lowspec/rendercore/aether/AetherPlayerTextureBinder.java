package dev.lowspec.rendercore.aether;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.Identifier;

/** Resolves a captured player's body skin to the GPU texture and sampler used by a render pass. */
public final class AetherPlayerTextureBinder {
    private AetherPlayerTextureBinder() { }

    public static Binding resolve(SkinTextures skin) {
        AetherRenderThread.assertCurrent();
        if (skin == null || skin.body() == null) return null;
        Identifier id = skin.body().texturePath();
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(id);
        if (texture == null) return null;
        GpuTextureView view = texture.getGlTextureView();
        GpuSampler sampler = texture.getSampler();
        if (view == null || sampler == null) return null;
        return new Binding(id, view, sampler);
    }

    public static boolean bind(RenderPass pass, SkinTextures skin) {
        if (pass == null) return false;
        Binding binding = resolve(skin);
        if (binding == null) return false;
        pass.bindTexture("Sampler0", binding.textureView(), binding.sampler());
        return true;
    }

    /** Frame-scoped view of a Minecraft-owned texture; callers must not close or retain it across reloads. */
    public record Binding(Identifier textureId, GpuTextureView textureView, GpuSampler sampler) { }
}
