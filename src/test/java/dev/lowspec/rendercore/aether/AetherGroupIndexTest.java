package dev.lowspec.rendercore.aether;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AetherGroupIndexTest {
    @Test
    void groupsEqualKeysAndPreservesInputOrderWithinEachGroup() {
        AetherGroupIndex<String> groups = new AetherGroupIndex<>(6);
        String[] keys = {"skin-a", "skin-b", "skin-a", "skin-c", "skin-b", "skin-a"};

        groups.build(keys, keys.length);

        assertEquals(3, groups.groupCount());
        assertEquals(6, groups.itemCount());
        assertEquals(3, groups.groupSize(0));
        assertEquals(2, groups.groupSize(1));
        assertEquals(1, groups.groupSize(2));
        assertEquals(0, groups.representativeIndex(0));
        assertEquals(1, groups.representativeIndex(1));
        assertEquals(3, groups.representativeIndex(2));
        assertEquals(0, groups.itemIndex(0, 0));
        assertEquals(2, groups.itemIndex(0, 1));
        assertEquals(5, groups.itemIndex(0, 2));
        assertEquals(1, groups.itemIndex(1, 0));
        assertEquals(4, groups.itemIndex(1, 1));
        assertEquals(3, groups.itemIndex(2, 0));
    }

    @Test
    void supportsNullKeysAndEmptyInput() {
        AetherGroupIndex<String> groups = new AetherGroupIndex<>(3);
        String[] keys = {null, "skin", null};

        groups.build(keys, keys.length);
        assertEquals(2, groups.groupCount());
        assertEquals(2, groups.groupSize(0));
        assertEquals(1, groups.groupSize(1));
        assertEquals(2, groups.itemIndex(0, 1));

        groups.build(keys, 0);
        assertEquals(0, groups.groupCount());
        assertEquals(0, groups.itemCount());
    }

    @Test
    void rejectsInvalidCountsAndGroupOffsets() {
        AetherGroupIndex<String> groups = new AetherGroupIndex<>(2);
        String[] keys = {"a", "b"};

        assertThrows(IllegalArgumentException.class, () -> groups.build(keys, 3));
        assertThrows(IllegalArgumentException.class, () -> groups.build(keys, -1));
        groups.build(keys, 2);
        assertThrows(IndexOutOfBoundsException.class, () -> groups.itemIndex(0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> groups.groupSize(2));
    }

    @Test
    void clearAllowsReuseWithoutStaleGroupSizes() {
        AetherGroupIndex<String> groups = new AetherGroupIndex<>(3);
        String[] keys = {"a", "b", "a"};

        groups.build(keys, 3);
        groups.clear();
        groups.build(keys, 1);

        assertEquals(1, groups.groupCount());
        assertEquals(1, groups.groupSize(0));
        assertEquals(0, groups.itemIndex(0, 0));
    }

    @Test
    void rejectsZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new AetherGroupIndex<>(0));
    }
}
