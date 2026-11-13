package dev.lowspec.rendercore;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("rendercore")
                        .then(ClientCommandManager.literal("stats").executes(context -> {
                            LightingMetrics.Snapshot light = getLightingMetrics();
                            context.getSource().sendFeedback(Text.literal(
                                    "LowSpec RenderCore | Average client tick: "
                                            + String.format(java.util.Locale.ROOT, "%.2f ms", getAverageClientTickMillis())
                                            + " | Samples: " + getMeasuredTicks()));
                            context.getSource().sendFeedback(Text.literal(
                                    "Lighting calls: " + light.calls()
                                            + " | Average call: "
                                            + String.format(java.util.Locale.ROOT, "%.3f ms", light.averageMillisPerCall())
                                            + " | Vanilla updates reported: " + light.reportedUpdates()));
                            context.getSource().sendFeedback(Text.literal(
                                    "Entity distance culling: "
                                            + (CONFIG.entityDistanceCullingEnabled ? "ON" : "OFF")));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("culling")
                                .then(ClientCommandManager.literal("on").executes(context -> setCulling(context.getSource(), true)))
                                .then(ClientCommandManager.literal("off").executes(context -> setCulling(context.getSource(), false))))
                ));

        LOGGER.info("LowSpec RenderCore initialized. Diagnostics: {}. Lighting timing: {}. Entity culling: {}.",
                CONFIG.diagnosticsEnabled ? "on" : "off",
                CONFIG.lightingDiagnosticsEnabled ? "on" : "off",
                CONFIG.entityDistanceCullingEnabled ? "on" : "off");
    }

    private static int setCulling(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, boolean enabled) {
        CONFIG.entityDistanceCullingEnabled = enabled;
        try {
            CONFIG.save(FabricLoader.getInstance().getConfigDir());
            source.sendFeedback(Text.literal("LowSpec RenderCore entity distance culling: " + (enabled ? "ON" : "OFF")));
        } catch (IOException exception) {
            source.sendFeedback(Text.literal("Could not save LowSpec RenderCore settings: " + exception.getMessage()));
        }
        return 1;
    }

    public static double getAverageClientTickMillis() { return averageTickMillis; }
    public static long getMeasuredTicks() { return measuredTicks; }
    public static LightingMetrics.Snapshot getLightingMetrics() { return LightingMetrics.snapshot(); }
}
