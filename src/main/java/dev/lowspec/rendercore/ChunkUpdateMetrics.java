package dev.lowspec.rendercore;

import java.util.Arrays;

/** Rolling CPU time spent preparing/scheduling visible chunk updates. */
public final class ChunkUpdateMetrics {
    private static final int CAPACITY = 120;
    private static final long[] SAMPLES = new long[CAPACITY];
    private static int next;
    private static int size;
    private static long totalNanos;

    private ChunkUpdateMetrics() { }

    public static void record(long nanos) {
        long safe = Math.max(0L, nanos);
        if (size == CAPACITY) totalNanos -= SAMPLES[next];
        else size++;
        SAMPLES[next] = safe;
        totalNanos += safe;
        next = (next + 1) % CAPACITY;
    }

    public static Snapshot snapshot() {
        if (size == 0) return new Snapshot(0, 0.0, 0.0);
        long[] sorted = Arrays.copyOf(SAMPLES, size);
        Arrays.sort(sorted);
        int p95 = Math.min(size - 1, (int) Math.ceil(size * 0.95) - 1);
        return new Snapshot(size, (double) totalNanos / size / 1_000_000.0,
                sorted[p95] / 1_000_000.0);
    }

    public record Snapshot(int samples, double averageMillis, double p95Millis) { }
}
