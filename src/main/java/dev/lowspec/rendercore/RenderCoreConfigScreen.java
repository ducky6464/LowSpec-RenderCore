package dev.lowspec.rendercore;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.IOException;

public final class RenderCoreConfigScreen extends Screen {
    private static final String[] CATEGORIES = {
            "Players", "Hostile mobs", "Passive mobs", "Villagers",
            "Dropped items", "Projectiles", "Experience orbs", "Armor stands"
    };
    private static final String[] KEYS = {
            "players", "hostile", "passive", "villagers",
            "items", "projectiles", "orbs", "armorstands"
    };
    private final Screen parent;

    public RenderCoreConfigScreen(Screen parent) {
        super(Text.literal("LowSpec RenderCore Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int rowStart = 54;
        for (int i = 0; i < CATEGORIES.length; i++) {
            final int index = i;
            int y = rowStart + i * 25;
            addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> adjustDistance(index, -16))
                    .dimensions(center + 45, y, 28, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> adjustDistance(index, 16))
                    .dimensions(center + 112, y, 28, 20).build());
        }

        int toggleY = rowStart + CATEGORIES.length * 25 + 8;
        addDrawableChild(ButtonWidget.builder(cullingText(), button -> {
            LowSpecRenderCoreClient.CONFIG.entityDistanceCullingEnabled =
                    !LowSpecRenderCoreClient.CONFIG.entityDistanceCullingEnabled;
            saveConfig();
            button.setMessage(cullingText());
        }).dimensions(center - 145, toggleY, 140, 20).build());
        addDrawableChild(ButtonWidget.builder(diagnosticsText(), button -> {
            LowSpecRenderCoreClient.CONFIG.diagnosticsEnabled =
                    !LowSpecRenderCoreClient.CONFIG.diagnosticsEnabled;
            saveConfig();
            button.setMessage(diagnosticsText());
        }).dimensions(center + 5, toggleY, 140, 20).build());

        addDrawableChild(ButtonWidget.builder(lightingText(), button -> {
            LowSpecRenderCoreClient.CONFIG.lightingDiagnosticsEnabled =
                    !LowSpecRenderCoreClient.CONFIG.lightingDiagnosticsEnabled;
            saveConfig();
            button.setMessage(lightingText());
        }).dimensions(center - 145, toggleY + 25, 140, 20).build());
        addDrawableChild(ButtonWidget.builder(chunkText(), button -> {
            LowSpecRenderCoreClient.CONFIG.chunkDiagnosticsEnabled =
                    !LowSpecRenderCoreClient.CONFIG.chunkDiagnosticsEnabled;
            saveConfig();
            button.setMessage(chunkText());
        }).dimensions(center + 5, toggleY + 25, 140, 20).build());

        int particleY = toggleY + 50;
        addDrawableChild(ButtonWidget.builder(particleText(), button -> {
            LowSpecRenderCoreClient.CONFIG.particleBudgetEnabled = !LowSpecRenderCoreClient.CONFIG.particleBudgetEnabled;
            saveConfig();
            button.setMessage(particleText());
        }).dimensions(center - 145, particleY, 140, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> adjustParticleBudget(-50))
                .dimensions(center + 5, particleY, 28, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> adjustParticleBudget(50))
                .dimensions(center + 112, particleY, 28, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
                .dimensions(center - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 18, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Entity render distance (blocks)"), width / 2, 38, 0xB8C8D8);
        int center = width / 2;
        int rowStart = 60;
        for (int i = 0; i < CATEGORIES.length; i++) {
            int y = rowStart + i * 25 + 6;
            context.drawTextWithShadow(textRenderer, Text.literal(CATEGORIES[i]), center - 145, y, 0xFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(Integer.toString(getDistance(i))), center + 91, y, 0xE0E0E0);
        }
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Particle budget: " + LowSpecRenderCoreClient.CONFIG.maxParticlesPerTick + " admissions / tick"),
                width / 2 + 55, rowStart + CATEGORIES.length * 25 + 64, 0xE0E0E0);
        super.render(context, mouseX, mouseY, delta);
    }

    private void adjustDistance(int index, int amount) {
        RenderCoreConfig config = LowSpecRenderCoreClient.CONFIG;
        int current = getDistance(index);
        int min = index < 4 ? 16 : 8;
        int max = index == 0 ? 512 : index < 4 ? 256 : 128;
        setDistance(index, Math.max(min, Math.min(max, current + amount)));
        saveConfig();
    }

    private int getDistance(int index) {
        RenderCoreConfig c = LowSpecRenderCoreClient.CONFIG;
        return switch (index) {
            case 0 -> c.playerRenderDistance;
            case 1 -> c.hostileMobRenderDistance;
            case 2 -> c.passiveMobRenderDistance;
            case 3 -> c.villagerRenderDistance;
            case 4 -> c.droppedItemRenderDistance;
            case 5 -> c.projectileRenderDistance;
            case 6 -> c.experienceOrbRenderDistance;
            default -> c.armorStandRenderDistance;
        };
    }

    private void setDistance(int index, int value) {
        RenderCoreConfig c = LowSpecRenderCoreClient.CONFIG;
        switch (index) {
            case 0 -> c.playerRenderDistance = value;
            case 1 -> c.hostileMobRenderDistance = value;
            case 2 -> c.passiveMobRenderDistance = value;
            case 3 -> c.villagerRenderDistance = value;
            case 4 -> c.droppedItemRenderDistance = value;
            case 5 -> c.projectileRenderDistance = value;
            case 6 -> c.experienceOrbRenderDistance = value;
            default -> c.armorStandRenderDistance = value;
        }
    }

    private void adjustParticleBudget(int amount) {
        RenderCoreConfig c = LowSpecRenderCoreClient.CONFIG;
        c.maxParticlesPerTick = Math.max(50, Math.min(2000, c.maxParticlesPerTick + amount));
        saveConfig();
    }

    private Text particleText() {
        return Text.literal("Particle limit: " + (LowSpecRenderCoreClient.CONFIG.particleBudgetEnabled ? "ON" : "OFF"));
    }

    private Text cullingText() {
        return Text.literal("Entity culling: " + (LowSpecRenderCoreClient.CONFIG.entityDistanceCullingEnabled ? "ON" : "OFF"));
    }

    private Text diagnosticsText() {
        return Text.literal("Diagnostics: " + (LowSpecRenderCoreClient.CONFIG.diagnosticsEnabled ? "ON" : "OFF"));
    }

    private Text lightingText() {
        return Text.literal("Lighting timing: " + (LowSpecRenderCoreClient.CONFIG.lightingDiagnosticsEnabled ? "ON" : "OFF"));
    }

    private Text chunkText() {
        return Text.literal("Chunk diagnostics: " + (LowSpecRenderCoreClient.CONFIG.chunkDiagnosticsEnabled ? "ON" : "OFF"));
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
