package mappa.core.items;

/** Where an item goes. Gear slots can be equipped; stackable slots share inventory stacks. */
public enum ItemSlot {
    WEAPON(true, false),
    HELMET(true, false),
    /** Chestplate (Adventure) / Armor (Dungeons). */
    CHEST(true, false),
    BOOTS(true, false),
    SPELL(false, false),
    CONSUMABLE(false, true),
    KEY(false, true),
    RESOURCE(false, true);

    private final boolean gear;
    private final boolean stackable;

    ItemSlot(boolean gear, boolean stackable) {
        this.gear = gear;
        this.stackable = stackable;
    }

    /** True for slots handled by {@link mappa.core.inventory.Equipment}. */
    public boolean isGear() {
        return gear;
    }

    public boolean isStackable() {
        return stackable;
    }
}
