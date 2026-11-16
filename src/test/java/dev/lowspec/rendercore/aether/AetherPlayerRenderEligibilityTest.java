package dev.lowspec.rendercore.aether;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AetherPlayerRenderEligibilityTest {
    @Test
    void ordinaryVisibleStandingPlayerIsCustomCandidate() {
        assertEquals(AetherPlayerRenderEligibility.Route.CUSTOM_BASE_MODEL,
                AetherPlayerRenderEligibility.classify(0));
        assertTrue(AetherPlayerRenderEligibility.canUseCustomBaseModel(0));
    }

    @Test
    void spectatorAndInvisibleStatesRemainVanilla() {
        assertEquals(AetherPlayerRenderEligibility.Route.VANILLA_SPECTATOR,
                AetherPlayerRenderEligibility.classify(AetherPlayerRenderQueue.SPECTATOR));
        assertEquals(AetherPlayerRenderEligibility.Route.VANILLA_INVISIBLE,
                AetherPlayerRenderEligibility.classify(AetherPlayerRenderQueue.INVISIBLE));
        assertEquals(AetherPlayerRenderEligibility.Route.VANILLA_INVISIBLE,
                AetherPlayerRenderEligibility.classify(AetherPlayerRenderQueue.INVISIBLE_TO_PLAYER));
    }

    @Test
    void unsupportedPosesRemainVanilla() {
        int[] flags = {
                AetherPlayerRenderQueue.BABY,
                AetherPlayerRenderQueue.SNEAKING,
                AetherPlayerRenderQueue.SWIMMING,
                AetherPlayerRenderQueue.GLIDING,
                AetherPlayerRenderQueue.SLEEPING,
                AetherPlayerRenderQueue.UPSIDE_DOWN,
                AetherPlayerRenderQueue.APPLY_FLYING_ROTATION
        };
        for (int flag : flags) {
            assertEquals(AetherPlayerRenderEligibility.Route.VANILLA_SPECIAL_POSE,
                    AetherPlayerRenderEligibility.classify(flag));
        }
    }

    @Test
    void specialEffectsAndOutlinesRemainVanilla() {
        assertEquals(AetherPlayerRenderEligibility.Route.VANILLA_SPECIAL_EFFECT,
                AetherPlayerRenderEligibility.classify(AetherPlayerRenderQueue.HURT));
        assertEquals(AetherPlayerRenderEligibility.Route.VANILLA_SPECIAL_EFFECT,
                AetherPlayerRenderEligibility.classify(AetherPlayerRenderQueue.SHAKING));
        assertEquals(AetherPlayerRenderEligibility.Route.VANILLA_SPECIAL_EFFECT,
                AetherPlayerRenderEligibility.classify(AetherPlayerRenderQueue.ON_FIRE));
        assertEquals(AetherPlayerRenderEligibility.Route.VANILLA_OUTLINE,
                AetherPlayerRenderEligibility.classify(AetherPlayerRenderQueue.OUTLINE));
        assertEquals(AetherPlayerRenderEligibility.Route.VANILLA_EXTRA_GEOMETRY,
                AetherPlayerRenderEligibility.classify(AetherPlayerRenderQueue.EXTRA_EARS));
    }

    @Test
    void mixedFlagsChooseTheFirstConservativeFallback() {
        assertEquals(AetherPlayerRenderEligibility.Route.VANILLA_INVISIBLE,
                AetherPlayerRenderEligibility.classify(
                        AetherPlayerRenderQueue.INVISIBLE | AetherPlayerRenderQueue.GLIDING));
    }
}
