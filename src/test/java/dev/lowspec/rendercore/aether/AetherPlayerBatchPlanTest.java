package dev.lowspec.rendercore.aether;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AetherPlayerBatchPlanTest {
    @Test
    void createsStableBatchesAndExcludesFallbackPlayers() {
        AetherGroupIndex<String> groups = new AetherGroupIndex<>(6);
        groups.build(new String[]{"a", "b", "a", "b", "a", "c"}, 6);
        int[] flags = {0, AetherPlayerRenderQueue.SNEAKING, 0, AetherPlayerRenderQueue.HURT,
                AetherPlayerRenderQueue.OUTLINE, 0};
        AetherPlayerBatchPlan plan = new AetherPlayerBatchPlan(6);
        plan.build(groups, flags, 6);
        assertEquals(2, plan.batchCount());
        assertEquals(3, plan.candidateCount());
        assertEquals(3, plan.fallbackCount());
        assertEquals(0, plan.batchSkinGroup(0));
        assertEquals(2, plan.batchSize(0));
        assertEquals(0, plan.playerIndex(0, 0));
        assertEquals(2, plan.playerIndex(0, 1));
        assertEquals(2, plan.batchSkinGroup(1));
        assertEquals(1, plan.batchSize(1));
        assertEquals(5, plan.playerIndex(1, 0));
    }

    @Test
    void omitsGroupsWithNoEligiblePlayers() {
        AetherGroupIndex<String> groups = new AetherGroupIndex<>(3);
        groups.build(new String[]{"a", "a", "b"}, 3);
        AetherPlayerBatchPlan plan = new AetherPlayerBatchPlan(3);
        plan.build(groups, new int[]{AetherPlayerRenderQueue.SNEAKING, AetherPlayerRenderQueue.HURT, 0}, 3);
        assertEquals(1, plan.batchCount());
        assertEquals(1, plan.batchSkinGroup(0));
        assertEquals(2, plan.fallbackCount());
    }

    @Test
    void rebuildClearsOldBatchCounts() {
        AetherGroupIndex<String> groups = new AetherGroupIndex<>(3);
        AetherPlayerBatchPlan plan = new AetherPlayerBatchPlan(3);
        groups.build(new String[]{"a", "b", "a"}, 3);
        plan.build(groups, new int[]{0, 0, 0}, 3);
        assertEquals(2, plan.batchCount());
        groups.build(new String[]{"z"}, 1);
        plan.build(groups, new int[]{AetherPlayerRenderQueue.SPECTATOR}, 1);
        assertEquals(0, plan.batchCount());
        assertEquals(1, plan.fallbackCount());
        assertEquals(1, plan.playerCount());
    }

    @Test
    void rejectsInvalidCountsAndCapacity() {
        AetherGroupIndex<String> groups = new AetherGroupIndex<>(2);
        groups.build(new String[]{"a"}, 1);
        AetherPlayerBatchPlan plan = new AetherPlayerBatchPlan(2);
        assertThrows(IllegalArgumentException.class, () -> plan.build(groups, new int[2], 2));
        assertThrows(IllegalArgumentException.class, () -> plan.build(groups, new int[0], 1));
        assertThrows(IllegalArgumentException.class, () -> new AetherPlayerBatchPlan(0));
        assertThrows(IndexOutOfBoundsException.class, () -> plan.batchSize(0));
    }
}
