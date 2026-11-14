package dev.lowspec.rendercore;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/** Pass-scoped, direct-mapped cache for block-state reads during light propagation. */
public final class LightingStateCache {
    private static final int SIZE = 256;
    private static final int MASK = SIZE - 1;
    private static final ThreadLocal<Cache> LOCAL = ThreadLocal.withInitial(Cache::new);

    private LightingStateCache() { }

    public static void beginPass() { LOCAL.get().begin(); }
    public static void endPass() { LOCAL.get().active = false; }

    public static BlockState lookup(BlockPos pos) {
        Cache cache = LOCAL.get();
        if (!cache.active) return null;
        long key = pos.asLong();
        int slot = mix(key) & MASK;
        return cache.generations[slot] == cache.generation && cache.positions[slot] == key
                ? cache.states[slot] : null;
    }

    public static void store(BlockPos pos, BlockState state) {
        Cache cache = LOCAL.get();
        if (!cache.active || state == null) return;
        long key = pos.asLong();
        int slot = mix(key) & MASK;
        cache.positions[slot] = key;
        cache.states[slot] = state;
        cache.generations[slot] = cache.generation;
    }

    private static int mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return (int) value;
    }

    private static final class Cache {
        private final long[] positions = new long[SIZE];
        private final BlockState[] states = new BlockState[SIZE];
        private final int[] generations = new int[SIZE];
        private int generation;
        private boolean active;

        private void begin() {
            generation++;
            if (generation == 0) {
                java.util.Arrays.fill(generations, 0);
                generation = 1;
            }
            active = true;
        }
    }
}
