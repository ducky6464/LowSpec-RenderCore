package dev.lowspec.rendercore;

/** Low-allocation entity extraction and render submission measurements. */
public final class EntityRenderMetrics {
    private static final int WINDOW = 120;
    private static final long[] extractionNanos = new long[WINDOW];
    private static final long[] renderNanos = new long[WINDOW];
    private static int cursor;
    private static int samples;
    private static long frameExtractions;
    private static long frameRenders;
    private static long framePlayerExtractions;
    private static long framePlayerRenders;
    private static long frameExtractionNanos;
    private static long frameRenderNanos;
    private static long totalExtractions;
    private static long totalRenders;
    private static long totalPlayerExtractions;
    private static long totalPlayerRenders;

    private EntityRenderMetrics() { }

    public static long beginExtraction() { return System.nanoTime(); }
    public static long beginRender() { return System.nanoTime(); }

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

    public static void endRender(long startNanos, boolean player) {
        if (startNanos == 0L) return;
        long elapsed = Math.max(0L, System.nanoTime() - startNanos);
        frameRenders++;
        totalRenders++;
        frameRenderNanos += elapsed;
        if (player) {
            framePlayerRenders++;
            totalPlayerRenders++;
        }
    }

    public static void finishFrame() {
        extractionNanos[cursor] = frameExtractionNanos;
        renderNanos[cursor] = frameRenderNanos;
        cursor = (cursor + 1) % WINDOW;
        if (samples < WINDOW) samples++;
        frameExtractions = frameRenders = 0L;
        framePlayerExtractions = framePlayerRenders = 0L;
        frameExtractionNanos = frameRenderNanos = 0L;
    }

    public static Snapshot snapshot() {
        long extraction = 0L;
        long render = 0L;
        for (int i = 0; i < samples; i++) {
            extraction += extractionNanos[i];
            render += renderNanos[i];
        }
        return new Snapshot(samples, frameExtractions, frameRenders,
                framePlayerExtractions, framePlayerRenders,
                samples == 0 ? 0.0 : extraction / (double) samples / 1_000_000.0,
                samples == 0 ? 0.0 : render / (double) samples / 1_000_000.0,
                totalExtractions, totalRenders, totalPlayerExtractions, totalPlayerRenders);
    }

    public record Snapshot(int samples, long extractionsThisFrame, long rendersThisFrame,
                           long playerExtractionsThisFrame, long playerRendersThisFrame,
                           double averageExtractionMillisPerFrame, double averageRenderMillisPerFrame,
                           long totalExtractions, long totalRenders,
                           long totalPlayerExtractions, long totalPlayerRenders) { }
}
