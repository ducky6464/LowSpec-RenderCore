package dev.lowspec.rendercore;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.concurrent.atomic.LongAdder;

/**
 * Pass-scoped, two-way set-associative cache for repeated block-state reads
 * during vanilla light propagation. Every pass receives a fresh generation.
 */
public final class LightingStateCache {
    private static final int SET_COUNT = 1024;
    private static final int ENTRY_COUNT = SET_COUNT * 2;
    private static final int MASK = SET_COUNT - 1;
    private static final ThreadLocal<Cache> LOCAL = ThreadLocal.withInitial(Cache::new);
    private static final LongAdder HITS = new LongAdder();
    private static final LongAdder MISSES = new LongAdder();

    private LightingStateCache() { }

    public static void beginPass() {
        LOCAL.get().begin();
        LightingShapeCache.beginPass();
    }

    public static void endPass() {
        LOCAL.get().active = false;
        LightingShapeCache.endPass();
    }

    public static BlockState lookup(BlockPos pos) {
        Cache cache = LOCAL.get();
        if (!cache.active) return null;
        long key = pos.asLong();
        int first = mix(key) & MASK;
        int second = first + SET_COUNT;
        if (cache.generations[first] == cache.generation && cache.positions[first] == key) {
            HITS.increment();
            return cache.states[first];
        }
        if (cache.generations[second] == cache.generation && cache.positions[second] == key) {
            HITS.increment();
            return cache.states[second];
        }
        MISSES.increment();
        return null;
    }

    public static void store(BlockPos pos, BlockState state) {
        Cache cache = LOCAL.get();
        if (!cache.active || state == null) return;
        long key = pos.asLong();
        int set = mix(key) & MASK;
        int first = set;
        int second = set + SET_COUNT;
        int target;
        if (cache.generations[first] == cache.generation && cache.positions[first] == key) {
            target = first;
        } else if (cache.generations[second] == cache.generation && cache.positions[second] == key) {
            target = second;
        } else {
            target = cache.replaceSecond[set] ? second : first;
            cache.replaceSecond[set] = !cache.replaceSecond[set];
        }
        cache.positions[target] = key;
        cache.states[target] = state;
        cache.generations[target] = cache.generation;
    }

    public static Snapshot snapshot() {
        long hits = HITS.sum();
        long misses = MISSES.sum();
        long total = hits + misses;
        return new Snapshot(hits, misses, total == 0L ? 0.0 : (double) hits / total * 100.0);
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
        private final long[] positions = new long[ENTRY_COUNT];
        private final BlockState[] states = new BlockState[ENTRY_COUNT];
        private final int[] generations = new int[ENTRY_COUNT];
        private final boolean[] replaceSecond = new boolean[SET_COUNT];
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

    public record Snapshot(long hits, long misses, double hitRatePercent) { }
}
