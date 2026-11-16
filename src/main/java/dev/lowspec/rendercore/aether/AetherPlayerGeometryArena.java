package dev.lowspec.rendercore.aether;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;

import java.util.Arrays;
import java.util.Objects;

/**
 * Fixed-capacity per-frame arena for captured player base-model geometry.
 * Mesh data is copied from a reusable scratch extractor into one contiguous
 * float array so later per-skin batching can reference stable player ranges.
 */
public final class AetherPlayerGeometryArena {
    private final int maxPlayers;
    private final float[] vertexData;
    private final int[] offsets, floatCounts, vertexCounts;
    private final AetherModelGeometryBuffer scratch;
    private int capturedPlayers;
    private int usedFloats;
    private long overflowCount;
    private long rejectedCount;

    public AetherPlayerGeometryArena(int maxPlayers, int maxQuadsPerPlayer, int totalFloatCapacity) {
        if (maxPlayers < 1 || maxQuadsPerPlayer < 1 || totalFloatCapacity < 1) {
            throw new IllegalArgumentException("all arena capacities must be positive");
        }
        this.maxPlayers = maxPlayers;
        this.vertexData = new float[totalFloatCapacity];
        this.offsets = new int[maxPlayers];
        this.floatCounts = new int[maxPlayers];
        this.vertexCounts = new int[maxPlayers];
        this.scratch = new AetherModelGeometryBuffer(maxQuadsPerPlayer);
        beginFrame();
    }

    public void beginFrame() {
        Arrays.fill(offsets, -1);
        Arrays.fill(floatCounts, 0);
        Arrays.fill(vertexCounts, 0);
        capturedPlayers = 0;
        usedFloats = 0;
    }

    /** Caller must invoke on Minecraft's render thread. */
    public boolean capture(int playerIndex, ModelPart baseModelRoot, MatrixStack matrices) {
        Objects.requireNonNull(baseModelRoot, "baseModelRoot");
        Objects.requireNonNull(matrices, "matrices");
        if (playerIndex < 0 || playerIndex >= maxPlayers) {
            rejectedCount++;
            return false;
        }
        if (offsets[playerIndex] >= 0) {
            rejectedCount++;
            return false;
        }
        if (!scratch.capture(baseModelRoot, matrices)) {
            overflowCount++;
            return false;
        }
        int floats = scratch.usedFloats();
        if (floats == 0 || floats > vertexData.length - usedFloats) {
            overflowCount++;
            return false;
        }
        offsets[playerIndex] = usedFloats;
        floatCounts[playerIndex] = floats;
        vertexCounts[playerIndex] = scratch.vertexCount();
        System.arraycopy(scratch.data(), 0, vertexData, usedFloats, floats);
        usedFloats += floats;
        capturedPlayers++;
        return true;
    }

    public boolean captureOnRenderThread(int playerIndex, ModelPart baseModelRoot, MatrixStack matrices) {
        AetherRenderThread.assertCurrent();
        return capture(playerIndex, baseModelRoot, matrices);
    }

    public int maxPlayers() { return maxPlayers; }
    public int capturedPlayers() { return capturedPlayers; }
    public int usedFloats() { return usedFloats; }
    public int capacityFloats() { return vertexData.length; }
    public long overflowCount() { return overflowCount; }
    public long rejectedCount() { return rejectedCount; }
    public float[] data() { return vertexData; }
    /** Backing metadata for render-thread draw-plan construction; do not mutate. */
    public int[] offsets() { return offsets; }
    public int[] floatCounts() { return floatCounts; }
    public int[] vertexCounts() { return vertexCounts; }

    public int offset(int playerIndex) { checkPlayer(playerIndex); return offsets[playerIndex]; }
    public int floatCount(int playerIndex) { checkPlayer(playerIndex); return floatCounts[playerIndex]; }
    public int vertexCount(int playerIndex) { checkPlayer(playerIndex); return vertexCounts[playerIndex]; }
    public boolean hasGeometry(int playerIndex) { checkPlayer(playerIndex); return offsets[playerIndex] >= 0; }

    private void checkPlayer(int playerIndex) {
        if (playerIndex < 0 || playerIndex >= maxPlayers) {
            throw new IndexOutOfBoundsException("player " + playerIndex + " of " + maxPlayers);
        }
    }
}
