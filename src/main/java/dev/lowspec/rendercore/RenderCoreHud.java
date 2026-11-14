package dev.lowspec.rendercore;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
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
            LightingMetrics.Snapshot light = LightingMetrics.snapshot();
            int x = 6;
            int y = 6;
            int color = 0xE8E8E8;
            context.drawTextWithShadow(client.textRenderer, Text.literal("LowSpec RenderCore"), x, y, 0x7FE7FF);
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal(String.format(java.util.Locale.ROOT, "Client tick: %.2f ms", LowSpecRenderCoreClient.getAverageClientTickMillis())),
                    x, y + 11, color);
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal(String.format(java.util.Locale.ROOT, "CPU render: %.2f ms avg / %.2f ms p95", frame.averageRenderMillis(), frame.p95RenderMillis())),
                    x, y + 22, color);
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal(String.format(java.util.Locale.ROOT, "Lighting: %.3f ms/call", light.averageMillisPerCall())),
                    x, y + 33, color);
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal("Particles admitted this tick: " + ParticleBudget.getAdmittedThisTick()),
                    x, y + 44, color);
        });
    }
}
