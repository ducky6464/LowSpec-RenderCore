package dev.lowspec.rendercore;

import dev.lowspec.rendercore.mixin.WorldRendererInvoker;
import net.minecraft.client.render.WorldRenderer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Paces chunk rebuild requests without dropping them. Important rebuilds are
 * drained before ordinary rebuilds. Requests for the same section are coalesced.
 */
public final class ChunkRenderScheduler {
    private static final int MAX_PENDING = 16_384;
    private static final LinkedHashMap<SectionKey, Boolean> IMPORTANT = new LinkedHashMap<>();
    private static final LinkedHashMap<SectionKey, Boolean> NORMAL = new LinkedHashMap<>();
    private static Object currentWorld;
    private static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> false);

    private ChunkRenderScheduler() {}

    public static synchronized boolean enqueue(int x, int y, int z, boolean important) {
        SectionKey key = new SectionKey(x, y, z);
        if (IMPORTANT.containsKey(key) || NORMAL.containsKey(key)) {
            if (important) {
                NORMAL.remove(key);
                IMPORTANT.putIfAbsent(key, true);
            }
            return true;
        }
        if (IMPORTANT.size() + NORMAL.size() >= MAX_PENDING) return false;
        (important ? IMPORTANT : NORMAL).put(key, important);
        return true;
    }

    public static void drain(WorldRenderer renderer, Object world, int budget) {
        synchronized (ChunkRenderScheduler.class) {
            if (world != currentWorld) {
                IMPORTANT.clear();
                NORMAL.clear();
                currentWorld = world;
            }
        }
        if (world == null || budget <= 0) return;

        WorldRendererInvoker invoker = (WorldRendererInvoker) renderer;
        int remaining = budget;
        REPLAYING.set(true);
        try {
            while (remaining-- > 0) {
                Map.Entry<SectionKey, Boolean> entry = pollNext();
                if (entry == null) break;
                SectionKey key = entry.getKey();
                try {
                    invoker.lowspec$invokeScheduleChunkRender(key.x(), key.y(), key.z(), entry.getValue());
                } catch (RuntimeException | Error failure) {
                    synchronized (ChunkRenderScheduler.class) {
                        LinkedHashMap<SectionKey, Boolean> target = entry.getValue() ? IMPORTANT : NORMAL;
                        target.putIfAbsent(key, entry.getValue());
                    }
                    throw failure;
                }
            }
        } finally {
            REPLAYING.remove();
        }
    }

    private static synchronized Map.Entry<SectionKey, Boolean> pollNext() {
        Iterator<Map.Entry<SectionKey, Boolean>> it = IMPORTANT.entrySet().iterator();
        if (it.hasNext()) {
            Map.Entry<SectionKey, Boolean> entry = it.next();
            Map.Entry<SectionKey, Boolean> copy = Map.entry(entry.getKey(), true);
            it.remove();
            return copy;
        }
        it = NORMAL.entrySet().iterator();
        if (it.hasNext()) {
            Map.Entry<SectionKey, Boolean> entry = it.next();
            Map.Entry<SectionKey, Boolean> copy = Map.entry(entry.getKey(), false);
            it.remove();
            return copy;
        }
        return null;
    }

    public static boolean isReplaying() { return REPLAYING.get(); }

    public static synchronized int pendingCount() {
        return IMPORTANT.size() + NORMAL.size();
    }

    public static synchronized void clear() {
        IMPORTANT.clear();
        NORMAL.clear();
        currentWorld = null;
    }

    private record SectionKey(int x, int y, int z) {}
}
