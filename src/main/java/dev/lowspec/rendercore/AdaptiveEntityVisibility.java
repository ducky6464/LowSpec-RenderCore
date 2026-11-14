package dev.lowspec.rendercore;

/**
 * Aether's first adaptive policy. It observes per-frame entity candidate volume
 * and frame time, then adjusts non-player render distance with hysteresis.
 * It never changes world state and never hides the local or remote player.
 */
public final class AdaptiveEntityVisibility {
    private static int candidates;
    private static volatile double distanceScale = 1.0;
    private static volatile int lastCandidates;
    private static int overloadedFrames;
    private static int recoveryFrames;

    private AdaptiveEntityVisibility() { }

    public static void beginFrame() {
        candidates = 0;
    }

    public static void recordCandidate(boolean player) {
        candidates++;
    }

    public static void finishFrame(long frameNanos) {
        RenderCoreConfig config = LowSpecRenderCoreClient.CONFIG;
        if (config == null || !config.adaptiveEntityVisibilityEnabled) {
            distanceScale = 1.0;
            return;
        }

        lastCandidates = candidates;
        boolean entityPressure = candidates > config.adaptiveEntityCandidateBudget;
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
