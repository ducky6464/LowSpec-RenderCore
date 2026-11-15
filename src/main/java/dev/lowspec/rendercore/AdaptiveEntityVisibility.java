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
    private static volatile double distanceScale = 1.0;
    private static volatile int lastCandidates;
    private static int overloadedFrames;
    private static int recoveryFrames;

    private static final long ENTITY_OVERLOAD_NANOS = 8_000_000L;
    private static final long ENTITY_RECOVERY_NANOS = 4_000_000L;

    private AdaptiveEntityVisibility() { }

    public static void beginFrame() {
        // LongAdder avoids a data race if visibility checks occur on a worker.
        candidates.sumThenReset();
    }

    public static void recordCandidate(boolean player) {
        candidates.increment();
    }

    public static void finishFrame() {
        lastCandidates = (int) Math.min(Integer.MAX_VALUE, candidates.sumThenReset());
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

    public static double getDistanceScale() {
        return distanceScale;
    }

    public static int getLastCandidateCount() {
        return lastCandidates;
    }
}
