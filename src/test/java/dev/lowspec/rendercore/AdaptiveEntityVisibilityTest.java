package dev.lowspec.rendercore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptiveEntityVisibilityTest {
    @Test
    void neverExpandsDistanceBeyondConfiguredValue() {
        assertEquals(16, AdaptiveEntityVisibility.scaledDistance(16, 24, 0.50));
        assertEquals(24, AdaptiveEntityVisibility.scaledDistance(24, 24, 0.50));
    }

    @Test
    void scalesLargeDistancesButKeepsNearbyMinimum() {
        assertEquals(50, AdaptiveEntityVisibility.scaledDistance(100, 24, 0.50));
        assertEquals(24, AdaptiveEntityVisibility.scaledDistance(100, 24, 0.10));
        assertEquals(100, AdaptiveEntityVisibility.scaledDistance(100, 24, 1.0));
    }

    @Test
    void clampsScaleAndPreferredMinimumToSafeRange() {
        assertEquals(100, AdaptiveEntityVisibility.scaledDistance(100, 24, 2.0));
        assertEquals(24, AdaptiveEntityVisibility.scaledDistance(100, 24, -1.0));
        assertEquals(0, AdaptiveEntityVisibility.scaledDistance(100, -5, 0.0));
        assertEquals(12, AdaptiveEntityVisibility.scaledDistance(12, 24, 0.25));
    }

    @Test
    void preservesDisabledAndZeroDistances() {
        assertEquals(-1, AdaptiveEntityVisibility.scaledDistance(-1, 24, 0.5));
        assertEquals(0, AdaptiveEntityVisibility.scaledDistance(0, 24, 0.5));
    }
}
