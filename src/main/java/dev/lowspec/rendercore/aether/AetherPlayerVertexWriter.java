package dev.lowspec.rendercore.aether;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Converts extractor vertices (8 floats) into a compact 24-byte GPU layout:
 * float3 position, float2 UV, normalized-byte3 normal, one pad byte.
 * No per-vertex objects or temporary arrays are allocated.
 */
public final class AetherPlayerVertexWriter {
    public static final int SOURCE_FLOATS_PER_VERTEX = 8;
    public static final int GPU_BYTES_PER_VERTEX = 24;
    private AetherPlayerVertexWriter() { }

    public static boolean write(float[] source, int floatCount, ByteBuffer target) {
        if (source == null || target == null) throw new NullPointerException("vertex data");
        if (floatCount < 0 || floatCount > source.length || floatCount % SOURCE_FLOATS_PER_VERTEX != 0) {
            return false;
        }
        int vertexCount = floatCount / SOURCE_FLOATS_PER_VERTEX;
        int requiredBytes = Math.multiplyExact(vertexCount, GPU_BYTES_PER_VERTEX);
        if (target.remaining() < requiredBytes) return false;
        for (int i = 0; i < floatCount; i++) {
            if (!Float.isFinite(source[i])) return false;
        }

        target.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < floatCount; i += SOURCE_FLOATS_PER_VERTEX) {
            target.putFloat(source[i]);
            target.putFloat(source[i + 1]);
            target.putFloat(source[i + 2]);
            target.putFloat(source[i + 3]);
            target.putFloat(source[i + 4]);
            target.put(toNormalizedByte(source[i + 5]));
            target.put(toNormalizedByte(source[i + 6]));
            target.put(toNormalizedByte(source[i + 7]));
            target.put((byte) 0);
        }
        return true;
    }

    private static byte toNormalizedByte(float value) {
        float clamped = Math.max(-1.0f, Math.min(1.0f, value));
        return (byte) Math.round(clamped * 127.0f);
    }
}
