package dev.lowspec.rendercore.aether;

public final class AetherPlayerBatchPlan {
    private final int capacity;
    private final int[] batchSkinGroups, batchStarts, batchSizes, playerIndices;
    private int batchCount, candidateCount, fallbackCount, playerCount;

    public AetherPlayerBatchPlan(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        batchSkinGroups = new int[capacity];
        batchStarts = new int[capacity];
        batchSizes = new int[capacity];
        playerIndices = new int[capacity];
    }

    /** Packs eligible players into stable per-skin batches without allocating. */
    public void build(AetherGroupIndex<?> skinGroups, int[] flags, int count) {
        if (skinGroups == null) throw new NullPointerException("skinGroups");
        if (flags == null) throw new NullPointerException("flags");
        if (count < 0 || count > capacity || count > flags.length || count != skinGroups.itemCount()) {
            throw new IllegalArgumentException("player count does not match batch capacity and skin groups");
        }
        clear();
        playerCount = count;
        for (int group = 0; group < skinGroups.groupCount(); group++) {
            int eligibleInGroup = 0;
            for (int offset = 0; offset < skinGroups.groupSize(group); offset++) {
                int player = skinGroups.itemIndex(group, offset);
                if (AetherPlayerRenderEligibility.canUseCustomBaseModel(flags[player])) eligibleInGroup++;
                else fallbackCount++;
            }
            if (eligibleInGroup == 0) continue;
            int batch = batchCount++;
            batchSkinGroups[batch] = group;
            batchStarts[batch] = candidateCount;
            batchSizes[batch] = eligibleInGroup;
            for (int offset = 0; offset < skinGroups.groupSize(group); offset++) {
                int player = skinGroups.itemIndex(group, offset);
                if (AetherPlayerRenderEligibility.canUseCustomBaseModel(flags[player])) {
                    playerIndices[candidateCount++] = player;
                }
            }
        }
    }

    public void clear() {
        batchCount = candidateCount = fallbackCount = playerCount = 0;
    }

    public int batchCount() { return batchCount; }
    public int candidateCount() { return candidateCount; }
    public int fallbackCount() { return fallbackCount; }
    public int playerCount() { return playerCount; }
    public int batchSkinGroup(int batch) { checkBatch(batch); return batchSkinGroups[batch]; }
    public int batchStart(int batch) { checkBatch(batch); return batchStarts[batch]; }
    public int batchSize(int batch) { checkBatch(batch); return batchSizes[batch]; }

    public int playerIndex(int batch, int offset) {
        checkBatch(batch);
        if (offset < 0 || offset >= batchSizes[batch]) {
            throw new IndexOutOfBoundsException("batch offset " + offset + " of " + batchSizes[batch]);
        }
        return playerIndices[batchStarts[batch] + offset];
    }

    private void checkBatch(int batch) {
        if (batch < 0 || batch >= batchCount) throw new IndexOutOfBoundsException("batch " + batch + " of " + batchCount);
    }
}
