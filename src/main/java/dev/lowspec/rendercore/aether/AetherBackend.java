package dev.lowspec.rendercore.aether;

/**
 * Backend contract for Aether. Implementations own render-thread resources;
 * worker threads must never call backend methods directly.
 */
public interface AetherBackend {
    String name();
    AetherCapabilities capabilities();
    void beginFrame();
    void endFrame();
}
