package dev.lowspec.rendercore.aether;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;

/**
 * Public entry point for Aether's Minecraft-specialized rendering API.
 * The first release intentionally selects the vanilla fallback while it
 * discovers capabilities and establishes safe lifecycle ownership.
 */
public final class AetherEngine {
    private static volatile AetherBackend backend;
    private static volatile boolean initialized;
    private static volatile String initializationError = "";
    private static final AetherInstanceBatch INSTANCE_BATCH = new AetherInstanceBatch();

    private AetherEngine() { }

    public static void beginFrame() {
        INSTANCE_BATCH.beginFrame();
        if (!initialized) initialize();
        AetherBackend active = backend;
        if (active != null) active.beginFrame();
    }

    public static void endFrame() {
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
            // Retry on a later frame in case the context was not ready yet.
        }
    }

    public static boolean isInitialized() { return initialized; }
    public static AetherBackend backend() { return backend; }
    public static AetherInstanceBatch instanceBatch() { return INSTANCE_BATCH; }
    public static String initializationError() { return initializationError; }
}
