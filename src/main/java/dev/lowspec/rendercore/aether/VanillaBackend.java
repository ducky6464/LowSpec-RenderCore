package dev.lowspec.rendercore.aether;

/** Safe initial backend: keeps Minecraft's renderer authoritative. */
public final class VanillaBackend implements AetherBackend {
    private final AetherCapabilities capabilities;

    public VanillaBackend(AetherCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    @Override public String name() { return "vanilla-fallback"; }
    @Override public AetherCapabilities capabilities() { return capabilities; }
    @Override public void beginFrame() { }
    @Override public void endFrame() { }
}
