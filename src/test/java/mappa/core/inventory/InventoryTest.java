package mappa.core.inventory;

import mappa.core.items.ItemDefinition;
import mappa.core.items.ItemEffect;
import mappa.core.items.ItemInstance;
import mappa.core.items.ItemSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryTest {
    static final ItemDefinition POTION = new ItemDefinition("potion", "Potion", ItemSlot.CONSUMABLE, null, 0, 5,
            Map.of(), new ItemEffect(5, 0, null, 0), null, null);
    static final ItemDefinition SWORD = new ItemDefinition("sword", "Sword", ItemSlot.WEAPON, null, 0, 10,
            Map.of(), null, null, null);

    @Test
    void add_intoEmpty_occupiesOneSlot() {
        Inventory inv = new Inventory(3);
        assertTrue(inv.add(ItemInstance.of(SWORD)));
        assertEquals(1, inv.usedSlots());
        assertEquals(2, inv.freeSlots());
    }

    @Test
    void add_stackables_shareASlot() {
        Inventory inv = new Inventory(3);
        inv.add(ItemInstance.of(POTION), 4);
        inv.add(ItemInstance.of(POTION), 3);
        assertEquals(1, inv.usedSlots());
        assertEquals(7, inv.count("potion"));
    }

    @Test
    @Timeout(2)
    @Tag("regression")
    @DisplayName("A-01: adding to a full inventory returns 0 instead of looping")
    void add_whenFull_returnsZeroAdded() {
        Inventory inv = new Inventory(1);
        inv.add(ItemInstance.of(SWORD));
        assertEquals(0, inv.add(ItemInstance.of(POTION), 5));
        assertFalse(inv.add(ItemInstance.of(SWORD)));
    }

    @Test
    void add_partialFit_returnsCountAdded() {
        Inventory inv = new Inventory(1);
        assertEquals(Inventory.MAX_STACK, inv.add(ItemInstance.of(POTION), Inventory.MAX_STACK + 10));
        assertEquals(0, inv.spaceFor(ItemInstance.of(POTION)));
    }

    @Test
    @Tag("regression")
    @DisplayName("A-02: removing N potions removes exactly N, across stacks")
    void remove_exactCount_removesOnlyThatMany() {
        Inventory inv = new Inventory(5);
        inv.add(ItemInstance.of(POTION), Inventory.MAX_STACK + 5); // two stacks
        assertEquals(2, inv.usedSlots());
        assertEquals(7, inv.removeByDefinition("potion", 7));
        assertEquals(Inventory.MAX_STACK - 2, inv.count("potion"));
    }

    @Test
    void removeByDefinition_moreThanOwned_removesWhatExists() {
        Inventory inv = new Inventory(5);
        inv.add(ItemInstance.of(POTION), 2);
        assertEquals(2, inv.removeByDefinition("potion", 10));
        assertTrue(inv.isEmpty());
    }

    @Test
    void count_byDefinitionId_ignoresOtherItems() {
        Inventory inv = new Inventory(5);
        inv.add(ItemInstance.of(SWORD));
        inv.add(ItemInstance.of(SWORD));
        inv.add(ItemInstance.of(POTION), 3);
        assertEquals(2, inv.count("sword"));
        assertEquals(3, inv.count("potion"));
    }

    @Test
    void add_sameGearInstanceTwice_throws() {
        Inventory inv = new Inventory(5);
        ItemInstance s = ItemInstance.of(SWORD);
        inv.add(s);
        assertThrows(IllegalArgumentException.class, () -> inv.add(s));
    }
}
