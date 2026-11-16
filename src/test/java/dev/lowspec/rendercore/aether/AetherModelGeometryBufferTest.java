package dev.lowspec.rendercore.aether;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AetherModelGeometryBufferTest {
    private static ModelPart oneCuboid() {
        ModelPart.Cuboid cuboid = new ModelPart.Cuboid(
                0, 0, 0, 0, 0, 2, 3, 4,
                0, 0, 0, false, 64, 64, EnumSet.allOf(Direction.class));
        return new ModelPart(List.of(cuboid), Map.of());
    }

    @Test
    void capturesSixFacesAsThirtySixTriangleVertices() {
        AetherModelGeometryBuffer buffer = new AetherModelGeometryBuffer(6);
        assertTrue(buffer.capture(oneCuboid(), new MatrixStack()));
        assertEquals(1, buffer.cuboidCount());
        assertEquals(6, buffer.quadCount());
        assertEquals(36, buffer.vertexCount());
        assertEquals(36 * AetherModelGeometryBuffer.FLOATS_PER_VERTEX, buffer.usedFloats());
        for (int i = 0; i < buffer.usedFloats(); i++) {
            assertTrue(Float.isFinite(buffer.data()[i]), "non-finite vertex component at " + i);
        }
    }

    @Test
    void capturesModelPartTransformIntoVertexPositions() {
        MatrixStack matrices = new MatrixStack();
        matrices.translate(2.0f, 3.0f, 4.0f);
        AetherModelGeometryBuffer translated = new AetherModelGeometryBuffer(6);
        assertTrue(translated.capture(oneCuboid(), matrices));

        AetherModelGeometryBuffer base = new AetherModelGeometryBuffer(6);
        assertTrue(base.capture(oneCuboid(), new MatrixStack()));
        for (int vertex = 0; vertex < base.vertexCount(); vertex++) {
            int offset = vertex * AetherModelGeometryBuffer.FLOATS_PER_VERTEX;
            assertEquals(base.data()[offset] + 2.0f, translated.data()[offset], 1.0e-5f);
            assertEquals(base.data()[offset + 1] + 3.0f, translated.data()[offset + 1], 1.0e-5f);
            assertEquals(base.data()[offset + 2] + 4.0f, translated.data()[offset + 2], 1.0e-5f);
        }
    }

    @Test
    void reportsCapacityOverflowWithoutWritingPastBuffer() {
        AetherModelGeometryBuffer buffer = new AetherModelGeometryBuffer(2);
        assertFalse(buffer.capture(oneCuboid(), new MatrixStack()));
        assertTrue(buffer.overflowed());
        assertEquals(2, buffer.quadCount());
        assertEquals(12, buffer.vertexCount());
        assertEquals(1, buffer.overflowCount());
    }

    @Test
    void rejectsInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new AetherModelGeometryBuffer(0));
    }
}
