package dev.lowspec.rendercore;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.util.math.BlockPos;

/**
 * Small per-worker direct-mapped cache for repeated block reads while a section mesh is built.
 * Entries are scoped to one immutable ChunkRendererRegion at a time.
 */
public final class ChunkRegionStateCache {
    private static final ThreadLocal<Cache> LOCAL = ThreadLocal.withInitial(Cache::new);

    private ChunkRegionStateCache() { }

    public static BlockState lookup(ChunkRendererRegion region, BlockPos pos) {
        Cache cache = LOCAL.get();
        cache.useRegion(region);
        long key = pos.asLong();
        int slot = mix(key) & (Cache.SIZE - 1);
        if (cache.generations[slot] == cache.generation && cache.keys[slot] == key) {
            return cache.values[slot];
        }
        return null;
    }

    public static void store(ChunkRendererRegion region, BlockPos pos, BlockState state) {
        Cache cache = LOCAL.get();
        cache.useRegion(region);
        long key = pos.asLong();
        int slot = mix(key) & (Cache.SIZE - 1);
        cache.keys[slot] = key;
        cache.values[slot] = state;
        cache.generations[slot] = cache.generation;
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
        private static final int SIZE = 4096;
        private final long[] keys = new long[SIZE];
        private final BlockState[] values = new BlockState[SIZE];
        private final int[] generations = new int[SIZE];
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
