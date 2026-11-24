package mappa.core.inventory;

import mappa.core.items.ItemDefinition;
import mappa.core.items.ItemInstance;
import mappa.core.items.ItemSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * A fixed number of slots. Stackable items (consumables, keys, resources without a
 * rarity) share stacks of up to {@link #MAX_STACK}; everything else takes one slot each.
 */
public class Inventory {
    public static final int MAX_STACK = 99;

    private final int capacity;
    private final List<ItemStack> stacks = new ArrayList<>();

    public Inventory(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    public int usedSlots() {
        return stacks.size();
    }

    public int freeSlots() {
        return capacity - stacks.size();
    }

    public boolean isFull() {
        return freeSlots() == 0;
    }

    public boolean isEmpty() {
        return stacks.isEmpty();
    }

    public List<ItemStack> stacks() {
        return Collections.unmodifiableList(stacks);
    }

    /** How many of this item could be added right now. */
    public int spaceFor(ItemInstance item) {
        if (!item.isStackable()) {
            return freeSlots();
        }
        int space = freeSlots() * MAX_STACK;
        for (ItemStack s : stacks) {
            if (canStack(s, item)) {
                space += MAX_STACK - s.count();
            }
        }
        return space;
    }

    /** Adds one item. Returns false (and adds nothing) if there is no room. */
    public boolean add(ItemInstance item) {
        return add(item, 1) == 1;
    }

    /**
     * Adds up to {@code count} of a stackable item, or exactly one gear item.
     * Returns how many were actually added (0 when full — never loops or throws).
     */
    public int add(ItemInstance item, int count) {
        if (count <= 0) {
            return 0;
        }
        if (!item.isStackable()) {
            if (count != 1) {
                throw new IllegalArgumentException("Non-stackable items must be added one instance at a time");
            }
            if (containsInstance(item)) {
                throw new IllegalArgumentException("Instance already in inventory: " + item);
            }
            if (isFull()) {
                return 0;
            }
            stacks.add(new ItemStack(item, 1));
            return 1;
        }
        int remaining = count;
        for (ItemStack s : stacks) {
            if (remaining == 0) {
                break;
            }
            if (canStack(s, item)) {
                int put = Math.min(remaining, MAX_STACK - s.count());
                s.setCount(s.count() + put);
                remaining -= put;
            }
        }
        while (remaining > 0 && !isFull()) {
            int put = Math.min(remaining, MAX_STACK);
            stacks.add(new ItemStack(item, put));
            remaining -= put;
        }
        return count - remaining;
    }

    /** Total count of items with this definition id. */
    public int count(String definitionId) {
        int n = 0;
        for (ItemStack s : stacks) {
            if (s.item().definitionId().equals(definitionId)) {
                n += s.count();
            }
        }
        return n;
    }

    /** Removes up to {@code count} items of a definition. Returns how many were removed. */
    public int removeByDefinition(String definitionId, int count) {
        int remaining = count;
        for (int i = stacks.size() - 1; i >= 0 && remaining > 0; i--) {
            ItemStack s = stacks.get(i);
            if (s.item().definitionId().equals(definitionId)) {
                int take = Math.min(remaining, s.count());
                s.setCount(s.count() - take);
                remaining -= take;
                if (s.count() == 0) {
                    stacks.remove(i);
                }
            }
        }
        return count - remaining;
    }

    /** Removes one of this exact item (or one from its stack). */
    public boolean remove(ItemInstance item) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack s = stacks.get(i);
            if (s.item() == item) {
                s.setCount(s.count() - 1);
                if (s.count() == 0) {
                    stacks.remove(i);
                }
                return true;
            }
        }
        return false;
    }

    public boolean containsInstance(ItemInstance item) {
        for (ItemStack s : stacks) {
            if (s.item() == item) {
                return true;
            }
        }
        return false;
    }

    public List<ItemStack> find(Predicate<ItemInstance> filter) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : stacks) {
            if (filter.test(s.item())) {
                out.add(s);
            }
        }
        return out;
    }

    public List<ItemStack> ofSlot(ItemSlot slot) {
        return find(i -> i.slot() == slot);
    }

    /** First stack of a definition, or null. */
    public ItemStack firstOf(ItemDefinition def) {
        for (ItemStack s : stacks) {
            if (s.item().definitionId().equals(def.id())) {
                return s;
            }
        }
        return null;
    }

    private static boolean canStack(ItemStack s, ItemInstance item) {
        return s.item().isStackable() && s.item().definitionId().equals(item.definitionId())
                && s.count() < MAX_STACK;
    }
}
