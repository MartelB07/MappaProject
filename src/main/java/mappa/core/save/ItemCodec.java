package mappa.core.save;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mappa.core.content.ContentRegistry;
import mappa.core.inventory.Equipment;
import mappa.core.inventory.Inventory;
import mappa.core.inventory.ItemStack;
import mappa.core.items.ItemInstance;
import mappa.core.items.ItemSlot;
import mappa.core.items.Rarity;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts items, inventories and equipment to and from save JSON. Saves store ids only;
 * stats come from the current content when loading. Anything that no longer exists is
 * skipped and reported in {@code warnings} instead of failing the load.
 */
public final class ItemCodec {
    private ItemCodec() {
    }

    /** {@code {"item": defId, "id": instanceId, "rarity"?: id, "multiplier"?: x}}. */
    public static JsonObject encode(ItemInstance item) {
        JsonObject o = new JsonObject();
        o.addProperty("item", item.definitionId());
        o.addProperty("id", item.instanceId());
        if (item.rarity() != null) {
            o.addProperty("rarity", item.rarity().id());
            o.addProperty("multiplier", item.multiplier());
        }
        return o;
    }

    /** Rebuilds an item, or returns null (with a warning) if its definition no longer exists. */
    public static ItemInstance decode(JsonObject o, ContentRegistry content, List<String> warnings) {
        String defId = o.has("item") ? o.get("item").getAsString() : "";
        if (!content.hasItem(defId)) {
            warnings.add("Item '" + defId + "' no longer exists and was removed.");
            return null;
        }
        String instanceId = o.has("id") ? o.get("id").getAsString() : java.util.UUID.randomUUID().toString();
        Rarity rarity = null;
        double multiplier = 1.0;
        if (o.has("rarity")) {
            String rarityId = o.get("rarity").getAsString();
            rarity = content.raritiesById().get(rarityId);
            if (rarity == null) {
                warnings.add(content.item(defId).name() + ": rarity '" + rarityId + "' no longer exists; it was reset.");
            } else if (o.has("multiplier")) {
                multiplier = o.get("multiplier").getAsDouble();
                if (multiplier <= 0) {
                    multiplier = 1.0;
                }
            }
        }
        return ItemInstance.restore(instanceId, content.item(defId), rarity, multiplier);
    }

    /** Inventory as a list of stacks: each entry is an encoded item plus {@code "count"}. */
    public static JsonArray encodeInventory(Inventory inventory) {
        JsonArray arr = new JsonArray();
        for (ItemStack s : inventory.stacks()) {
            JsonObject o = encode(s.item());
            o.addProperty("count", s.count());
            arr.add(o);
        }
        return arr;
    }

    /** Adds saved stacks to {@code inventory}; items that don't fit or no longer exist are reported. */
    public static void decodeInventory(JsonArray arr, Inventory inventory, ContentRegistry content,
                                       List<String> warnings) {
        if (arr == null) {
            return;
        }
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            ItemInstance item = decode(o, content, warnings);
            if (item == null) {
                continue;
            }
            int count = o.has("count") ? Math.max(1, o.get("count").getAsInt()) : 1;
            int added;
            if (item.isStackable()) {
                added = inventory.add(item, count);
            } else {
                // Gear is one instance per slot; extra copies (old saves) become new instances.
                added = inventory.add(item) ? 1 : 0;
                for (int i = 1; i < count; i++) {
                    if (inventory.add(ItemInstance.restore(java.util.UUID.randomUUID().toString(),
                            item.definition(), item.rarity(), item.multiplier()))) {
                        added++;
                    }
                }
            }
            if (added < count) {
                warnings.add("No inventory space for " + (count - added) + " x " + item.name() + ".");
            }
        }
    }

    /** {@code {"WEAPON": {...}, "HELMET": {...}}}. */
    public static JsonObject encodeEquipment(Equipment equipment) {
        JsonObject o = new JsonObject();
        for (Map.Entry<ItemSlot, ItemInstance> e : equipment.all().entrySet()) {
            o.add(e.getKey().name(), encode(e.getValue()));
        }
        return o;
    }

    /**
     * Re-equips saved gear. Gear that can't be worn any more (level too high after a content change,
     * or no longer gear) goes into the inventory instead.
     */
    public static void decodeEquipment(JsonObject o, Equipment equipment, Inventory inventory, int playerLevel,
                                       ContentRegistry content, List<String> warnings) {
        if (o == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            ItemInstance item = decode(e.getValue().getAsJsonObject(), content, warnings);
            if (item == null) {
                continue;
            }
            Equipment.Result r = equipment.equip(item, playerLevel);
            if (!r.ok()) {
                String where = inventory.add(item) ? "moved to your inventory" : "lost (inventory full)";
                warnings.add(item.name() + " can't be equipped any more (" + e.getKey().toLowerCase(Locale.ROOT)
                        + ") and was " + where + ".");
            }
        }
    }
}
