package dev.lowspec.rendercore;

import java.util.concurrent.atomic.LongAdder;

/** Low-allocation entity extraction and render-command enqueue measurements. */
public final class EntityRenderMetrics {
    private static final int WINDOW = 120;
    private static final long[] extractionNanos = new long[WINDOW];
    private static final long[] enqueueNanos = new long[WINDOW];
    private static final long[] dispatchNanos = new long[WINDOW];
    private static int cursor;
    private static int samples;

    // Extraction can be performed by more than one thread. LongAdder keeps the
    // instrumentation safe without serializing every entity through one lock.
    private static final LongAdder frameExtractions = new LongAdder();
    private static final LongAdder frameEnqueues = new LongAdder();
    private static final LongAdder framePlayerExtractions = new LongAdder();
    private static final LongAdder framePlayerEnqueues = new LongAdder();
    private static final LongAdder frameExtractionNanos = new LongAdder();
    private static final LongAdder frameEnqueueNanos = new LongAdder();
    private static final LongAdder frameDispatchCalls = new LongAdder();
    private static final LongAdder frameDispatchNanos = new LongAdder();
    private static final LongAdder totalExtractions = new LongAdder();
    private static final LongAdder totalEnqueues = new LongAdder();
    private static final LongAdder totalDispatchCalls = new LongAdder();
    private static final LongAdder totalDispatchNanos = new LongAdder();
    private static final LongAdder totalPlayerExtractions = new LongAdder();
    private static final LongAdder totalPlayerEnqueues = new LongAdder();

    private static volatile long lastExtractions;
    private static volatile long lastEnqueues;
    private static volatile long lastDispatchCalls;
    private static volatile long lastDispatchNanos;
    private static volatile long lastPlayerExtractions;
    private static volatile long lastPlayerEnqueues;

    private EntityRenderMetrics() { }

    private static boolean enabled() {
        return LowSpecRenderCoreClient.CONFIG != null
                && LowSpecRenderCoreClient.CONFIG.diagnosticsEnabled;
    }

    public static long beginExtraction() { return enabled() ? System.nanoTime() : 0L; }
    public static long beginEnqueue() { return enabled() ? System.nanoTime() : 0L; }
    public static long beginDispatch() { return enabled() ? System.nanoTime() : 0L; }

    public static void endDispatch(long startNanos) {
        if (startNanos == 0L) return;
        long elapsed = Math.max(0L, System.nanoTime() - startNanos);
        frameDispatchCalls.increment();
        frameDispatchNanos.add(elapsed);
        totalDispatchCalls.increment();
        totalDispatchNanos.add(elapsed);
    }

    public static void endExtraction(long startNanos, boolean player) {
        if (startNanos == 0L) return;
        long elapsed = Math.max(0L, System.nanoTime() - startNanos);
        frameExtractions.increment();
        totalExtractions.increment();
        frameExtractionNanos.add(elapsed);
        if (player) {
            framePlayerExtractions.increment();
            totalPlayerExtractions.increment();
        }
    }

    public static void endEnqueue(long startNanos, boolean player) {
        if (startNanos == 0L) return;
        long elapsed = Math.max(0L, System.nanoTime() - startNanos);
        frameEnqueues.increment();
        totalEnqueues.increment();
        frameEnqueueNanos.add(elapsed);
        if (player) {
            framePlayerEnqueues.increment();
            totalPlayerEnqueues.increment();
        }
    }

    /** Called once per frame; concurrent worker updates are safely accumulated. */
    public static synchronized void finishFrame() {
        extractionNanos[cursor] = frameExtractionNanos.sumThenReset();
        enqueueNanos[cursor] = frameEnqueueNanos.sumThenReset();
        dispatchNanos[cursor] = frameDispatchNanos.sumThenReset();
        cursor = (cursor + 1) % WINDOW;
        if (samples < WINDOW) samples++;
        lastExtractions = frameExtractions.sumThenReset();
        lastEnqueues = frameEnqueues.sumThenReset();
        lastDispatchCalls = frameDispatchCalls.sumThenReset();
        lastDispatchNanos = dispatchNanos[cursor == 0 ? WINDOW - 1 : cursor - 1];
        lastPlayerExtractions = framePlayerExtractions.sumThenReset();
        lastPlayerEnqueues = framePlayerEnqueues.sumThenReset();
    }

    public static synchronized Snapshot snapshot() {
        long extraction = 0L;
        long enqueue = 0L;
        long dispatch = 0L;
        for (int i = 0; i < samples; i++) {
            extraction += extractionNanos[i];
            enqueue += enqueueNanos[i];
            dispatch += dispatchNanos[i];
        }
        return new Snapshot(samples, lastExtractions, lastEnqueues,
                lastPlayerExtractions, lastPlayerEnqueues,
                samples == 0 ? 0.0 : extraction / (double) samples / 1_000_000.0,
                samples == 0 ? 0.0 : enqueue / (double) samples / 1_000_000.0,
                totalExtractions.sum(), totalEnqueues.sum(),
                totalPlayerExtractions.sum(), totalPlayerEnqueues.sum(),
                lastDispatchCalls, lastDispatchNanos / 1_000_000.0,
                samples == 0 ? 0.0 : dispatch / (double) samples / 1_000_000.0,
                totalDispatchCalls.sum(), totalDispatchNanos.sum());
    }

    public record Snapshot(int samples, long extractionsLastFrame, long enqueuesLastFrame,
                           long playerExtractionsLastFrame, long playerEnqueuesLastFrame,
                           double averageExtractionMillisPerFrame, double averageEnqueueMillisPerFrame,
                           long totalExtractions, long totalEnqueues,
                           long totalPlayerExtractions, long totalPlayerEnqueues,
                           long dispatchCallsLastFrame, double dispatchMillisLastFrame,
                           double averageDispatchMillisPerFrame,
                           long totalDispatchCalls, long totalDispatchNanos) { }
}
