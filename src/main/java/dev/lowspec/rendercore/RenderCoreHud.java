package dev.lowspec.rendercore;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Lightweight, opt-in diagnostics overlay. Values are CPU-side measurements. */
public final class RenderCoreHud {
    private RenderCoreHud() { }

    public static void register() {
        HudElementRegistry.addLast(Identifier.of(LowSpecRenderCoreClient.MOD_ID, "performance_hud"), (context, tickCounter) -> {
            RenderCoreConfig config = LowSpecRenderCoreClient.CONFIG;
            MinecraftClient client = MinecraftClient.getInstance();
            if (config == null || !config.performanceHudEnabled || !config.diagnosticsEnabled
                    || client.player == null || client.world == null) return;

            RenderFrameMetrics.Snapshot frame = RenderFrameMetrics.snapshot();
            ChunkBuilder builder = client.worldRenderer.getChunkBuilder();
            int x = 6;
            int y = 6;
            int color = 0xE8E8E8;
            context.drawTextWithShadow(client.textRenderer, Text.literal("LowSpec RenderCore"), x, y, 0x7FE7FF);
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal(String.format(java.util.Locale.ROOT, "Client tick: %.2f ms", LowSpecRenderCoreClient.getAverageClientTickMillis())),
                    x, y + 11, color);
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal(String.format(java.util.Locale.ROOT, "CPU render: %.2f avg / %.2f p95 ms", frame.averageRenderMillis(), frame.p95RenderMillis())),
                    x, y + 22, color);

            String lightingText = "Lighting timing: disabled";
            if (config.lightingDiagnosticsEnabled) {
                LightingMetrics.Snapshot light = LightingMetrics.snapshot();
                lightingText = String.format(java.util.Locale.ROOT, "Lighting: %.3f ms/call", light.averageMillisPerCall());
            }
            context.drawTextWithShadow(client.textRenderer, Text.literal(lightingText), x, y + 33, color);

            if (config.chunkDiagnosticsEnabled) {
                ChunkUpdateMetrics.Snapshot chunks = ChunkUpdateMetrics.snapshot();
                context.drawTextWithShadow(client.textRenderer,
                        Text.literal(String.format(java.util.Locale.ROOT, "Chunk update: %.3f avg / %.3f p95 ms", chunks.averageMillis(), chunks.p95Millis())),
                        x, y + 44, color);
                context.drawTextWithShadow(client.textRenderer,
                        Text.literal("Chunk tasks: " + builder.getScheduledTaskCount()
                                + " | uploads: " + builder.getChunksToUpload()
                                + " | free buffers: " + builder.getFreeBufferCount()),
                        x, y + 55, color);
                ChunkMeshMetrics.Snapshot mesh = ChunkMeshMetrics.snapshot();
                context.drawTextWithShadow(client.textRenderer,
                        Text.literal(String.format(java.util.Locale.ROOT,
                                "Chunk mesh CPU: %.2f avg / %.2f p95 ms", mesh.averageMillis(), mesh.p95Millis())),
                        x, y + 66, color);
            } else {
                context.drawTextWithShadow(client.textRenderer, Text.literal("Chunk diagnostics: disabled"), x, y + 44, color);
            }

            context.drawTextWithShadow(client.textRenderer,
                    Text.literal("Chunk upload cap: " + ChunkUploadBudget.getLastEffectiveLimit()
                            + " / frame (adaptive)"),
                    x, y + 77, color);
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal("Chunk mesh state cache: " + (config.chunkMeshStateCacheEnabled ? "ON" : "OFF")),
                    x, y + 88, color);
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal("Particles admitted: " + (config.particleBudgetEnabled
                            ? ParticleBudget.getAdmittedThisTick() : "limiter off")),
                    x, y + 99, color);
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal("GPU buffer pool: " + ChunkGpuBufferPool.getReuseHits()
                            + " reuses | " + String.format(java.util.Locale.ROOT, "%.1f MiB",
                                    ChunkGpuBufferPool.getPooledBytes() / 1048576.0)),
                    x, y + 110, color);
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal("Lighting state cache: " + (config.lightingStateCacheEnabled ? "ON" : "OFF")),
                    x, y + 121, color);
        });
    }
}
