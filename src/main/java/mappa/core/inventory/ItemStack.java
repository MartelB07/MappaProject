package mappa.core.inventory;

import mappa.core.items.ItemInstance;

/** One inventory slot: an item and how many of it (always 1 for gear). */
public final class ItemStack {
    private final ItemInstance item;
    private int count;

    ItemStack(ItemInstance item, int count) {
        this.item = item;
        this.count = count;
    }

    public ItemInstance item() {
        return item;
    }

    public int count() {
        return count;
    }

    void setCount(int count) {
        this.count = count;
    }

    @Override
    public String toString() {
        return item.name() + (count > 1 ? " x" + count : "");
    }
}
