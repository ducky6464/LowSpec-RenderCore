package dev.lowspec.rendercore;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.function.Supplier;

/**
 * Small render-thread-only pool for retired chunk vertex/index buffers.
 * Buffers are reused only when usage matches and capacity is sufficient.
 */
public final class ChunkGpuBufferPool {
    private static final long MAX_POOL_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_RETAINED_BUFFER_BYTES = 4L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 32;
    private static final ArrayDeque<GpuBuffer> AVAILABLE = new ArrayDeque<>();
    private static long pooledBytes;
    private static long reuseHits;
    private static long allocationMisses;
    private static GpuDevice pooledDevice;

    private ChunkGpuBufferPool() { }

    public static GpuBuffer createOrReuse(GpuDevice device, Supplier<String> label, int usage, ByteBuffer source) {
        if (!RenderSystem.isOnRenderThread()) {
            allocationMisses++;
            return device.createBuffer(label, usage, source);
        }
        if (!enabled()) {
            clear();
            allocationMisses++;
            return device.createBuffer(label, usage, source);
        }
        if (pooledDevice != null && pooledDevice != device) clear();
        pooledDevice = device;

        ByteBuffer data = source.duplicate();
        int requiredBytes = data.remaining();
        GpuBuffer reused = acquire(usage, requiredBytes);
        if (reused == null) {
            allocationMisses++;
            return device.createBuffer(label, usage, source);
        }

        try {
            CommandEncoder encoder = device.createCommandEncoder();
            encoder.writeToBuffer(reused.slice(0L, requiredBytes), data);
            reuseHits++;
            return reused;
        } catch (RuntimeException exception) {
            reused.close();
            allocationMisses++;
            return device.createBuffer(label, usage, source);
        }
    }

    public static void release(GpuBuffer buffer) {
        if (buffer == null || buffer.isClosed()) return;
        GpuDevice currentDevice = RenderSystem.tryGetDevice();
        if (!enabled() || !RenderSystem.isOnRenderThread() || currentDevice == null
                || (pooledDevice != null && pooledDevice != currentDevice)
                || (buffer.usage() & GpuBuffer.USAGE_COPY_DST) == 0
                || buffer.size() <= 0L || buffer.size() > MAX_RETAINED_BUFFER_BYTES
                || AVAILABLE.size() >= MAX_ENTRIES
                || pooledBytes + buffer.size() > MAX_POOL_BYTES) {
            buffer.close();
            return;
        }
        pooledDevice = currentDevice;
        AVAILABLE.addLast(buffer);
        pooledBytes += buffer.size();
    }

    private static GpuBuffer acquire(int usage, long requiredBytes) {
        if (requiredBytes <= 0L || requiredBytes > MAX_RETAINED_BUFFER_BYTES
                || (usage & GpuBuffer.USAGE_COPY_DST) == 0) return null;
        GpuBuffer best = null;
        for (Iterator<GpuBuffer> iterator = AVAILABLE.iterator(); iterator.hasNext();) {
            GpuBuffer candidate = iterator.next();
            if (candidate.isClosed()) {
                iterator.remove();
                pooledBytes -= candidate.size();
                continue;
            }
            if (candidate.usage() == usage && candidate.size() >= requiredBytes
                    && (best == null || candidate.size() < best.size())) {
                best = candidate;
            }
        }
        if (best != null) {
            AVAILABLE.remove(best);
            pooledBytes -= best.size();
        }
        return best;
    }

    public static void clear() {
        if (RenderSystem.tryGetDevice() != null && RenderSystem.isOnRenderThread()) {
            for (GpuBuffer buffer : AVAILABLE) {
                if (!buffer.isClosed()) buffer.close();
            }
        }
        AVAILABLE.clear();
        pooledBytes = 0L;
        pooledDevice = null;
    }

    public static long getReuseHits() { return reuseHits; }
    public static long getAllocationMisses() { return allocationMisses; }
    public static long getPooledBytes() { return pooledBytes; }

    private static boolean enabled() {
        RenderCoreConfig config = LowSpecRenderCoreClient.CONFIG;
        return config != null && config.chunkGpuBufferPoolEnabled;
    }
}
