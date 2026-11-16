package dev.lowspec.rendercore.aether;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AetherCuboidMeshBuilderTest {
    private static float[] uvs() {
        float[] values = new float[AetherCuboidMeshBuilder.FACE_UV_FLOATS];
        for (int face = 0; face < 6; face++) {
            values[face * 4] = face;
            values[face * 4 + 1] = 0.1f;
            values[face * 4 + 2] = face + 0.5f;
            values[face * 4 + 3] = 0.9f;
        }
        return values;
    }

    @Test
    void writesThirtySixVerticesWithOutwardFaceNormals() {
        float[] output = new float[AetherCuboidMeshBuilder.FLOATS_PER_CUBOID];
        int end = AetherCuboidMeshBuilder.writeCuboid(0, 1, 2, 3, 4, 5, uvs(), output, 0);
        assertEquals(AetherCuboidMeshBuilder.FLOATS_PER_CUBOID, end);
        assertEquals(36, end / AetherCuboidMeshBuilder.FLOATS_PER_VERTEX);

        float[][] expected = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int face = 0; face < 6; face++) {
            int firstVertex = face * 6;
            for (int vertex = 0; vertex < 6; vertex++) {
                int normal = (firstVertex + vertex) * AetherCuboidMeshBuilder.FLOATS_PER_VERTEX + 5;
                assertEquals(expected[face][0], output[normal]);
                assertEquals(expected[face][1], output[normal + 1]);
                assertEquals(expected[face][2], output[normal + 2]);
            }
        }
    }

    @Test
    void writesWithinCallerProvidedOffsetAndKeepsBounds() {
        float[] output = new float[AetherCuboidMeshBuilder.FLOATS_PER_CUBOID + 4];
        output[0] = 1234;
        int end = AetherCuboidMeshBuilder.writeCuboid(-2, -3, -4, 2, 3, 4, uvs(), output, 4);
        assertEquals(4 + AetherCuboidMeshBuilder.FLOATS_PER_CUBOID, end);
        assertEquals(1234, output[0]);
        for (int vertex = 0; vertex < 36; vertex++) {
            int p = 4 + vertex * 8;
            assertTrue(output[p] >= -2 && output[p] <= 2);
            assertTrue(output[p + 1] >= -3 && output[p + 1] <= 3);
            assertTrue(output[p + 2] >= -4 && output[p + 2] <= 4);
        }
    }

    @Test
    void rejectsInvalidBoundsUvsAndDestinationRanges() {
        float[] output = new float[AetherCuboidMeshBuilder.FLOATS_PER_CUBOID];
        assertThrows(IllegalArgumentException.class,
                () -> AetherCuboidMeshBuilder.writeCuboid(0,0,0, 0,1,1, uvs(), output, 0));
        assertThrows(IllegalArgumentException.class,
                () -> AetherCuboidMeshBuilder.writeCuboid(0,0,0, 1,1,1, new float[2], output, 0));
        assertThrows(IndexOutOfBoundsException.class,
                () -> AetherCuboidMeshBuilder.writeCuboid(0,0,0, 1,1,1, uvs(), output, 1));
        float[] invalidUvs = uvs();
        invalidUvs[3] = Float.NaN;
        assertThrows(IllegalArgumentException.class,
                () -> AetherCuboidMeshBuilder.writeCuboid(0,0,0, 1,1,1, invalidUvs, output, 0));
    }
}
