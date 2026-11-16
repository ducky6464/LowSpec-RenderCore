package dev.lowspec.rendercore.aether;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;
import dev.lowspec.rendercore.EntityGpuMetrics;

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
    private static volatile boolean uploadSelfTestRequested;
    private static long uploadSelfTestRuns;
    private static long uploadSelfTestPassed;
    private static long uploadSelfTestFailed;
    private static volatile String uploadSelfTestStatus = "not run";

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
        PLAYER_RENDER_QUEUE.buildSkinGroups();
        if (INSTANCE_BATCH.instanceCount() > 0) {
            lastUploadedInstanceBuffer = INSTANCE_BUFFER_POOL.upload(INSTANCE_BATCH);
        }
        if (uploadSelfTestRequested) {
            uploadSelfTestRequested = false;
            uploadSelfTestRuns++;
            AetherInstanceBatch testBatch = new AetherInstanceBatch(1);
            float[] identity = new float[16];
            identity[0] = identity[5] = identity[10] = identity[15] = 1.0f;
            if (testBatch.tryAppend(identity, 0xFFFFFFFF, 0)) {
                GpuBuffer uploaded = INSTANCE_BUFFER_POOL.upload(testBatch);
                if (uploaded != null) {
                    uploadSelfTestPassed++;
                    uploadSelfTestStatus = "passed: 72-byte GPU upload queued and fenced (no draw issued)";
                    lastUploadedInstanceBuffer = uploaded;
                } else {
                    uploadSelfTestFailed++;
                    uploadSelfTestStatus = "failed: upload returned no buffer; inspect ring errors/skips";
                }
            } else {
                uploadSelfTestFailed++;
                uploadSelfTestStatus = "failed: could not stage the test instance";
            }
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
        EntityGpuMetrics.close();
        lastUploadedInstanceBuffer = null;
    }

    public static boolean isInitialized() { return initialized; }
    public static AetherBackend backend() { return backend; }
    public static AetherInstanceBatch instanceBatch() { return INSTANCE_BATCH; }
    public static AetherPlayerRenderQueue playerRenderQueue() { return PLAYER_RENDER_QUEUE; }
    public static AetherInstanceBufferPool instanceBufferPool() { return INSTANCE_BUFFER_POOL; }
    public static GpuBuffer lastUploadedInstanceBuffer() { return lastUploadedInstanceBuffer; }
    /** Queues a one-record upload smoke test for the next render-thread frame end. */
    public static boolean requestUploadSelfTest() {
        if (uploadSelfTestRequested) return false;
        uploadSelfTestRequested = true;
        return true;
    }
    public static String uploadSelfTestStatus() { return uploadSelfTestStatus; }
    public static long uploadSelfTestRuns() { return uploadSelfTestRuns; }
    public static long uploadSelfTestPassed() { return uploadSelfTestPassed; }
    public static long uploadSelfTestFailed() { return uploadSelfTestFailed; }
    public static String initializationError() { return initializationError; }
}
