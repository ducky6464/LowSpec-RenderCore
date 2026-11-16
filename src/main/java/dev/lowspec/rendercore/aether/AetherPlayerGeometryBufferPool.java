package dev.lowspec.rendercore.aether;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.lowspec.rendercore.LowSpecRenderCoreClient;


/**
 * Bounded non-blocking ring for uploading captured player base-model vertices.
 * This prepares GPU-resident geometry but intentionally does not issue draw calls.
 */
public final class AetherPlayerGeometryBufferPool implements AutoCloseable {
    private static final int SLOT_COUNT = 3;
    private final int capacityBytes;
    private final Slot[] slots = new Slot[SLOT_COUNT];
    private int nextSlot;
    private long successfulUploads, skippedBusyUploads, failedUploads, uploadedBytes, fenceErrors, drawFenceUpdates;
    private boolean closed;

    public AetherPlayerGeometryBufferPool(int capacityFloats) {
        if (capacityFloats < 1) throw new IllegalArgumentException("capacity must be positive");
        if (capacityFloats % AetherPlayerVertexWriter.SOURCE_FLOATS_PER_VERTEX != 0) {
            throw new IllegalArgumentException("capacity must contain whole vertices");
        }
        capacityBytes = Math.multiplyExact(capacityFloats / AetherPlayerVertexWriter.SOURCE_FLOATS_PER_VERTEX,
                AetherPlayerVertexWriter.GPU_BYTES_PER_VERTEX);
        for (int i = 0; i < slots.length; i++) slots[i] = new Slot(i);
    }

    public GpuBuffer upload(AetherPlayerGeometryArena arena) {
        AetherRenderThread.assertCurrent();
        if (closed || arena == null || arena.usedFloats() == 0) return null;
        if (arena.usedFloats() % AetherPlayerVertexWriter.SOURCE_FLOATS_PER_VERTEX != 0) {
            failedUploads++;
            return null;
        }
        int bytes = Math.multiplyExact(arena.usedFloats() / AetherPlayerVertexWriter.SOURCE_FLOATS_PER_VERTEX,
                AetherPlayerVertexWriter.GPU_BYTES_PER_VERTEX);
        if (bytes > capacityBytes) {
            failedUploads++;
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
                        () -> "Aether player geometry slot " + slot.index,
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, capacityBytes);
            }
            encoder = RenderSystem.getDevice().createCommandEncoder();
            try (GpuBuffer.MappedView mapped = encoder.mapBuffer(slot.buffer.slice(0L, bytes), false, true)) {
                if (!AetherPlayerVertexWriter.write(arena.data(), arena.usedFloats(), mapped.data())) {
                    throw new IllegalStateException("Could not pack captured player vertices into GPU layout");
                }
            }
            slot.fence = encoder.createFence();
            slot.bytes = bytes;
            successfulUploads++;
            uploadedBytes += bytes;
            nextSlot = (slot.index + 1) % slots.length;
            return slot.buffer;
        } catch (RuntimeException exception) {
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
            failedUploads++;
            if (failedUploads == 1 || failedUploads % 100 == 0) {
                LowSpecRenderCoreClient.LOGGER.warn("Aether player geometry upload failed (failures={})",
                        failedUploads, exception);
            }
            return null;
        }
    }

    /** Call after the render pass using this buffer has been closed/submitted. */
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
                LowSpecRenderCoreClient.LOGGER.warn(
                        "Could not fence Aether player geometry draw; slot quarantined", exception);
                return false;
            }
        }
        return false;
    }

    private Slot acquireSlot() {
        for (int offset = 0; offset < slots.length; offset++) {
            Slot slot = slots[(nextSlot + offset) % slots.length];
            if (slot.quarantined) continue;
            if (slot.fence == null) {
                nextSlot = slot.index;
                return slot;
            }
            try {
                if (!slot.fence.awaitCompletion(0L)) continue;
                slot.fence.close();
                slot.fence = null;
                slot.bytes = 0;
                nextSlot = slot.index;
                return slot;
            } catch (RuntimeException exception) {
                slot.quarantined = true;
                fenceErrors++;
                LowSpecRenderCoreClient.LOGGER.warn("Aether geometry fence polling failed; slot quarantined",
                        exception);
            }
        }
        return null;
    }

    public long successfulUploads() { return successfulUploads; }
    public long skippedBusyUploads() { return skippedBusyUploads; }
    public long failedUploads() { return failedUploads; }
    public long uploadedBytes() { return uploadedBytes; }
    public long fenceErrors() { return fenceErrors; }
    public long drawFenceUpdates() { return drawFenceUpdates; }
    public int retainedBytes() {
        int bytes = 0;
        for (Slot slot : slots) if (slot.buffer != null && !slot.buffer.isClosed()) bytes += capacityBytes;
        return bytes;
    }
    public int slotCount() { return slots.length; }
    public int capacityBytes() { return capacityBytes; }
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

    @Override
    public void close() {
        AetherRenderThread.assertCurrent();
        if (closed) return;
        closed = true;
        for (Slot slot : slots) {
            boolean safe = !slot.quarantined;
            if (slot.fence != null) {
                try {
                    safe &= slot.fence.awaitCompletion(1_000_000_000L);
                } catch (RuntimeException exception) {
                    safe = false;
                    LowSpecRenderCoreClient.LOGGER.warn("Aether geometry fence failed during shutdown", exception);
                }
                try { slot.fence.close(); } catch (RuntimeException ignored) { }
                slot.fence = null;
            }
            if (safe && slot.buffer != null) slot.buffer.close();
            else if (slot.buffer != null) {
                LowSpecRenderCoreClient.LOGGER.warn(
                        "Deferring cleanup of busy Aether geometry buffer slot {}", slot.index);
            }
            slot.buffer = null;
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
