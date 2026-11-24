package mappa.core.inventory;

import mappa.core.items.ItemDefinition;
import mappa.core.items.ItemInstance;
import mappa.core.items.ItemSlot;
import mappa.core.stats.Health;
import mappa.core.stats.StatType;
import mappa.core.stats.Stats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentTest {
    private static ItemDefinition helm(String id, int hp, int level) {
        return new ItemDefinition(id, id, ItemSlot.HELMET, null, level, 1, Map.of(StatType.MAX_HEALTH, hp), null,
                null, null);
    }

    private final Stats stats = new Stats();
    private Health health;
    private Equipment eq;

    private void setUp() {
        stats.setBase(StatType.MAX_HEALTH, 10);
        health = new Health(stats);
        eq = new Equipment(stats, health);
    }

    @Test
    void equip_replacesOldItem_returnsPrevious() {
        setUp();
        ItemInstance a = ItemInstance.of(helm("a", 5, 0));
        ItemInstance b = ItemInstance.of(helm("b", 20, 0));
        assertNull(eq.equip(a, 0).previous());
        assertEquals(15, stats.get(StatType.MAX_HEALTH));
        assertSame(a, eq.equip(b, 0).previous());
        assertEquals(30, stats.get(StatType.MAX_HEALTH));
    }

    @Test
    void equip_belowLevelReq_rejected() {
        setUp();
        Equipment.Result r = eq.equip(ItemInstance.of(helm("x", 5, 3)), 2);
        assertEquals(Equipment.Status.LEVEL_TOO_LOW, r.status());
        assertNull(eq.get(ItemSlot.HELMET));
    }

    @Test
    @Tag("regression")
    @DisplayName("A-12: potions/keys can't be equipped and get a clear reason")
    void equip_nonEquippableSlot_rejected() {
        setUp();
        ItemDefinition potion = new ItemDefinition("p", "p", ItemSlot.CONSUMABLE, null, 0, 1, null, null, null, null);
        assertEquals(Equipment.Status.NOT_EQUIPPABLE, eq.equip(ItemInstance.of(potion), 99).status());
    }

    @Test
    @Tag("regression")
    @DisplayName("A-13: unequipping armor at low HP leaves the player alive")
    void unequipArmor_lowHp_clampsToAtLeast1() {
        setUp();
        eq.equip(ItemInstance.of(helm("big", 100, 0)), 0);
        health.damage(health.current() - 3);
        eq.unequip(ItemSlot.HELMET);
        assertTrue(health.isAlive());
        assertEquals(3, health.current());
    }

    @Test
    @Tag("regression")
    @DisplayName("D-08: swapping class gear never shifts damage to the wrong stat")
    void swapWarHelmForMageHelm_statsExact() {
        setUp();
        ItemDefinition war = new ItemDefinition("w", "w", ItemSlot.HELMET, null, 0, 1,
                Map.of(StatType.WAR_DAMAGE, 6), null, null, null);
        ItemDefinition mage = new ItemDefinition("m", "m", ItemSlot.HELMET, null, 0, 1,
                Map.of(StatType.MAGE_DAMAGE, 4), null, null, null);
        eq.equip(ItemInstance.of(war), 0);
        eq.equip(ItemInstance.of(mage), 0);
        assertEquals(0, stats.get(StatType.WAR_DAMAGE));
        assertEquals(4, stats.get(StatType.MAGE_DAMAGE));
        eq.unequip(ItemSlot.HELMET);
        assertEquals(0, stats.get(StatType.MAGE_DAMAGE));
    }
}
