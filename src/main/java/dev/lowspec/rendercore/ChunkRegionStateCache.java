package dev.lowspec.rendercore;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.util.math.BlockPos;

/**
 * Two-way set-associative per-worker cache for repeated block reads during a section mesh build.
 * Entries are scoped to one immutable ChunkRendererRegion snapshot at a time.
 */
public final class ChunkRegionStateCache {
    private static final ThreadLocal<Cache> LOCAL = ThreadLocal.withInitial(Cache::new);

    private ChunkRegionStateCache() { }

    public static BlockState lookup(ChunkRendererRegion region, BlockPos pos) {
        Cache cache = LOCAL.get();
        cache.useRegion(region);
        long key = pos.asLong();
        int first = mix(key) & (Cache.SET_COUNT - 1);
        int second = first + Cache.SET_COUNT;
        if (cache.generations[first] == cache.generation && cache.keys[first] == key) {
            return cache.values[first];
        }
        if (cache.generations[second] == cache.generation && cache.keys[second] == key) {
            return cache.values[second];
        }
        return null;
    }

    public static void store(ChunkRendererRegion region, BlockPos pos, BlockState state) {
        Cache cache = LOCAL.get();
        cache.useRegion(region);
        long key = pos.asLong();
        int set = mix(key) & (Cache.SET_COUNT - 1);
        int first = set;
        int second = set + Cache.SET_COUNT;
        int target;
        if (cache.generations[first] == cache.generation && cache.keys[first] == key) {
            target = first;
        } else if (cache.generations[second] == cache.generation && cache.keys[second] == key) {
            target = second;
        } else {
            target = cache.replaceSecond[set] ? second : first;
            cache.replaceSecond[set] = !cache.replaceSecond[set];
        }
        cache.keys[target] = key;
        cache.values[target] = state;
        cache.generations[target] = cache.generation;
    }

    /** Releases the region snapshot as soon as its section build finishes. */
    public static void clear(ChunkRendererRegion region) {
        Cache cache = LOCAL.get();
        if (cache.region == region) {
            cache.region = null;
            cache.generation++;
            if (cache.generation == 0) {
                java.util.Arrays.fill(cache.generations, 0);
                cache.generation = 1;
            }
        }
    }

    private static int mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        return (int) value;
    }

    private static final class Cache {
        private static final int SET_COUNT = 2048;
        private final long[] keys = new long[SET_COUNT * 2];
        private final BlockState[] values = new BlockState[SET_COUNT * 2];
        private final int[] generations = new int[SET_COUNT * 2];
        private final boolean[] replaceSecond = new boolean[SET_COUNT];
        private ChunkRendererRegion region;
        private int generation = 1;

        private void useRegion(ChunkRendererRegion nextRegion) {
            if (region == nextRegion) return;
            region = nextRegion;
            generation++;
            if (generation == 0) {
                java.util.Arrays.fill(generations, 0);
                generation = 1;
            }
        }
    }
}
