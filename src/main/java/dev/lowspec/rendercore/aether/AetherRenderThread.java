package dev.lowspec.rendercore.aether;

import com.mojang.blaze3d.systems.RenderSystem;

/** Enforces Minecraft's render-thread ownership rule for Aether GPU resources. */
public final class AetherRenderThread {
    private AetherRenderThread() { }

    public static void assertCurrent() {
        RenderSystem.assertOnRenderThread();
    }

    public static boolean isCurrent() {
        return RenderSystem.isOnRenderThread();
    }
}
