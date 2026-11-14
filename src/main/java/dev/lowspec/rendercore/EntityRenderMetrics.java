package dev.lowspec.rendercore;

/** Low-allocation entity extraction and render-command enqueue measurements. */
public final class EntityRenderMetrics {
    private static final int WINDOW = 120;
    private static final long[] extractionNanos = new long[WINDOW];
    private static final long[] enqueueNanos = new long[WINDOW];
    private static int cursor;
    private static int samples;
    private static long frameExtractions;
    private static long frameEnqueues;
    private static long framePlayerExtractions;
    private static long framePlayerEnqueues;
    private static long frameExtractionNanos;
    private static long frameEnqueueNanos;
    private static long lastExtractions;
    private static long lastEnqueues;
    private static long lastPlayerExtractions;
    private static long lastPlayerEnqueues;
    private static long totalExtractions;
    private static long totalEnqueues;
    private static long totalPlayerExtractions;
    private static long totalPlayerEnqueues;

    private EntityRenderMetrics() { }

    private static boolean enabled() {
        return LowSpecRenderCoreClient.CONFIG != null
                && LowSpecRenderCoreClient.CONFIG.diagnosticsEnabled;
    }

    public static long beginExtraction() { return enabled() ? System.nanoTime() : 0L; }
    public static long beginEnqueue() { return enabled() ? System.nanoTime() : 0L; }

    public static void endExtraction(long startNanos, boolean player) {
        if (startNanos == 0L) return;
        long elapsed = Math.max(0L, System.nanoTime() - startNanos);
        frameExtractions++;
        totalExtractions++;
        frameExtractionNanos += elapsed;
        if (player) {
            framePlayerExtractions++;
            totalPlayerExtractions++;
        }
    }

    public static void endEnqueue(long startNanos, boolean player) {
        if (startNanos == 0L) return;
        long elapsed = Math.max(0L, System.nanoTime() - startNanos);
        frameEnqueues++;
        totalEnqueues++;
        frameEnqueueNanos += elapsed;
        if (player) {
            framePlayerEnqueues++;
            totalPlayerEnqueues++;
        }
    }

    public static void finishFrame() {
        extractionNanos[cursor] = frameExtractionNanos;
        enqueueNanos[cursor] = frameEnqueueNanos;
        cursor = (cursor + 1) % WINDOW;
        if (samples < WINDOW) samples++;
        lastExtractions = frameExtractions;
        lastEnqueues = frameEnqueues;
        lastPlayerExtractions = framePlayerExtractions;
        lastPlayerEnqueues = framePlayerEnqueues;
        frameExtractions = frameEnqueues = 0L;
        framePlayerExtractions = framePlayerEnqueues = 0L;
        frameExtractionNanos = frameEnqueueNanos = 0L;
    }

    public static Snapshot snapshot() {
        long extraction = 0L;
        long enqueue = 0L;
        for (int i = 0; i < samples; i++) {
            extraction += extractionNanos[i];
            enqueue += enqueueNanos[i];
        }
        return new Snapshot(samples, lastExtractions, lastEnqueues,
                lastPlayerExtractions, lastPlayerEnqueues,
                samples == 0 ? 0.0 : extraction / (double) samples / 1_000_000.0,
                samples == 0 ? 0.0 : enqueue / (double) samples / 1_000_000.0,
                totalExtractions, totalEnqueues, totalPlayerExtractions, totalPlayerEnqueues);
    }

    public record Snapshot(int samples, long extractionsLastFrame, long enqueuesLastFrame,
                           long playerExtractionsLastFrame, long playerEnqueuesLastFrame,
                           double averageExtractionMillisPerFrame, double averageEnqueueMillisPerFrame,
                           long totalExtractions, long totalEnqueues,
                           long totalPlayerExtractions, long totalPlayerEnqueues) { }
}
