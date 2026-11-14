package dev.lowspec.rendercore;

/**
 * Limits particle admissions during a client tick. When the client tick is already
 * CPU-heavy, the admission ceiling is reduced for that tick to avoid compounding
 * the spike with a large particle burst.
 */
public final class ParticleBudget {
    private static int admittedThisTick;
    private static long tickStartNanos;

    private ParticleBudget() { }

    public static void resetForNewTick() {
        admittedThisTick = 0;
        tickStartNanos = System.nanoTime();
    }

    public static int getAdmittedThisTick() {
        return admittedThisTick;
    }

    public static boolean tryAdmit() {
        RenderCoreConfig config = LowSpecRenderCoreClient.CONFIG;
        if (config == null || !config.particleBudgetEnabled) return true;

        int limit = config.maxParticlesPerTick;
        long start = tickStartNanos;
        if (start != 0L) {
            double elapsedMillis = Math.max(0L, System.nanoTime() - start) / 1_000_000.0;
            if (elapsedMillis >= 40.0) {
                limit = Math.max(50, limit / 4);
            } else if (elapsedMillis >= 30.0) {
                limit = Math.max(50, limit / 2);
            } else if (elapsedMillis >= 20.0) {
                limit = Math.max(50, (limit * 3) / 4);
            }
        }

        if (admittedThisTick >= limit) return false;
        admittedThisTick++;
        return true;
    }
}
