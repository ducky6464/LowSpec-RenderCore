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
 * Upload completion is fenced before a slot can be reused. Once a renderer
 * submits a draw using a returned buffer, it must call markDrawSubmitted()
 * after closing that draw pass so the slot fence also covers the draw.
 * If all slots are busy, an upload is skipped rather than stalling the frame.
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
    private long drawFenceUpdates;
    private long fenceErrors;
    private boolean closed;

    public AetherInstanceBufferPool() {
        for (int i = 0; i < slots.length; i++) slots[i] = new Slot(i);
    }

    /**
     * Uploads the current batch into a free ring slot and returns its GPU
     * buffer. Returns null for empty input, a busy ring, or an upload failure.
     */
    public GpuBuffer upload(AetherInstanceBatch batch) {
        AetherRenderThread.assertCurrent();
        if (closed || batch == null || batch.instanceCount() == 0) return null;

        int byteCount = batch.usedBytes();
        if (byteCount <= 0 || byteCount > BUFFER_BYTES) {
            recordFailure("Invalid Aether instance upload size: " + byteCount, null);
            return null;
        }
        ByteBuffer source = batch.dataForUpload();
        if (source.remaining() != byteCount) {
            recordFailure("Aether staging buffer size did not match its instance count", null);
            return null;
        }

        Slot slot = acquireSlot();
        if (slot == null) {
            skippedBusyUploads++;
            return null;
        }

        CommandEncoder encoder = null;
        try {
            if (slot.buffer == null || slot.buffer.isClosed()) {
                slot.buffer = RenderSystem.getDevice().createBuffer(
                        () -> "Aether instance upload slot " + slot.index,
                        BUFFER_USAGE, BUFFER_BYTES);
            }

            encoder = RenderSystem.getDevice().createCommandEncoder();
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
            // A failed map may have partially queued GPU work. Quarantine the
            // slot instead of reusing memory whose completion is uncertain.
            if (encoder != null) {
                try {
                    slot.fence = encoder.createFence();
                } catch (RuntimeException fenceException) {
                    slot.quarantined = true;
                    fenceErrors++;
                    exception.addSuppressed(fenceException);
                }
            } else {
                slot.quarantined = true;
            }
            recordFailure("Aether instance buffer upload failed", exception);
            return null;
        }
    }

    /**
     * Call after the render pass using this buffer has been closed/submitted.
     * The new fence is ordered after the draw and supersedes the upload fence.
     */
    public boolean markDrawSubmitted(GpuBuffer buffer) {
        AetherRenderThread.assertCurrent();
        if (closed || buffer == null) return false;
        for (Slot slot : slots) {
            if (slot.buffer != buffer || slot.quarantined) continue;
            try {
                GpuFence drawFence = RenderSystem.getDevice().createCommandEncoder().createFence();
                GpuFence previous = slot.fence;
                slot.fence = drawFence;
                if (previous != null) previous.close();
                drawFenceUpdates++;
                return true;
            } catch (RuntimeException exception) {
                slot.quarantined = true;
                fenceErrors++;
                recordFailure("Could not fence Aether draw-buffer use; slot quarantined", exception);
                return false;
            }
        }
        return false;
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
            if (slot.quarantined) continue;
            if (slot.fence == null) {
                nextSlot = index;
                return slot;
            }
            try {
                if (!slot.fence.awaitCompletion(0L)) continue;
                slot.fence.close();
                slot.fence = null;
                slot.bytes = 0;
                nextSlot = index;
                return slot;
            } catch (RuntimeException exception) {
                slot.quarantined = true;
                fenceErrors++;
                recordFailure("Aether upload fence polling failed; slot quarantined", exception);
            }
        }
        return null;
    }

    public long successfulUploads() { return successfulUploads; }
    public long skippedBusyUploads() { return skippedBusyUploads; }
    public long failedUploads() { return failedUploads; }
    public long uploadedBytes() { return uploadedBytes; }
    public long drawFenceUpdates() { return drawFenceUpdates; }
    public long fenceErrors() { return fenceErrors; }
    public int slotCount() { return slots.length; }

    public int inFlightSlots() {
        int count = 0;
        for (Slot slot : slots) if (!slot.quarantined && slot.fence != null) count++;
        return count;
    }

    public int quarantinedSlots() {
        int count = 0;
        for (Slot slot : slots) if (slot.quarantined) count++;
        return count;
    }

    public int retainedBytes() {
        int bytes = 0;
        for (Slot slot : slots) if (slot.buffer != null && !slot.buffer.isClosed()) bytes += BUFFER_BYTES;
        return bytes;
    }

    @Override
    public void close() {
        AetherRenderThread.assertCurrent();
        if (closed) return;
        closed = true;
        for (Slot slot : slots) {
            boolean safeToClose = !slot.quarantined;
            if (slot.fence != null) {
                try {
                    safeToClose &= slot.fence.awaitCompletion(1_000_000_000L);
                } catch (RuntimeException exception) {
                    safeToClose = false;
                    LowSpecRenderCoreClient.LOGGER.warn(
                            "Aether GPU fence failed during shutdown for slot {}.", slot.index, exception);
                }
                try {
                    slot.fence.close();
                } catch (RuntimeException exception) {
                    LowSpecRenderCoreClient.LOGGER.debug("Aether GPU fence cleanup failed.", exception);
                }
                slot.fence = null;
            }
            if (!safeToClose) {
                LowSpecRenderCoreClient.LOGGER.warn(
                        "Aether GPU slot {} could not be proven idle; deferring its buffer cleanup to device teardown.",
                        slot.index);
                slot.buffer = null;
                continue;
            }
            if (slot.buffer != null) {
                slot.buffer.close();
                slot.buffer = null;
            }
        }
    }

    private static final class Slot {
        private final int index;
        private GpuBuffer buffer;
        private GpuFence fence;
        private int bytes;
        private boolean quarantined;

        private Slot(int index) { this.index = index; }
    }
}
