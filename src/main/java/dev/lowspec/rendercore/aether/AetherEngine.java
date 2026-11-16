package dev.lowspec.rendercore.aether;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
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
    private static final AetherPlayerGeometryDrawPlan PLAYER_GEOMETRY_DRAW_PLAN = new AetherPlayerGeometryDrawPlan(PLAYER_RENDER_QUEUE.capacity());
    private static final AetherPlayerGeometryArena PLAYER_GEOMETRY_ARENA =
            new AetherPlayerGeometryArena(128, 128, 128 * 128 * 48);
    private static final MatrixStack PLAYER_GEOMETRY_MATRICES = new MatrixStack();
    private static final AetherInstanceBufferPool INSTANCE_BUFFER_POOL = new AetherInstanceBufferPool();
    private static final AetherPlayerGeometryBufferPool PLAYER_GEOMETRY_BUFFER_POOL =
            new AetherPlayerGeometryBufferPool(PLAYER_GEOMETRY_ARENA.capacityFloats());
    private static volatile GpuBuffer lastUploadedPlayerGeometryBuffer;
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
        PLAYER_GEOMETRY_ARENA.beginFrame();
        // This reference is valid only for the frame whose upload completed.
        // Never expose a previous frame's ring slot after it may be recycled.
        lastUploadedInstanceBuffer = null;
        lastUploadedPlayerGeometryBuffer = null;
        if (!initialized) initialize();
        AetherBackend active = backend;
        if (active != null) active.beginFrame();
    }

    public static void endFrame() {
        PLAYER_RENDER_QUEUE.buildSkinGroups();
        PLAYER_GEOMETRY_DRAW_PLAN.build(PLAYER_RENDER_QUEUE.batchPlan(), PLAYER_GEOMETRY_ARENA.offsets(), PLAYER_GEOMETRY_ARENA.floatCounts(), PLAYER_GEOMETRY_ARENA.vertexCounts(), PLAYER_RENDER_QUEUE.size());
        if (PLAYER_GEOMETRY_ARENA.usedFloats() > 0) {
            lastUploadedPlayerGeometryBuffer = PLAYER_GEOMETRY_BUFFER_POOL.upload(PLAYER_GEOMETRY_ARENA);
        }
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
        PLAYER_GEOMETRY_BUFFER_POOL.close();
        EntityGpuMetrics.close();
        lastUploadedInstanceBuffer = null;
    }

    public static boolean isInitialized() { return initialized; }
    public static AetherBackend backend() { return backend; }
    public static AetherInstanceBatch instanceBatch() { return INSTANCE_BATCH; }
    public static AetherPlayerRenderQueue playerRenderQueue() { return PLAYER_RENDER_QUEUE; }
    public static AetherPlayerGeometryArena playerGeometryArena() { return PLAYER_GEOMETRY_ARENA; }
    public static AetherPlayerGeometryDrawPlan playerGeometryDrawPlan() { return PLAYER_GEOMETRY_DRAW_PLAN; }
    /** Captures the posed base model into the bounded frame arena; vanilla rendering is untouched. */
    public static boolean capturePlayerGeometry(int playerIndex, ModelPart root) {
        if (!RenderSystem.isOnRenderThread() || root == null) return false;
        return PLAYER_GEOMETRY_ARENA.captureOnRenderThread(playerIndex, root, PLAYER_GEOMETRY_MATRICES);
    }
    public static AetherInstanceBufferPool instanceBufferPool() { return INSTANCE_BUFFER_POOL; }
    public static AetherPlayerGeometryBufferPool playerGeometryBufferPool() { return PLAYER_GEOMETRY_BUFFER_POOL; }
    public static GpuBuffer lastUploadedPlayerGeometryBuffer() { return lastUploadedPlayerGeometryBuffer; }
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
