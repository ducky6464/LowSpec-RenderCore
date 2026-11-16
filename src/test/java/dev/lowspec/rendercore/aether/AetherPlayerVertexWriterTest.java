package dev.lowspec.rendercore.aether;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.jupiter.api.Assertions.*;

class AetherPlayerVertexWriterTest {
    @Test
    void declaredMinecraftVertexFormatMatchesPackedStrideAndOffsets() {
        assertEquals(24, AetherPlayerVertexLayout.FORMAT.getVertexSize());
        assertEquals(0, AetherPlayerVertexLayout.FORMAT.getOffset(
                com.mojang.blaze3d.vertex.VertexFormatElement.POSITION));
        assertEquals(12, AetherPlayerVertexLayout.FORMAT.getOffset(
                com.mojang.blaze3d.vertex.VertexFormatElement.UV0));
        assertEquals(20, AetherPlayerVertexLayout.FORMAT.getOffset(
                com.mojang.blaze3d.vertex.VertexFormatElement.NORMAL));
    }

    @Test
    void writesExpectedCompactVertexLayout() {
        float[] source = {1.25f, -2.5f, 3.75f, 0.5f, 1.0f, -1.0f, 0.0f, 1.0f};
        ByteBuffer target = ByteBuffer.allocate(24);
        assertTrue(AetherPlayerVertexWriter.write(source, source.length, target));
        assertEquals(24, target.position());
        target.flip().order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1.25f, target.getFloat());
        assertEquals(-2.5f, target.getFloat());
        assertEquals(3.75f, target.getFloat());
        assertEquals(0.5f, target.getFloat());
        assertEquals(1.0f, target.getFloat());
        assertEquals(-127, target.get());
        assertEquals(0, target.get());
        assertEquals(127, target.get());
        assertEquals(0, target.get());
    }

    @Test
    void preservesUvValuesAndSupportsMultipleVertices() {
        float[] source = {
                0, 0, 0, -5, 5, -2, 2, 0,
                1, 2, 3, 0, 0.25f, 0, 0.5f, -1
        };
        ByteBuffer target = ByteBuffer.allocate(48);
        assertTrue(AetherPlayerVertexWriter.write(source, source.length, target));
        assertEquals(48, target.position());
        target.flip().order(ByteOrder.LITTLE_ENDIAN);
        target.position(12);
        assertEquals(-5.0f, target.getFloat());
        assertEquals(5.0f, target.getFloat());
        target.position(20);
        assertEquals(-127, target.get());
        assertEquals(127, target.get());
    }

    @Test
    void rejectsInvalidCountsNonFiniteValuesAndInsufficientSpace() {
        float[] source = new float[8];
        assertFalse(AetherPlayerVertexWriter.write(source, 7, ByteBuffer.allocate(24)));
        assertFalse(AetherPlayerVertexWriter.write(source, 16, ByteBuffer.allocate(24)));
        source[4] = Float.NaN;
        assertFalse(AetherPlayerVertexWriter.write(source, 8, ByteBuffer.allocate(24)));
        assertFalse(AetherPlayerVertexWriter.write(source, Integer.MAX_VALUE, ByteBuffer.allocate(24)));
    }
}
