package dev.lowspec.rendercore.aether;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;

import java.nio.ByteBuffer;

/**
 * Small non-blocking ring for uploading Aether instance records.
 *
 * A slot is not reused until its fence reports completion. If every slot is
 * still in flight, the upload is skipped rather than stalling the render
 * thread. This class only uploads data; it does not submit a draw.
 */
public final class AetherInstanceBufferPool implements AutoCloseable {
    private static final int SLOT_COUNT = 3;
    private static final int BUFFER_BYTES =
            AetherInstanceBatch.DEFAULT_CAPACITY * AetherInstanceBatch.INSTANCE_BYTES;
    private static final int BUFFER_USAGE = GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE;

    private final Slot[] slots = new Slot[SLOT_COUNT];
    private int nextSlot;
    private long successfulUploads;
    private long skippedBusyUploads;
    private long failedUploads;
    private long uploadedBytes;
    private boolean closed;

    public AetherInstanceBufferPool() {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new Slot();
        }
    }

    /**
     * Uploads the batch into a free ring slot and returns its GPU buffer.
     * Returns null for an empty batch or when all slots are still in flight.
     */
    public GpuBuffer upload(AetherInstanceBatch batch) {
        AetherRenderThread.assertCurrent();
        if (closed) return null;
        if (batch == null || batch.instanceCount() == 0) return null;

        int byteCount = batch.usedBytes();
        if (byteCount <= 0 || byteCount > BUFFER_BYTES) {
            recordFailure("Invalid Aether instance upload size: " + byteCount, null);
            return null;
        }

        Slot slot = acquireSlot();
        if (slot == null) {
            skippedBusyUploads++;
            return null;
        }

        try {
            if (slot.buffer == null || slot.buffer.isClosed()) {
                slot.buffer = RenderSystem.getDevice().createBuffer(
                        () -> "Aether instance upload slot",
                        BUFFER_USAGE,
                        BUFFER_BYTES);
            }

            ByteBuffer source = batch.dataForUpload();
            if (source.remaining() != byteCount) {
                recordFailure("Aether staging buffer size did not match its instance count", null);
                return null;
            }

            // Keep the upload and its fence on the same encoder so their
            // ordering is explicit. The fence marks completion of this upload.
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            try (GpuBuffer.MappedView mapped = encoder.mapBuffer(
                    slot.buffer.slice(0L, byteCount), false, true)) {
                mapped.data().put(source);
            }

            slot.fence = encoder.createFence();
            slot.bytes = byteCount;
            successfulUploads++;
            uploadedBytes += byteCount;
            nextSlot = (slot.index + 1) % slots.length;
            return slot.buffer;
        } catch (RuntimeException exception) {
            // Do not silently hide driver/API failures. Avoid logging every
            // frame if a persistent driver problem occurs.
            recordFailure("Aether instance buffer upload failed", exception);
            return null;
        }
    }

    private void recordFailure(String message, RuntimeException exception) {
        failedUploads++;
        if (failedUploads == 1 || failedUploads % 100 == 0) {
            if (exception == null) {
                LowSpecRenderCoreClient.LOGGER.warn("{} (failures={})", message, failedUploads);
            } else {
                LowSpecRenderCoreClient.LOGGER.warn("{} (failures={})", message, failedUploads, exception);
            }
        }
    }

    private Slot acquireSlot() {
        for (int offset = 0; offset < slots.length; offset++) {
            int index = (nextSlot + offset) % slots.length;
            Slot slot = slots[index];
            if (slot.fence == null) {
                slot.index = index;
                return slot;
            }
            if (slot.fence.awaitCompletion(0L)) {
                slot.fence.close();
                slot.fence = null;
                slot.bytes = 0;
                slot.index = index;
                return slot;
            }
        }
        return null;
    }

    public long successfulUploads() { return successfulUploads; }
    public long skippedBusyUploads() { return skippedBusyUploads; }
    public long failedUploads() { return failedUploads; }
    public long uploadedBytes() { return uploadedBytes; }
    public int slotCount() { return slots.length; }

    @Override
    public void close() {
        AetherRenderThread.assertCurrent();
        if (closed) return;
        closed = true;
        for (Slot slot : slots) {
            if (slot.fence != null) {
                // Do not allow a broken or lost graphics context to hang
                // client shutdown forever. If the GPU does not signal within
                // one second, leave the buffer for device teardown instead
                // of explicitly releasing a resource that may still be busy.
                boolean completed = slot.fence.awaitCompletion(1_000_000_000L);
                slot.fence.close();
                slot.fence = null;
                if (!completed) {
                    LowSpecRenderCoreClient.LOGGER.warn(
                            "Aether GPU upload slot {} did not finish before shutdown; deferring buffer cleanup to device teardown.",
                            slot.index);
                    slot.buffer = null;
                    continue;
                }
            }
            if (slot.buffer != null) {
                slot.buffer.close();
                slot.buffer = null;
            }
        }
    }

    private static final class Slot {
        private int index;
        private GpuBuffer buffer;
        private GpuFence fence;
        private int bytes;
    }
}
