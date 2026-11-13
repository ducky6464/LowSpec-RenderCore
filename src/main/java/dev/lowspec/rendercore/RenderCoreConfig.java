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
    public boolean diagnosticsEnabled = true;
    public boolean lightingDiagnosticsEnabled = true;
    public boolean chunkDiagnosticsEnabled = true;
    public int playerRenderDistance = 128;
    public int hostileMobRenderDistance = 96;
    public int passiveMobRenderDistance = 96;
    public int droppedItemRenderDistance = 48;

    public static RenderCoreConfig load(Path configDir) {
        RenderCoreConfig config = new RenderCoreConfig();
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) return config;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
            config.diagnosticsEnabled = Boolean.parseBoolean(p.getProperty("diagnostics.enabled", "true"));
            config.lightingDiagnosticsEnabled = Boolean.parseBoolean(p.getProperty("diagnostics.lighting", "true"));
            config.chunkDiagnosticsEnabled = Boolean.parseBoolean(p.getProperty("diagnostics.chunks", "true"));
            config.playerRenderDistance = bounded(p.getProperty("render.players", "128"), 128, 16, 512);
            config.hostileMobRenderDistance = bounded(p.getProperty("render.hostile_mobs", "96"), 96, 16, 256);
            config.passiveMobRenderDistance = bounded(p.getProperty("render.passive_mobs", "96"), 96, 16, 256);
            config.droppedItemRenderDistance = bounded(p.getProperty("render.dropped_items", "48"), 48, 8, 128);
        } catch (IOException ignored) { }
        return config;
    }

    public void save(Path configDir) throws IOException {
        Files.createDirectories(configDir);
        Properties p = new Properties();
        p.setProperty("diagnostics.enabled", Boolean.toString(diagnosticsEnabled));
        p.setProperty("diagnostics.lighting", Boolean.toString(lightingDiagnosticsEnabled));
        p.setProperty("diagnostics.chunks", Boolean.toString(chunkDiagnosticsEnabled));
        p.setProperty("render.players", Integer.toString(playerRenderDistance));
        p.setProperty("render.hostile_mobs", Integer.toString(hostileMobRenderDistance));
        p.setProperty("render.passive_mobs", Integer.toString(passiveMobRenderDistance));
        p.setProperty("render.dropped_items", Integer.toString(droppedItemRenderDistance));
        try (OutputStream out = Files.newOutputStream(configDir.resolve(FILE_NAME))) {
            p.store(out, "LowSpec RenderCore settings. Values are controlled by the player.");
        }
    }

    private static int bounded(String value, int fallback, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(value))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
