package dev.lowspec.rendercore;

/** Rolling CPU timings for vanilla chunk-section mesh generation. */
public final class ChunkMeshMetrics {
    private static final int CAPACITY = 120;
    private static final long[] SAMPLES = new long[CAPACITY];
    private static int next;
    private static int size;
    private static long totalNanos;

    private ChunkMeshMetrics() { }

    public static synchronized void record(long nanos) {
        long safe = Math.max(0L, nanos);
        if (size == CAPACITY) totalNanos -= SAMPLES[next];
        else size++;
        SAMPLES[next] = safe;
        totalNanos += safe;
        next = (next + 1) % CAPACITY;
    }

    public static synchronized Snapshot snapshot() {
        if (size == 0) return new Snapshot(0, 0.0, 0.0);
        long[] sorted = new long[size];
        System.arraycopy(SAMPLES, 0, sorted, 0, size);
        java.util.Arrays.sort(sorted);
        int index = Math.max(0, (int) Math.ceil(size * 0.95) - 1);
        return new Snapshot(size, (double) totalNanos / size / 1_000_000.0,
                sorted[index] / 1_000_000.0);
    }

    public record Snapshot(int samples, double averageMillis, double p95Millis) { }
}
