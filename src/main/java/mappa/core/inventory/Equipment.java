package mappa.core.inventory;

import mappa.core.items.ItemInstance;
import mappa.core.items.ItemSlot;
import mappa.core.stats.Health;
import mappa.core.stats.Stats;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Worn gear. Equipping adds the item's stat modifiers (tagged with its instance id);
 * unequipping removes exactly those modifiers, so stats can never drift.
 */
public class Equipment {
    public enum Status { OK, LEVEL_TOO_LOW, NOT_EQUIPPABLE }

    public record Result(Status status, ItemInstance previous) {
        public boolean ok() {
            return status == Status.OK;
        }
    }

    private final Stats stats;
    private final Health health;
    private final EnumMap<ItemSlot, ItemInstance> worn = new EnumMap<>(ItemSlot.class);

    public Equipment(Stats stats, Health health) {
        this.stats = stats;
        this.health = health;
    }

    public ItemInstance get(ItemSlot slot) {
        return worn.get(slot);
    }

    public Map<ItemSlot, ItemInstance> all() {
        return Collections.unmodifiableMap(worn);
    }

    /**
     * Equips {@code item}; the previously worn item (if any) is returned in the result
     * and its modifiers removed. The caller moves items between inventory and equipment.
     */
    public Result equip(ItemInstance item, int playerLevel) {
        if (!item.slot().isGear()) {
            return new Result(Status.NOT_EQUIPPABLE, null);
        }
        if (item.levelReq() > playerLevel) {
            return new Result(Status.LEVEL_TOO_LOW, null);
        }
        int oldMax = health.max();
        ItemInstance previous = worn.put(item.slot(), item);
        if (previous != null) {
            stats.removeSource(previous.instanceId());
        }
        stats.addAll(item.instanceId(), item.modifiers());
        health.onMaxChanged(oldMax);
        return new Result(Status.OK, previous);
    }

    /** Removes the item in {@code slot} and returns it (or null if empty). */
    public ItemInstance unequip(ItemSlot slot) {
        ItemInstance previous = worn.remove(slot);
        if (previous != null) {
            int oldMax = health.max();
            stats.removeSource(previous.instanceId());
            health.onMaxChanged(oldMax);
        }
        return previous;
    }
}
