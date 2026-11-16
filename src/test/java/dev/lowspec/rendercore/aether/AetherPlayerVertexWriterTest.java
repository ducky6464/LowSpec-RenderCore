package dev.lowspec.rendercore.aether;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.jupiter.api.Assertions.*;

class AetherPlayerVertexWriterTest {
    @Test
    void writesExpectedCompactVertexLayout() {
        float[] source = {1.25f, -2.5f, 3.75f, 0.5f, 1.0f, -1.0f, 0.0f, 1.0f};
        ByteBuffer target = ByteBuffer.allocate(20);
        assertTrue(AetherPlayerVertexWriter.write(source, source.length, target));
        assertEquals(20, target.position());
        target.flip().order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1.25f, target.getFloat());
        assertEquals(-2.5f, target.getFloat());
        assertEquals(3.75f, target.getFloat());
        assertEquals(16384, target.getShort());
        assertEquals(32767, target.getShort());
        assertEquals(-127, target.get());
        assertEquals(0, target.get());
        assertEquals(127, target.get());
        assertEquals(0, target.get());
    }

    @Test
    void clampsNormalizedAttributesAndSupportsMultipleVertices() {
        float[] source = {
                0, 0, 0, -5, 5, -2, 2, 0,
                1, 2, 3, 0, 0.25f, 0, 0.5f, -1
        };
        ByteBuffer target = ByteBuffer.allocate(40);
        assertTrue(AetherPlayerVertexWriter.write(source, source.length, target));
        assertEquals(40, target.position());
        target.flip().order(ByteOrder.LITTLE_ENDIAN);
        target.position(12);
        assertEquals(-32767, target.getShort());
        assertEquals(32767, target.getShort());
        assertEquals(-127, target.get());
        assertEquals(127, target.get());
    }

    @Test
    void rejectsInvalidCountsNonFiniteValuesAndInsufficientSpace() {
        float[] source = new float[8];
        assertFalse(AetherPlayerVertexWriter.write(source, 7, ByteBuffer.allocate(20)));
        assertFalse(AetherPlayerVertexWriter.write(source, 16, ByteBuffer.allocate(20)));
        source[4] = Float.NaN;
        assertFalse(AetherPlayerVertexWriter.write(source, 8, ByteBuffer.allocate(20)));
        assertThrows(IllegalArgumentException.class,
                () -> AetherPlayerVertexWriter.write(source, Integer.MAX_VALUE, ByteBuffer.allocate(20)));
    }
}
