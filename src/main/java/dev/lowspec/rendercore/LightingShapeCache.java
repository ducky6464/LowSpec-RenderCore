package dev.lowspec.rendercore;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

import java.util.concurrent.atomic.LongAdder;

/**
 * Pass-scoped cache for the expensive, deterministic full-face coverage test
 * used by vanilla light propagation. Keys are compared by identity because
 * BlockState instances are immutable canonical state objects.
 */
public final class LightingShapeCache {
    private static final int SET_COUNT = 256;
    private static final int ENTRY_COUNT = SET_COUNT * 2;
    private static final int MASK = SET_COUNT - 1;
    private static final ThreadLocal<Cache> LOCAL = ThreadLocal.withInitial(Cache::new);
    private static final LongAdder HITS = new LongAdder();
    private static final LongAdder MISSES = new LongAdder();

    private LightingShapeCache() { }

    public static void beginPass() { LOCAL.get().begin(); }
    public static void endPass() { LOCAL.get().active = false; }

    public static Boolean lookup(BlockState source, BlockState target, Direction direction) {
        Cache cache = LOCAL.get();
        if (!cache.active) return null;
        int first = hash(source, target, direction) & MASK;
        int second = first + SET_COUNT;
        if (matches(cache, first, source, target, direction)) {
            HITS.increment();
            return cache.results[first];
        }
        if (matches(cache, second, source, target, direction)) {
            HITS.increment();
            return cache.results[second];
        }
        MISSES.increment();
        return null;
    }

    public static void store(BlockState source, BlockState target, Direction direction, boolean result) {
        Cache cache = LOCAL.get();
        if (!cache.active) return;
        int set = hash(source, target, direction) & MASK;
        int first = set;
        int second = set + SET_COUNT;
        int slot;
        if (matches(cache, first, source, target, direction)) {
            slot = first;
        } else if (matches(cache, second, source, target, direction)) {
            slot = second;
        } else {
            slot = cache.replaceSecond[set] ? second : first;
            cache.replaceSecond[set] = !cache.replaceSecond[set];
        }
        cache.sources[slot] = source;
        cache.targets[slot] = target;
        cache.directions[slot] = (byte) direction.ordinal();
        cache.results[slot] = result;
        cache.generations[slot] = cache.generation;
    }

    public static Snapshot snapshot() {
        long hits = HITS.sum();
        long misses = MISSES.sum();
        long total = hits + misses;
        return new Snapshot(hits, misses, total == 0L ? 0.0 : (double) hits / total * 100.0);
    }

    private static boolean matches(Cache cache, int slot, BlockState source, BlockState target, Direction direction) {
        return cache.generations[slot] == cache.generation
                && cache.sources[slot] == source
                && cache.targets[slot] == target
                && cache.directions[slot] == direction.ordinal();
    }

    private static int hash(BlockState source, BlockState target, Direction direction) {
        int value = System.identityHashCode(source);
        value = 31 * value + System.identityHashCode(target);
        return 31 * value + direction.ordinal();
    }

    private static final class Cache {
        private final BlockState[] sources = new BlockState[ENTRY_COUNT];
        private final BlockState[] targets = new BlockState[ENTRY_COUNT];
        private final byte[] directions = new byte[ENTRY_COUNT];
        private final boolean[] results = new boolean[ENTRY_COUNT];
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
