package dev.lowspec.rendercore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Player-owned settings. Nothing is changed automatically. */
public final class RenderCoreConfig {
    private static final String FILE_NAME = "lowspec-rendercore.properties";
    public boolean diagnosticsEnabled = false;
    public boolean lightingDiagnosticsEnabled = false;
    public boolean lightingUpdatePacingEnabled = true;
    public boolean chunkDiagnosticsEnabled = false;
    public boolean chunkSchedulingEnabled = true;
    public int chunkRebuildsPerTick = 128;
    public boolean chunkUploadPacingEnabled = true;
    public int chunkUploadsPerFrame = 8;
    public boolean entityDistanceCullingEnabled = true;
    public boolean particleBudgetEnabled = true;
    public int maxParticlesPerTick = 400;
    public boolean performanceHudEnabled = false;
    public boolean blockEntityDistanceCullingEnabled = true;
    public int blockEntityRenderDistance = 96;
    public int playerRenderDistance = 128;
    public int hostileMobRenderDistance = 96;
    public int passiveMobRenderDistance = 96;
    public int villagerRenderDistance = 64;
    public int droppedItemRenderDistance = 48;
    public int projectileRenderDistance = 48;
    public int experienceOrbRenderDistance = 32;
    public int armorStandRenderDistance = 64;

    public static RenderCoreConfig load(Path configDir) {
        RenderCoreConfig config = new RenderCoreConfig();
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            try { config.save(configDir); } catch (IOException ignored) { }
            return config;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            config.diagnosticsEnabled = Boolean.parseBoolean(p.getProperty("diagnostics.enabled", "false"));
            config.lightingDiagnosticsEnabled = Boolean.parseBoolean(p.getProperty("diagnostics.lighting", "false"));
            config.lightingUpdatePacingEnabled = Boolean.parseBoolean(p.getProperty("lighting.pacing_enabled", "true"));
            config.chunkDiagnosticsEnabled = Boolean.parseBoolean(p.getProperty("diagnostics.chunks", "false"));
            config.chunkSchedulingEnabled = Boolean.parseBoolean(p.getProperty("chunks.scheduler_enabled", "true"));
            config.chunkRebuildsPerTick = bounded(p.getProperty("chunks.rebuilds_per_tick", "128"), 128, 16, 512);
            config.chunkUploadPacingEnabled = Boolean.parseBoolean(p.getProperty("chunks.upload_pacing_enabled", "true"));
            config.chunkUploadsPerFrame = bounded(p.getProperty("chunks.uploads_per_frame", "8"), 8, 1, 64);
            config.entityDistanceCullingEnabled = Boolean.parseBoolean(p.getProperty("render.entity_distance_culling", "true"));
            config.particleBudgetEnabled = Boolean.parseBoolean(p.getProperty("particles.budget_enabled", "true"));
            config.maxParticlesPerTick = bounded(p.getProperty("particles.max_per_tick", "400"), 400, 50, 2000);
            config.performanceHudEnabled = Boolean.parseBoolean(p.getProperty("hud.performance_enabled", "false"));
            config.blockEntityDistanceCullingEnabled = Boolean.parseBoolean(p.getProperty("render.block_entity_culling", "true"));
            config.blockEntityRenderDistance = bounded(p.getProperty("render.block_entities", "96"), 96, 16, 256);
            config.playerRenderDistance = bounded(p.getProperty("render.players", "128"), 128, 16, 512);
            config.hostileMobRenderDistance = bounded(p.getProperty("render.hostile_mobs", "96"), 96, 16, 256);
            config.passiveMobRenderDistance = bounded(p.getProperty("render.passive_mobs", "96"), 96, 16, 256);
            config.villagerRenderDistance = bounded(p.getProperty("render.villagers", "64"), 64, 16, 256);
            config.droppedItemRenderDistance = bounded(p.getProperty("render.dropped_items", "48"), 48, 8, 128);
            config.projectileRenderDistance = bounded(p.getProperty("render.projectiles", "48"), 48, 8, 128);
            config.experienceOrbRenderDistance = bounded(p.getProperty("render.experience_orbs", "32"), 32, 8, 128);
            config.armorStandRenderDistance = bounded(p.getProperty("render.armor_stands", "64"), 64, 8, 128);
        } catch (IOException ignored) { }
        return config;
    }

    public void save(Path configDir) throws IOException {
        Files.createDirectories(configDir);
        Properties p = new Properties();
        p.setProperty("diagnostics.enabled", Boolean.toString(diagnosticsEnabled));
        p.setProperty("diagnostics.lighting", Boolean.toString(lightingDiagnosticsEnabled));
        p.setProperty("lighting.pacing_enabled", Boolean.toString(lightingUpdatePacingEnabled));
        p.setProperty("diagnostics.chunks", Boolean.toString(chunkDiagnosticsEnabled));
        p.setProperty("chunks.scheduler_enabled", Boolean.toString(chunkSchedulingEnabled));
        p.setProperty("chunks.rebuilds_per_tick", Integer.toString(chunkRebuildsPerTick));
        p.setProperty("chunks.upload_pacing_enabled", Boolean.toString(chunkUploadPacingEnabled));
        p.setProperty("chunks.uploads_per_frame", Integer.toString(chunkUploadsPerFrame));
        p.setProperty("render.entity_distance_culling", Boolean.toString(entityDistanceCullingEnabled));
        p.setProperty("particles.budget_enabled", Boolean.toString(particleBudgetEnabled));
        p.setProperty("particles.max_per_tick", Integer.toString(maxParticlesPerTick));
        p.setProperty("hud.performance_enabled", Boolean.toString(performanceHudEnabled));
        p.setProperty("render.block_entity_culling", Boolean.toString(blockEntityDistanceCullingEnabled));
        p.setProperty("render.block_entities", Integer.toString(blockEntityRenderDistance));
        p.setProperty("render.players", Integer.toString(playerRenderDistance));
        p.setProperty("render.hostile_mobs", Integer.toString(hostileMobRenderDistance));
        p.setProperty("render.passive_mobs", Integer.toString(passiveMobRenderDistance));
        p.setProperty("render.villagers", Integer.toString(villagerRenderDistance));
        p.setProperty("render.dropped_items", Integer.toString(droppedItemRenderDistance));
        p.setProperty("render.projectiles", Integer.toString(projectileRenderDistance));
        p.setProperty("render.experience_orbs", Integer.toString(experienceOrbRenderDistance));
        p.setProperty("render.armor_stands", Integer.toString(armorStandRenderDistance));
        try (OutputStream out = Files.newOutputStream(configDir.resolve(FILE_NAME))) {
            p.store(out, "LowSpec RenderCore settings. Values are controlled by the player.");
        }
    }

    private static int bounded(String value, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(value))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
