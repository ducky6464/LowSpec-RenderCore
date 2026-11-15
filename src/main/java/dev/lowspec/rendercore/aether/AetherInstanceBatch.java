package dev.lowspec.rendercore.aether;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Fixed-capacity CPU staging area for Aether's future instanced entity path.
 *
 * Layout per instance (little-endian): 16 column-major transform floats,
 * one packed RGBA color int, and one texture-array layer int (72 bytes).
 * This class does not issue GPU calls. It is deliberately bounded and
 * render-thread confined until a separate immutable worker-output queue exists.
 */
public final class AetherInstanceBatch {
    public static final int FLOATS_PER_TRANSFORM = 16;
    public static final int INSTANCE_BYTES = FLOATS_PER_TRANSFORM * Float.BYTES + 2 * Integer.BYTES;
    public static final int DEFAULT_CAPACITY = 256;

    private final ByteBuffer staging;
    private final int capacity;
    private int instanceCount;
    private long overflowCount;
    private long totalAccepted;

    public AetherInstanceBatch() {
        this(DEFAULT_CAPACITY);
    }

    public AetherInstanceBatch(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.staging = ByteBuffer.allocateDirect(Math.multiplyExact(capacity, INSTANCE_BYTES))
                .order(ByteOrder.LITTLE_ENDIAN);
    }

    /** Starts a new frame and discards the previous frame's staging data. */
    public void beginFrame() {
        staging.clear();
        instanceCount = 0;
    }

    /**
     * Appends one instance record. The matrix must contain 16 column-major
     * floats. Returns false instead of growing or allocating when full.
     */
    public boolean tryAppend(float[] matrix, int packedColor, int textureLayer) {
        if (matrix == null || matrix.length < FLOATS_PER_TRANSFORM || textureLayer < 0) {
            overflowCount++;
            return false;
        }
        if (instanceCount >= capacity) {
            overflowCount++;
            return false;
        }

        for (int i = 0; i < FLOATS_PER_TRANSFORM; i++) {
            staging.putFloat(matrix[i]);
        }
        staging.putInt(packedColor);
        staging.putInt(textureLayer);
        instanceCount++;
        totalAccepted++;
        return true;
    }

    /** Read-only view of the current frame's packed records, ready for upload. */
    public ByteBuffer dataForUpload() {
        ByteBuffer view = staging.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        view.flip();
        return view;
    }

    public int instanceCount() { return instanceCount; }
    public int capacity() { return capacity; }
    public int usedBytes() { return instanceCount * INSTANCE_BYTES; }
    public int capacityBytes() { return capacity * INSTANCE_BYTES; }
    public long overflowCount() { return overflowCount; }
    public long totalAccepted() { return totalAccepted; }
}
