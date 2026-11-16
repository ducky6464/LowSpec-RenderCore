package dev.lowspec.rendercore.aether;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Experimental live-world bridge for validating captured player meshes.
 * Mirror mode is OFF by default and deliberately does not replace vanilla rendering.
 */
public final class AetherPlayerWorldRenderer {
    private static final Vector4f WHITE = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    private static final Vector3f ZERO_OFFSET = new Vector3f();
    private static final Matrix4f IDENTITY_TEXTURE = new Matrix4f();
    private static volatile boolean mirrorEnabled;
    private static volatile long successfulFrames;
    private static volatile long submittedPackets;
    private static volatile long skippedFrames;
    private static volatile long failedFrames;
    private static volatile String lastStatus = "disabled";

    private AetherPlayerWorldRenderer() { }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> drawAfterEntities());
    }

    public static void setMirrorEnabled(boolean enabled) {
        mirrorEnabled = enabled;
        lastStatus = enabled ? "enabled; experimental mirror pass" : "disabled";
    }

    public static boolean isMirrorEnabled() { return mirrorEnabled; }
    public static long successfulFrames() { return successfulFrames; }
    public static long submittedPackets() { return submittedPackets; }
    public static long skippedFrames() { return skippedFrames; }
    public static long failedFrames() { return failedFrames; }
    public static String lastStatus() { return lastStatus; }

    private static void drawAfterEntities() {
        if (!mirrorEnabled) return;
        if (!RenderSystem.isOnRenderThread()) {
            skippedFrames++;
            lastStatus = "skipped: not on render thread";
            return;
        }
        AetherPlayerRenderQueue queue = AetherEngine.playerRenderQueue();
        if (queue.size() == 0) {
            skippedFrames++;
            lastStatus = "skipped: no player snapshots";
            return;
        }

        GpuBuffer vertexBuffer = AetherEngine.preparePlayerGeometryForDraw();
        AetherPlayerGeometryDrawPlan plan = AetherEngine.playerGeometryDrawPlan();
        if (vertexBuffer == null || plan.packetCount() == 0) {
            skippedFrames++;
            lastStatus = "skipped: no uploaded player geometry";
            return;
        }

        Framebuffer target = MinecraftClient.getInstance().getFramebuffer();
        if (target == null || target.getColorAttachmentView() == null || target.getDepthAttachmentView() == null) {
            skippedFrames++;
            lastStatus = "skipped: main framebuffer color/depth attachment unavailable";
            return;
        }

        int submitted = 0;
        try {
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().write(
                    RenderSystem.getModelViewMatrix(), WHITE, ZERO_OFFSET, IDENTITY_TEXTURE);
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "Aether player mirror pass",
                    target.getColorAttachmentView(),
                    OptionalInt.empty(),
                    target.getDepthAttachmentView(),
                    OptionalDouble.empty())) {
                if (AetherPlayerPipeline.pipeline() == null) {
                    skippedFrames++;
                    lastStatus = "skipped: Aether pipeline not registered";
                    return;
                }
                for (int skinGroup = 0; skinGroup < queue.skinGroupCount(); skinGroup++) {
                    submitted += AetherPlayerDrawSubmitter.submitSkinBatch(
                            pass, vertexBuffer, queue, plan, skinGroup, transforms);
                }
            }
            if (submitted > 0) {
                AetherEngine.markPlayerGeometryDrawSubmitted(vertexBuffer);
                successfulFrames++;
                submittedPackets += submitted;
                lastStatus = "submitted " + submitted + " player base-model ranges; vanilla still active";
            } else {
                skippedFrames++;
                lastStatus = "skipped: no skin batch could be submitted";
            }
        } catch (RuntimeException exception) {
            failedFrames++;
            lastStatus = "failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage();
            if (failedFrames == 1 || failedFrames % 100 == 0) {
                dev.lowspec.rendercore.LowSpecRenderCoreClient.LOGGER.warn(
                        "Aether experimental player mirror pass failed (failures={})", failedFrames, exception);
            }
        }
    }
}
