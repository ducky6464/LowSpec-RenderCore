package dev.lowspec.rendercore;

import net.minecraft.block.BlockState;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.util.math.BlockPos;

/**
 * Worker-local block-state caches for section mesh generation.
 *
 * The dense section cache uses the block's local 0..15 coordinates, avoiding
 * a 64-bit position hash for the 4096 positions in the section being built.
 * Reads outside that section use the smaller two-way region cache.
 */
public final class ChunkRegionStateCache {
    private static final ThreadLocal<Cache> LOCAL = ThreadLocal.withInitial(Cache::new);

    private ChunkRegionStateCache() { }

    public static void beginSection(ChunkRendererRegion region, int sectionX, int sectionY, int sectionZ) {
        Cache cache = LOCAL.get();
        cache.useRegion(region);
        cache.sectionX = sectionX;
        cache.sectionY = sectionY;
        cache.sectionZ = sectionZ;
        cache.sectionActive = true;
        cache.sectionGeneration++;
        if (cache.sectionGeneration == 0) {
            java.util.Arrays.fill(cache.sectionGenerations, 0);
            cache.sectionGeneration = 1;
        }
    }

    public static boolean isInActiveSection(ChunkRendererRegion region, BlockPos pos) {
        Cache cache = LOCAL.get();
        return cache.sectionActive && cache.region == region
                && (pos.getX() >> 4) == cache.sectionX
                && (pos.getY() >> 4) == cache.sectionY
                && (pos.getZ() >> 4) == cache.sectionZ;
    }

    public static BlockState lookupInSection(ChunkRendererRegion region, BlockPos pos) {
        Cache cache = LOCAL.get();
        if (!isInActiveSection(region, pos)) return null;
        int index = (pos.getX() & 15) | ((pos.getZ() & 15) << 4) | ((pos.getY() & 15) << 8);
        return cache.sectionGenerations[index] == cache.sectionGeneration
                ? cache.sectionValues[index] : null;
    }

    public static void storeInSection(ChunkRendererRegion region, BlockPos pos, BlockState state) {
        Cache cache = LOCAL.get();
        if (!isInActiveSection(region, pos)) return;
        int index = (pos.getX() & 15) | ((pos.getZ() & 15) << 4) | ((pos.getY() & 15) << 8);
        cache.sectionValues[index] = state;
        cache.sectionGenerations[index] = cache.sectionGeneration;
    }

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

    /** Releases the region snapshot and section-local state after each build. */
    public static void clear(ChunkRendererRegion region) {
        Cache cache = LOCAL.get();
        if (cache.region == region) {
            cache.region = null;
            cache.sectionActive = false;
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
        private final BlockState[] sectionValues = new BlockState[4096];
        private final int[] sectionGenerations = new int[4096];
        private ChunkRendererRegion region;
        private int generation = 1;
        private int sectionGeneration = 1;
        private int sectionX;
        private int sectionY;
        private int sectionZ;
        private boolean sectionActive;

        private void useRegion(ChunkRendererRegion nextRegion) {
            if (region == nextRegion) return;
            region = nextRegion;
            sectionActive = false;
            generation++;
            if (generation == 0) {
                java.util.Arrays.fill(generations, 0);
                generation = 1;
            }
        }
    }
}
