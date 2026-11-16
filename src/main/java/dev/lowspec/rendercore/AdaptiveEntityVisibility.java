package dev.lowspec.rendercore;

import java.util.concurrent.atomic.LongAdder;

/**
 * Aether's first adaptive policy. It observes entity candidate volume and
 * CPU time spent specifically in entity extraction/enqueue, then adjusts
 * render distance with hysteresis. Unrelated chunk, GUI, and GPU frame costs
 * do not trigger entity throttling.
 */
public final class AdaptiveEntityVisibility {
    private static final LongAdder candidates = new LongAdder();
    private static final LongAdder playerCandidates = new LongAdder();
    private static volatile double distanceScale = 1.0;
    private static volatile int lastCandidates;
    private static volatile int lastPlayerCandidates;
    private static int overloadedFrames;
    private static int recoveryFrames;

    private static final long ENTITY_OVERLOAD_NANOS = 8_000_000L;
    private static final long ENTITY_RECOVERY_NANOS = 4_000_000L;

    private AdaptiveEntityVisibility() { }

    public static void beginFrame() {
        // Visibility checks can run on worker threads; LongAdder avoids a
        // contended global lock on the hot path.
        candidates.sumThenReset();
        playerCandidates.sumThenReset();
    }

    public static void recordCandidate(boolean player) {
        candidates.increment();
        if (player) playerCandidates.increment();
    }

    public static void finishFrame() {
        lastCandidates = (int) Math.min(Integer.MAX_VALUE, candidates.sumThenReset());
        lastPlayerCandidates = (int) Math.min(Integer.MAX_VALUE, playerCandidates.sumThenReset());
        RenderCoreConfig config = LowSpecRenderCoreClient.CONFIG;
        if (config == null || !config.adaptiveEntityVisibilityEnabled) {
            distanceScale = 1.0;
            overloadedFrames = 0;
            recoveryFrames = 0;
            return;
        }

        long entityCpuNanos = EntityRenderMetrics.lastFrameEntityCpuNanos();
        boolean candidatePressure = lastCandidates > config.adaptiveEntityCandidateBudget;
        boolean entityCpuPressure = entityCpuNanos >= ENTITY_OVERLOAD_NANOS;
        boolean overloaded = candidatePressure || entityCpuPressure;

        if (overloaded) {
            overloadedFrames++;
            recoveryFrames = 0;
            if (overloadedFrames >= 3) {
                distanceScale = Math.max(0.50, distanceScale - 0.10);
                overloadedFrames = 0;
            }
        } else if (entityCpuNanos <= ENTITY_RECOVERY_NANOS && !candidatePressure) {
            recoveryFrames++;
            overloadedFrames = 0;
            if (recoveryFrames >= 12) {
                distanceScale = Math.min(1.0, distanceScale + 0.05);
                recoveryFrames = 0;
            }
        } else {
            overloadedFrames = 0;
            recoveryFrames = 0;
        }
    }

    /**
     * Applies adaptive scaling without ever exceeding the user's configured
     * distance. A minimum protects nearby entities only when the configured
     * distance itself is at least that large.
     */
    public static int scaledDistance(int configuredDistance, int preferredMinimum, double scale) {
        if (configuredDistance <= 0) return configuredDistance;
        int safeMinimum = Math.min(configuredDistance, Math.max(0, preferredMinimum));
        int scaled = (int) Math.floor(configuredDistance * Math.max(0.0, Math.min(1.0, scale)));
        return Math.min(configuredDistance, Math.max(safeMinimum, scaled));
    }

    public static double getDistanceScale() { return distanceScale; }
    public static int getLastCandidateCount() { return lastCandidates; }
    public static int getLastPlayerCandidateCount() { return lastPlayerCandidates; }
}
