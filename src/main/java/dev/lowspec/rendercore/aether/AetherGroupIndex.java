package dev.lowspec.rendercore.aether;

import java.util.Objects;

/**
 * Fixed-capacity, allocation-free grouping index for equal render resources.
 * Preserves input order within each group and never grows beyond its capacity.
 */
public final class AetherGroupIndex<T> {
    private final int capacity;
    private final int[] groupOfItem;
    private final int[] representatives;
    private final int[] starts;
    private final int[] sizes;
    private final int[] writeCursors;
    private final int[] groupedItems;
    private int groupCount;
    private int itemCount;

    public AetherGroupIndex(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        groupOfItem = new int[capacity];
        representatives = new int[capacity];
        starts = new int[capacity];
        sizes = new int[capacity];
        writeCursors = new int[capacity];
        groupedItems = new int[capacity];
    }

    /** Builds stable groups by key equality. Null keys are supported. */
    public void build(T[] keys, int count) {
        Objects.requireNonNull(keys, "keys");
        if (count < 0 || count > capacity || count > keys.length) {
            throw new IllegalArgumentException("item count exceeds grouping capacity");
        }
        clear();
        itemCount = count;

        for (int item = 0; item < count; item++) {
            T key = keys[item];
            int group = 0;
            while (group < groupCount && !Objects.equals(keys[representatives[group]], key)) {
                group++;
            }
            if (group == groupCount) {
                representatives[groupCount] = item;
                groupCount++;
            }
            groupOfItem[item] = group;
            sizes[group]++;
        }

        int offset = 0;
        for (int group = 0; group < groupCount; group++) {
            starts[group] = offset;
            writeCursors[group] = offset;
            offset += sizes[group];
        }
        for (int item = 0; item < count; item++) {
            int group = groupOfItem[item];
            groupedItems[writeCursors[group]++] = item;
        }
    }

    /** Clears previous counts while retaining all backing arrays. */
    public void clear() {
        for (int group = 0; group < groupCount; group++) sizes[group] = 0;
        groupCount = 0;
        itemCount = 0;
    }

    public int itemCount() { return itemCount; }
    public int groupCount() { return groupCount; }
    public int groupStart(int group) { checkGroup(group); return starts[group]; }
    public int groupSize(int group) { checkGroup(group); return sizes[group]; }
    public int representativeIndex(int group) { checkGroup(group); return representatives[group]; }
    public int itemIndex(int group, int offset) {
        checkGroup(group);
        if (offset < 0 || offset >= sizes[group]) {
            throw new IndexOutOfBoundsException("group offset " + offset + " of " + sizes[group]);
        }
        return groupedItems[starts[group] + offset];
    }

    private void checkGroup(int group) {
        if (group < 0 || group >= groupCount) {
            throw new IndexOutOfBoundsException("group " + group + " of " + groupCount);
        }
    }
}
