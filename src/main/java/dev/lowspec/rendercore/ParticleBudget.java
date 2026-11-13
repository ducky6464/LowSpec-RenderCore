package dev.lowspec.rendercore;

/** Limits particle admissions during a single client tick to reduce burst work. */
public final class ParticleBudget {
    private static int admittedThisTick;

    private ParticleBudget() { }

    public static void resetForNewTick() {
        admittedThisTick = 0;
    }

    public static boolean tryAdmit() {
        RenderCoreConfig config = LowSpecRenderCoreClient.CONFIG;
        if (config == null || !config.particleBudgetEnabled) return true;
        if (admittedThisTick >= config.maxParticlesPerTick) return false;
        admittedThisTick++;
        return true;
    }
}
