package dev.lowspec.rendercore;

import java.util.Arrays;

/** Rolling CPU-side GameRenderer.render duration samples. Not a GPU/present-time measurement. */
public final class RenderFrameMetrics {
    private static final int CAPACITY = 120;
    private static final long[] SAMPLES = new long[CAPACITY];
    private static int next;
    private static int size;
    private static long totalNanos;

    private RenderFrameMetrics() { }

    public static void record(long nanos) {
        long safe = Math.max(0L, nanos);
        if (size == CAPACITY) {
            totalNanos -= SAMPLES[next];
        } else {
            size++;
        }
        SAMPLES[next] = safe;
        totalNanos += safe;
        next = (next + 1) % CAPACITY;
    }

    public static Snapshot snapshot() {
        if (size == 0) return new Snapshot(0, 0.0, 0.0);
        long[] sorted = new long[size];
        System.arraycopy(SAMPLES, 0, sorted, 0, size);
        Arrays.sort(sorted);
        int p95Index = Math.min(size - 1, (int) Math.ceil(size * 0.95) - 1);
        return new Snapshot(size, (double) totalNanos / size / 1_000_000.0,
                sorted[p95Index] / 1_000_000.0);
    }

    public record Snapshot(int samples, double averageRenderMillis, double p95RenderMillis) { }
}
