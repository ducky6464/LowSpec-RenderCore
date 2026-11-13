package dev.lowspec.rendercore;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LowSpecRenderCoreClient implements ClientModInitializer {
    public static final String MOD_ID = "lowspec_rendercore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static RenderCoreConfig CONFIG;

    private static long measuredTicks;
    private static double averageTickMillis;
    private long tickStartNanos;

    @Override
    public void onInitializeClient() {
        CONFIG = RenderCoreConfig.load(FabricLoader.getInstance().getConfigDir());
        ClientTickEvents.START_CLIENT_TICK.register(client -> tickStartNanos = System.nanoTime());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!CONFIG.diagnosticsEnabled || tickStartNanos == 0L) return;
            double elapsedMs = (System.nanoTime() - tickStartNanos) / 1_000_000.0;
            measuredTicks++;
            averageTickMillis += (elapsedMs - averageTickMillis) / measuredTicks;
        });
        LOGGER.info("LowSpec RenderCore initialized. Diagnostics: {}. Lighting timing: {}.",
                CONFIG.diagnosticsEnabled ? "on" : "off",
                CONFIG.lightingDiagnosticsEnabled ? "on" : "off");
    }

    public static double getAverageClientTickMillis() { return averageTickMillis; }
    public static long getMeasuredTicks() { return measuredTicks; }
    public static LightingMetrics.Snapshot getLightingMetrics() { return LightingMetrics.snapshot(); }
}
