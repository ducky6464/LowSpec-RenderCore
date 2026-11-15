package dev.lowspec.rendercore;

import java.util.concurrent.atomic.LongAdder;

/**
 * Aether's first adaptive policy. It observes per-frame entity candidate volume
 * and frame time, then adjusts render distance with hysteresis. It never changes
 * world state and never hides the local player or camera entity.
 */
public final class AdaptiveEntityVisibility {
    private static final LongAdder candidates = new LongAdder();
    private static volatile double distanceScale = 1.0;
    private static volatile int lastCandidates;
    private static int overloadedFrames;
    private static int recoveryFrames;

    private AdaptiveEntityVisibility() { }

    public static void beginFrame() {
        // LongAdder avoids a data race if visibility checks occur on a worker.
        candidates.sumThenReset();
    }

    public static void recordCandidate(boolean player) {
        candidates.increment();
    }

    public static void finishFrame(long frameNanos) {
        lastCandidates = (int) Math.min(Integer.MAX_VALUE, candidates.sumThenReset());
        RenderCoreConfig config = LowSpecRenderCoreClient.CONFIG;
        if (config == null || !config.adaptiveEntityVisibilityEnabled) {
            distanceScale = 1.0;
            overloadedFrames = 0;
            recoveryFrames = 0;
            return;
        }

        boolean entityPressure = lastCandidates > config.adaptiveEntityCandidateBudget;
        boolean framePressure = frameNanos >= 45_000_000L;
        boolean overloaded = entityPressure || framePressure;

        if (overloaded) {
            overloadedFrames++;
            recoveryFrames = 0;
            if (overloadedFrames >= 3) {
                distanceScale = Math.max(0.50, distanceScale - 0.10);
                overloadedFrames = 0;
            }
        } else if (frameNanos <= 32_000_000L && !entityPressure) {
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
