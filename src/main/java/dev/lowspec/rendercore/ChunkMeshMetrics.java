package dev.lowspec.rendercore;

/** Rolling, allocation-free CPU timings for vanilla chunk-section mesh generation. */
public final class ChunkMeshMetrics {
    private static final int CAPACITY = 120;
    private static final double BUCKET_MILLIS = 0.25;
    private static final int BUCKET_COUNT = 1025;
    private static final long[] SAMPLES = new long[CAPACITY];
    private static final int[] HISTOGRAM = new int[BUCKET_COUNT];
    private static int next;
    private static int size;
    private static long totalNanos;

    private ChunkMeshMetrics() { }

    public static synchronized void record(long nanos) {
        long safe = Math.max(0L, nanos);
        if (size == CAPACITY) {
            long old = SAMPLES[next];
            totalNanos -= old;
            HISTOGRAM[bucket(old)]--;
        } else {
            size++;
        }
        SAMPLES[next] = safe;
        totalNanos += safe;
        HISTOGRAM[bucket(safe)]++;
        next = (next + 1) % CAPACITY;
    }

    public static synchronized Snapshot snapshot() {
        if (size == 0) return new Snapshot(0, 0.0, 0.0);
        int target = (int) Math.ceil(size * 0.95);
        int cumulative = 0;
        int percentileBucket = BUCKET_COUNT - 1;
        for (int i = 0; i < HISTOGRAM.length; i++) {
            cumulative += HISTOGRAM[i];
            if (cumulative >= target) {
                percentileBucket = i;
                break;
            }
        }
        double p95 = percentileBucket == BUCKET_COUNT - 1
                ? 256.0 : percentileBucket * BUCKET_MILLIS;
        return new Snapshot(size, (double) totalNanos / size / 1_000_000.0, p95);
    }

    private static int bucket(long nanos) {
        return Math.min(BUCKET_COUNT - 1, Math.max(0, (int) (nanos / 250_000L)));
    }

    public record Snapshot(int samples, double averageMillis, double p95Millis) { }
}
