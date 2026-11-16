package dev.lowspec.rendercore.aether;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;

/**
 * Public entry point for Aether's Minecraft-specialized rendering API.
 * Minecraft remains authoritative until a compatible custom draw pipeline is
 * available; the instance upload ring is groundwork, not a renderer.
 */
public final class AetherEngine {
    private static volatile AetherBackend backend;
    private static volatile boolean initialized;
    private static volatile String initializationError = "";
    private static final AetherInstanceBatch INSTANCE_BATCH = new AetherInstanceBatch();
    private static final AetherPlayerRenderQueue PLAYER_RENDER_QUEUE = new AetherPlayerRenderQueue();
    private static final AetherInstanceBufferPool INSTANCE_BUFFER_POOL = new AetherInstanceBufferPool();
    private static volatile GpuBuffer lastUploadedInstanceBuffer;

    private AetherEngine() { }

    public static void beginFrame() {
        INSTANCE_BATCH.beginFrame();
        PLAYER_RENDER_QUEUE.beginFrame();
        // This reference is valid only for the frame whose upload completed.
        // Never expose a previous frame's ring slot after it may be recycled.
        lastUploadedInstanceBuffer = null;
        if (!initialized) initialize();
        AetherBackend active = backend;
        if (active != null) active.beginFrame();
    }

    public static void endFrame() {
        if (INSTANCE_BATCH.instanceCount() > 0) {
            lastUploadedInstanceBuffer = INSTANCE_BUFFER_POOL.upload(INSTANCE_BATCH);
        }
        AetherBackend active = backend;
        if (active != null) active.endFrame();
    }

    public static void initialize() {
        if (initialized || !RenderSystem.isOnRenderThread()) return;
        try {
            AetherCapabilities capabilities = AetherCapabilities.detect();
            backend = new VanillaBackend(capabilities);
            initialized = true;
            LowSpecRenderCoreClient.LOGGER.info(
                    "Aether initialized: backend={}, OpenGL {}.{}, instancedArrays={}, SSBO={}",
                    backend.name(), capabilities.majorVersion(), capabilities.minorVersion(),
                    capabilities.instancedArrays(), capabilities.shaderStorageBuffers());
        } catch (RuntimeException exception) {
            initializationError = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            LowSpecRenderCoreClient.LOGGER.warn("Aether capability detection failed; vanilla rendering remains active.",
                    exception);
        }
    }

    public static void close() {
        if (!RenderSystem.isOnRenderThread()) {
            LowSpecRenderCoreClient.LOGGER.warn("Aether GPU resources were not closed because shutdown was not on the render thread.");
            return;
        }
        INSTANCE_BUFFER_POOL.close();
        lastUploadedInstanceBuffer = null;
    }

    public static boolean isInitialized() { return initialized; }
    public static AetherBackend backend() { return backend; }
    public static AetherInstanceBatch instanceBatch() { return INSTANCE_BATCH; }
    public static AetherPlayerRenderQueue playerRenderQueue() { return PLAYER_RENDER_QUEUE; }
    public static AetherInstanceBufferPool instanceBufferPool() { return INSTANCE_BUFFER_POOL; }
    public static GpuBuffer lastUploadedInstanceBuffer() { return lastUploadedInstanceBuffer; }
    public static String initializationError() { return initializationError; }
}
