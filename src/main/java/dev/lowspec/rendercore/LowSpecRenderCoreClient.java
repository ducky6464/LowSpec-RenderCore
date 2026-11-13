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
    private long tickStartNanos;
    private long lastTickNanos;
    private long measuredTicks;
    private double averageTickMillis;

    @Override
    public void onInitializeClient() {
        CONFIG = RenderCoreConfig.load(FabricLoader.getInstance().getConfigDir());
        ClientTickEvents.START_CLIENT_TICK.register(client -> tickStartNanos = System.nanoTime());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = System.nanoTime();
            if (CONFIG.diagnosticsEnabled && tickStartNanos != 0L) {
                double elapsedMs = (now - tickStartNanos) / 1_000_000.0;
                measuredTicks++;
                averageTickMillis += (elapsedMs - averageTickMillis) / measuredTicks;
            }
            lastTickNanos = now;
        });
        LOGGER.info("LowSpec RenderCore initialized. Player-controlled diagnostics are {}.", CONFIG.diagnosticsEnabled ? "enabled" : "disabled");
    }

    public double getAverageClientTickMillis() { return averageTickMillis; }
    public long getMeasuredTicks() { return measuredTicks; }
    public long getLastTickNanos() { return lastTickNanos; }
}
