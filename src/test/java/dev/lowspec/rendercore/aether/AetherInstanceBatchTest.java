package dev.lowspec.rendercore.aether;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class AetherInstanceBatchTest {
    @Test
    void packsTransformColorAndTextureLayerInExpectedOrder() {
        AetherInstanceBatch batch = new AetherInstanceBatch(1);
        float[] matrix = new float[16];
        for (int i = 0; i < matrix.length; i++) {
            matrix[i] = i + 0.25f;
        }

        assertTrue(batch.tryAppend(matrix, 0xAABBCCDD, 7));
        assertEquals(1, batch.instanceCount());
        assertEquals(AetherInstanceBatch.INSTANCE_BYTES, batch.usedBytes());

        ByteBuffer data = batch.dataForUpload().order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(AetherInstanceBatch.INSTANCE_BYTES, data.remaining());
        for (int i = 0; i < 16; i++) {
            assertEquals(matrix[i], data.getFloat(), 0.0001f);
        }
        assertEquals(0xAABBCCDD, data.getInt());
        assertEquals(7, data.getInt());
        assertFalse(data.hasRemaining());
    }

    @Test
    void rejectsInvalidRecordsAndCountsCapacityOverflow() {
        AetherInstanceBatch batch = new AetherInstanceBatch(1);
        float[] matrix = new float[16];

        assertFalse(batch.tryAppend(null, -1, 0));
        assertFalse(batch.tryAppend(matrix, -1, -1));
        assertEquals(0, batch.instanceCount());
        assertEquals(2, batch.overflowCount());

        assertTrue(batch.tryAppend(matrix, -1, 0));
        assertFalse(batch.tryAppend(matrix, -1, 0));
        assertEquals(1, batch.instanceCount());
        assertEquals(3, batch.overflowCount());
    }

    @Test
    void beginFrameResetsOnlyCurrentFrameData() {
        AetherInstanceBatch batch = new AetherInstanceBatch(2);
        float[] matrix = new float[16];

        assertTrue(batch.tryAppend(matrix, 0xFFFFFFFF, 0));
        batch.beginFrame();

        assertEquals(0, batch.instanceCount());
        assertEquals(0, batch.usedBytes());
        assertEquals(1, batch.totalAccepted());
        assertEquals(0, batch.dataForUpload().remaining());
    }

    @Test
    void rejectsZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new AetherInstanceBatch(0));
    }
}
