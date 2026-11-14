package dev.lowspec.rendercore;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
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
    private static volatile double lastClientTickMillis;
    private long tickStartNanos;

    @Override
    public void onInitializeClient() {
        CONFIG = RenderCoreConfig.load(FabricLoader.getInstance().getConfigDir());
        RenderCoreHud.register();
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            tickStartNanos = System.nanoTime();
            ParticleBudget.resetForNewTick();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long elapsedTickNanos = tickStartNanos == 0L ? 0L : System.nanoTime() - tickStartNanos;
            if (client.world != null) {
                int rebuildBudget = CONFIG.chunkSchedulingEnabled
                        ? ChunkRenderScheduler.adaptiveBudget(CONFIG.chunkRebuildsPerTick, elapsedTickNanos)
                        : 4096;
                ChunkRenderScheduler.drain(client.worldRenderer, client.world, rebuildBudget, tickStartNanos);
            } else {
                ChunkRenderScheduler.clear();
            }
            if (tickStartNanos == 0L) return;
            double elapsedMs = elapsedTickNanos / 1_000_000.0;
            lastClientTickMillis = elapsedMs;
            if (!CONFIG.diagnosticsEnabled) return;
            measuredTicks++;
            averageTickMillis += (elapsedMs - averageTickMillis) / measuredTicks;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("rendercore")
                        .then(ClientCommandManager.literal("stats").executes(context -> {
                            LightingMetrics.Snapshot light = getLightingMetrics();
                            ChunkUpdateMetrics.Snapshot chunks = ChunkUpdateMetrics.snapshot();
                            context.getSource().sendFeedback(Text.literal(
                                    "LowSpec RenderCore | Average client tick: "
                                            + String.format(java.util.Locale.ROOT, "%.2f ms", getAverageClientTickMillis())
                                            + " | Samples: " + getMeasuredTicks()));
                            RenderFrameMetrics.Snapshot frame = RenderFrameMetrics.snapshot();
                            context.getSource().sendFeedback(Text.literal(
                                    "CPU render time: "
                                            + String.format(java.util.Locale.ROOT, "%.2f ms avg / %.2f ms p95",
                                                    frame.averageRenderMillis(), frame.p95RenderMillis())
                                            + " | Samples: " + frame.samples()));
                            context.getSource().sendFeedback(Text.literal(
                                    "Lighting calls: " + light.calls()
                                            + " | Average call: "
                                            + String.format(java.util.Locale.ROOT, "%.3f ms", light.averageMillisPerCall())
                                            + " | Vanilla updates reported: " + light.reportedUpdates()));
                            context.getSource().sendFeedback(Text.literal(
                                    "Chunk update CPU time: "
                                            + String.format(java.util.Locale.ROOT, "%.3f ms avg / %.3f ms p95", chunks.averageMillis(), chunks.p95Millis())
                                            + " | Samples: " + chunks.samples()));
                            ChunkMeshMetrics.Snapshot mesh = ChunkMeshMetrics.snapshot();
                            context.getSource().sendFeedback(Text.literal(
                                    "Chunk mesh CPU time: "
                                            + String.format(java.util.Locale.ROOT, "%.3f ms avg / %.3f ms p95", mesh.averageMillis(), mesh.p95Millis())
                                            + " | Samples: " + mesh.samples()));
                            context.getSource().sendFeedback(Text.literal(
                                    "Paced chunk rebuild requests: " + ChunkRenderScheduler.pendingCount()
                                            + " | Scheduler: " + (CONFIG.chunkSchedulingEnabled ? "ON" : "OFF")
                                            + " | Budget ceiling: " + CONFIG.chunkRebuildsPerTick + "/tick"));
                            context.getSource().sendFeedback(Text.literal(
                                    "Chunk uploads: " + ChunkUploadBudget.getLastEffectiveLimit()
                                            + "/" + CONFIG.chunkUploadsPerFrame + " per frame (adaptive)"));
                            context.getSource().sendFeedback(Text.literal(
                                    "Entity distance culling: "
                                            + (CONFIG.entityDistanceCullingEnabled ? "ON" : "OFF")));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("culling")
                                .then(ClientCommandManager.literal("on").executes(context -> setCulling(context.getSource(), true)))
                                .then(ClientCommandManager.literal("off").executes(context -> setCulling(context.getSource(), false))))
                        .then(ClientCommandManager.literal("distance")
                                .then(distanceCommand("players"))
                                .then(distanceCommand("hostile"))
                                .then(distanceCommand("passive"))
                                .then(distanceCommand("villagers"))
                                .then(distanceCommand("items"))
                                .then(distanceCommand("projectiles"))
                                .then(distanceCommand("orbs"))
                                .then(distanceCommand("armorstands")))
                ));

        LOGGER.info("LowSpec RenderCore initialized. Diagnostics: {}. Lighting timing: {}. Entity culling: {}.",
                CONFIG.diagnosticsEnabled ? "on" : "off",
                CONFIG.lightingDiagnosticsEnabled ? "on" : "off",
                CONFIG.entityDistanceCullingEnabled ? "on" : "off");
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> distanceCommand(String category) {
        return ClientCommandManager.literal(category)
                .then(ClientCommandManager.argument("blocks", IntegerArgumentType.integer(8, 512))
                        .executes(context -> setDistance(context.getSource(), category,
                                IntegerArgumentType.getInteger(context, "blocks"))));
    }

    private static int setDistance(FabricClientCommandSource source, String category, int requested) {
        int min = category.equals("players") || category.equals("hostile")
                || category.equals("passive") || category.equals("villagers") ? 16 : 8;
        int max = category.equals("players") ? 512
                : category.equals("hostile") || category.equals("passive") || category.equals("villagers") ? 256 : 128;
        int value = Math.max(min, Math.min(max, requested));
        switch (category) {
            case "players" -> CONFIG.playerRenderDistance = value;
            case "hostile" -> CONFIG.hostileMobRenderDistance = value;
            case "passive" -> CONFIG.passiveMobRenderDistance = value;
            case "villagers" -> CONFIG.villagerRenderDistance = value;
            case "items" -> CONFIG.droppedItemRenderDistance = value;
            case "projectiles" -> CONFIG.projectileRenderDistance = value;
            case "orbs" -> CONFIG.experienceOrbRenderDistance = value;
            case "armorstands" -> CONFIG.armorStandRenderDistance = value;
            default -> {
                source.sendFeedback(Text.literal("Unknown entity category."));
                return 0;
            }
        }
        try {
            CONFIG.save(FabricLoader.getInstance().getConfigDir());
            source.sendFeedback(Text.literal("Render distance for " + category + " set to " + value + " blocks."));
        } catch (IOException exception) {
            source.sendFeedback(Text.literal("Could not save LowSpec RenderCore settings: " + exception.getMessage()));
        }
        return 1;
    }

    private static int setCulling(FabricClientCommandSource source, boolean enabled) {
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
    public static double getLastClientTickMillis() { return lastClientTickMillis; }
    public static long getMeasuredTicks() { return measuredTicks; }
    public static LightingMetrics.Snapshot getLightingMetrics() { return LightingMetrics.snapshot(); }
}
