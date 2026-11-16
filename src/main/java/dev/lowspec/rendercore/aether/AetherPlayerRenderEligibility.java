package dev.lowspec.rendercore.aether;

/**
 * Conservative routing policy for the first base-player renderer prototype.
 * Until a custom path explicitly supports a state, that player stays on
 * Minecraft's renderer. This class does not suppress vanilla rendering.
 */
public final class AetherPlayerRenderEligibility {
    public enum Route {
        CUSTOM_BASE_MODEL,
        VANILLA_SPECTATOR,
        VANILLA_INVISIBLE,
        VANILLA_SPECIAL_POSE,
        VANILLA_SPECIAL_EFFECT,
        VANILLA_OUTLINE,
        VANILLA_EXTRA_GEOMETRY
    }

    private AetherPlayerRenderEligibility() { }

    /**
     * Only ordinary, visible, standing players are candidates for a future
     * base-model batch. All feature layers remain vanilla even for candidates.
     */
    public static Route classify(int flags) {
        if ((flags & AetherPlayerRenderQueue.SPECTATOR) != 0) return Route.VANILLA_SPECTATOR;
        if ((flags & (AetherPlayerRenderQueue.INVISIBLE
                | AetherPlayerRenderQueue.INVISIBLE_TO_PLAYER)) != 0) {
            return Route.VANILLA_INVISIBLE;
        }
        if ((flags & (AetherPlayerRenderQueue.BABY
                | AetherPlayerRenderQueue.SNEAKING
                | AetherPlayerRenderQueue.SWIMMING
                | AetherPlayerRenderQueue.GLIDING
                | AetherPlayerRenderQueue.SLEEPING
                | AetherPlayerRenderQueue.UPSIDE_DOWN
                | AetherPlayerRenderQueue.APPLY_FLYING_ROTATION)) != 0) {
            return Route.VANILLA_SPECIAL_POSE;
        }
        if ((flags & (AetherPlayerRenderQueue.HURT
                | AetherPlayerRenderQueue.SHAKING
                | AetherPlayerRenderQueue.ON_FIRE)) != 0) {
            return Route.VANILLA_SPECIAL_EFFECT;
        }
        if ((flags & AetherPlayerRenderQueue.OUTLINE) != 0) return Route.VANILLA_OUTLINE;
        if ((flags & AetherPlayerRenderQueue.EXTRA_EARS) != 0) return Route.VANILLA_EXTRA_GEOMETRY;
        return Route.CUSTOM_BASE_MODEL;
    }

    public static boolean canUseCustomBaseModel(int flags) {
        return classify(flags) == Route.CUSTOM_BASE_MODEL;
    }
}
