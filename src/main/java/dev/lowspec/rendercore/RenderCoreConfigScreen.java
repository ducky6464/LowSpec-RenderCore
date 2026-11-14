package dev.lowspec.rendercore;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class RenderCoreConfigScreen extends Screen {
    private static final String[] PAGES = {"Entities", "Other", "Chunks", "Lighting", "Diagnostics"};
    private final Screen parent;
    private int page;
    private final List<RowLabel> rowLabels = new ArrayList<>();
    private final List<NumericValue> numericValues = new ArrayList<>();

    private record RowLabel(String text, int y) {}
    private record NumericValue(IntSupplier getter, int x, int y) {}

    public RenderCoreConfigScreen(Screen parent) {
        super(Text.literal("LowSpec RenderCore Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rowLabels.clear();
        numericValues.clear();
        int center = width / 2;
        int tabWidth = Math.min(82, Math.max(60, (width - 20) / PAGES.length));
        int totalWidth = tabWidth * PAGES.length;
        int tabX = center - totalWidth / 2;
        for (int i = 0; i < PAGES.length; i++) {
            final int selected = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(PAGES[i]), b -> {
                page = selected;
                clearAndInit();
            }).dimensions(tabX + i * tabWidth, 43, tabWidth - 3, 20).build());
        }

        int y = 82;
        switch (page) {
            case 0 -> buildEntityPage(y);
            case 1 -> buildOtherPage(y);
            case 2 -> buildChunkPage(y);
            case 3 -> buildLightingPage(y);
            default -> buildDiagnosticsPage(y);
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(center - 70, height - 28, 140, 20).build());
    }

    private void buildEntityPage(int y) {
        RenderCoreConfig c = LowSpecRenderCoreClient.CONFIG;
        toggle("Entity distance culling", y, () -> c.entityDistanceCullingEnabled,
                v -> c.entityDistanceCullingEnabled = v); y += 27;
        adjust("Players distance", y, () -> c.playerRenderDistance, v -> c.playerRenderDistance = v, 16, 16, 512); y += 27;
        adjust("Hostile mob distance", y, () -> c.hostileMobRenderDistance, v -> c.hostileMobRenderDistance = v, 16, 16, 256); y += 27;
        adjust("Passive mob distance", y, () -> c.passiveMobRenderDistance, v -> c.passiveMobRenderDistance = v, 16, 16, 256); y += 27;
        adjust("Villager distance", y, () -> c.villagerRenderDistance, v -> c.villagerRenderDistance = v, 16, 16, 256); y += 27;
        adjust("Dropped item distance", y, () -> c.droppedItemRenderDistance, v -> c.droppedItemRenderDistance = v, 16, 8, 128); y += 27;
        adjust("Projectile distance", y, () -> c.projectileRenderDistance, v -> c.projectileRenderDistance = v, 16, 8, 128); y += 27;
        adjust("Experience orb distance", y, () -> c.experienceOrbRenderDistance, v -> c.experienceOrbRenderDistance = v, 16, 8, 128); y += 27;
        adjust("Armor stand distance", y, () -> c.armorStandRenderDistance, v -> c.armorStandRenderDistance = v, 16, 8, 128);
    }

    private void buildOtherPage(int y) {
        RenderCoreConfig c = LowSpecRenderCoreClient.CONFIG;
        toggle("Block entity culling", y, () -> c.blockEntityDistanceCullingEnabled,
                v -> c.blockEntityDistanceCullingEnabled = v); y += 30;
        adjust("Block entity distance", y, () -> c.blockEntityRenderDistance,
                v -> c.blockEntityRenderDistance = v, 16, 16, 256); y += 30;
        toggle("Particle budget", y, () -> c.particleBudgetEnabled, v -> c.particleBudgetEnabled = v); y += 30;
        adjust("Particle admissions / tick", y, () -> c.maxParticlesPerTick,
                v -> c.maxParticlesPerTick = v, 50, 50, 2000);
    }

    private void buildChunkPage(int y) {
        RenderCoreConfig c = LowSpecRenderCoreClient.CONFIG;
        toggle("Chunk rebuild pacing", y, () -> c.chunkSchedulingEnabled, v -> c.chunkSchedulingEnabled = v); y += 30;
        adjust("Rebuilds / tick", y, () -> c.chunkRebuildsPerTick, v -> c.chunkRebuildsPerTick = v, 16, 16, 512); y += 30;
        toggle("Chunk upload pacing", y, () -> c.chunkUploadPacingEnabled, v -> c.chunkUploadPacingEnabled = v); y += 30;
        adjust("Uploads / frame", y, () -> c.chunkUploadsPerFrame, v -> c.chunkUploadsPerFrame = v, 1, 1, 64); y += 30;
        toggle("Chunk state cache", y, () -> c.chunkMeshStateCacheEnabled, v -> c.chunkMeshStateCacheEnabled = v); y += 30;
        toggle("Model parts cache", y, () -> c.chunkMeshModelPartsCacheEnabled, v -> c.chunkMeshModelPartsCacheEnabled = v); y += 30;
        toggle("GPU buffer pool", y, () -> c.chunkGpuBufferPoolEnabled, v -> c.chunkGpuBufferPoolEnabled = v);
    }

    private void buildLightingPage(int y) {
        RenderCoreConfig c = LowSpecRenderCoreClient.CONFIG;
        toggle("Lighting update pacing", y, () -> c.lightingUpdatePacingEnabled, v -> c.lightingUpdatePacingEnabled = v); y += 30;
        toggle("Lighting state cache", y, () -> c.lightingStateCacheEnabled, v -> c.lightingStateCacheEnabled = v); y += 30;
        toggle("Lighting shape cache", y, () -> c.lightingShapeCacheEnabled, v -> c.lightingShapeCacheEnabled = v); y += 30;
        toggle("Lighting timing diagnostics", y, () -> c.lightingDiagnosticsEnabled, v -> c.lightingDiagnosticsEnabled = v);
    }

    private void buildDiagnosticsPage(int y) {
        RenderCoreConfig c = LowSpecRenderCoreClient.CONFIG;
        toggle("General diagnostics", y, () -> c.diagnosticsEnabled, v -> c.diagnosticsEnabled = v); y += 30;
        toggle("Chunk diagnostics", y, () -> c.chunkDiagnosticsEnabled, v -> c.chunkDiagnosticsEnabled = v); y += 30;
        toggle("Performance HUD", y, () -> c.performanceHudEnabled, v -> c.performanceHudEnabled = v);
    }

    private void toggle(String label, int y, BooleanSupplier getter, Consumer<Boolean> setter) {
        addRowLabel(label, y);
        addDrawableChild(ButtonWidget.builder(toggleText(getter.getAsBoolean()), b -> {
            setter.accept(!getter.getAsBoolean());
            saveConfig();
            b.setMessage(toggleText(getter.getAsBoolean()));
        }).dimensions(width / 2 + 20, y, Math.min(150, width / 2 - 28), 20).build());
    }

    private void adjust(String label, int y, IntSupplier getter, IntConsumer setter,
                        int step, int min, int max) {
        addRowLabel(label, y);
        int x = width / 2 + 20;
        numericValues.add(new NumericValue(getter, x + 76, y + 6));
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> {
            setter.accept(Math.max(min, getter.getAsInt() - step));
            saveConfig();
        }).dimensions(x, y, 28, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            setter.accept(Math.min(max, getter.getAsInt() + step));
            saveConfig();
        }).dimensions(x + 104, y, 28, 20).build());
    }

    private Text toggleText(boolean enabled) {
        return Text.literal(enabled ? "ON" : "OFF");
    }

    private void addRowLabel(String label, int y) {
        rowLabels.add(new RowLabel(label, y + 6));
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        // Render vanilla widgets first. Draw setting text afterwards so button backgrounds
        // and other widgets cannot cover the labels or numeric values.
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFF);
        int labelX = Math.max(8, width / 2 - 190);
        for (RowLabel row : rowLabels) {
            context.drawTextWithShadow(textRenderer, Text.literal(row.text()), labelX, row.y(), 0xFFFFFF);
        }
        for (NumericValue value : numericValues) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(Integer.toString(value.getter().getAsInt())),
                    value.x(), value.y(), 0xE0E0E0);
        }
    }

    private void saveConfig() {
        try {
            LowSpecRenderCoreClient.CONFIG.save(FabricLoader.getInstance().getConfigDir());
        } catch (IOException exception) {
            LowSpecRenderCoreClient.LOGGER.error("Could not save RenderCore settings", exception);
        }
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
