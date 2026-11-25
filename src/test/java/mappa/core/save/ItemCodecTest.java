package mappa.core.save;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mappa.core.content.ContentRegistry;
import mappa.core.inventory.Equipment;
import mappa.core.inventory.Inventory;
import mappa.core.items.ItemInstance;
import mappa.core.items.ItemSlot;
import mappa.core.stats.Health;
import mappa.core.stats.StatType;
import mappa.core.stats.Stats;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemCodecTest {
    private final ContentRegistry content = TestContent.small();
    private final List<String> warnings = new ArrayList<>();

    @Test
    void rolledItem_roundTripsExactly() {
        ItemInstance epic = ItemInstance.rolled(content.item("t_helm"), content.rarity("RARE"), 1.42);
        ItemInstance back = ItemCodec.decode(ItemCodec.encode(epic), content, warnings);
        assertEquals(epic.instanceId(), back.instanceId());
        assertEquals("RARE", back.rarity().id());
        assertEquals(1.42, back.multiplier());
        assertEquals(epic.stat(StatType.MAX_HEALTH), back.stat(StatType.MAX_HEALTH));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void deletedItem_skippedWithWarning() {
        JsonObject o = new JsonObject();
        o.addProperty("item", "sword_that_was_removed");
        assertNull(ItemCodec.decode(o, content, warnings));
        assertTrue(warnings.get(0).contains("sword_that_was_removed"));
    }

    @Test
    void deletedRarity_resetWithWarning() {
        JsonObject o = ItemCodec.encode(ItemInstance.rolled(content.item("t_helm"), content.rarity("RARE"), 1.3));
        o.addProperty("rarity", "MYTHIC");
        ItemInstance back = ItemCodec.decode(o, content, warnings);
        assertNull(back.rarity());
        assertEquals(1.0, back.multiplier());
        assertEquals(1, warnings.size());
    }

    @Test
    void inventory_roundTripsStacksAndGear() {
        Inventory inv = new Inventory(10);
        inv.add(ItemInstance.of(content.item("t_potion")), 7);
        inv.add(ItemInstance.of(content.item("t_helm")));
        inv.add(ItemInstance.of(content.item("t_helm")));
        Inventory back = new Inventory(10);
        ItemCodec.decodeInventory(ItemCodec.encodeInventory(inv), back, content, warnings);
        assertEquals(7, back.count("t_potion"));
        assertEquals(2, back.count("t_helm"));
        assertEquals(3, back.usedSlots());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void inventory_smallerThanSave_reportsWhatDidNotFit() {
        Inventory inv = new Inventory(5);
        for (int i = 0; i < 5; i++) {
            inv.add(ItemInstance.of(content.item("t_helm")));
        }
        JsonArray saved = ItemCodec.encodeInventory(inv);
        Inventory small = new Inventory(3);
        ItemCodec.decodeInventory(saved, small, content, warnings);
        assertEquals(3, small.count("t_helm"));
        assertEquals(2, warnings.size());
    }

    @Test
    void equipment_aboveLevel_movesToInventory() {
        Stats stats = new Stats();
        stats.setBase(StatType.MAX_HEALTH, 10);
        Equipment eq = new Equipment(stats, new Health(stats));
        eq.equip(ItemInstance.of(content.item("t_big_sword")), 99);
        eq.equip(ItemInstance.of(content.item("t_helm")), 99);
        JsonObject saved = ItemCodec.encodeEquipment(eq);

        Stats stats2 = new Stats();
        stats2.setBase(StatType.MAX_HEALTH, 10);
        Equipment eq2 = new Equipment(stats2, new Health(stats2));
        Inventory inv = new Inventory(5);
        ItemCodec.decodeEquipment(saved, eq2, inv, 0, content, warnings);
        assertEquals("t_helm", eq2.get(ItemSlot.HELMET).definitionId());
        assertNull(eq2.get(ItemSlot.WEAPON), "level 3 sword can't be worn at level 0");
        assertEquals(1, inv.count("t_big_sword"));
        assertEquals(20, stats2.get(StatType.MAX_HEALTH), "stats rebuilt from re-equipped gear");
        assertEquals(1, warnings.size());
    }
}
