package mappa.dungeons;

import mappa.core.content.ContentRegistry;
import mappa.core.items.ItemInstance;
import mappa.core.items.ItemSlot;
import mappa.core.stats.StatType;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static mappa.dungeons.DungeonPlayer.Attribute.HEALTH;
import static mappa.dungeons.DungeonPlayer.Attribute.MAGE;
import static mappa.dungeons.DungeonPlayer.Attribute.WAR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonPlayerTest {
    private final ContentRegistry content = TestContent.small();
    private final DungeonsConfig config = DungeonsConfig.from(content);

    private DungeonPlayer player() {
        return new DungeonPlayer("Tess", config, content);
    }

    private static ItemInstance find(DungeonPlayer p, String defId) {
        return p.inventory().find(i -> i.definitionId().equals(defId)).get(0).item();
    }

    @Test
    void newPlayer_hasStartingItems() {
        DungeonPlayer p = player();
        assertEquals(3, p.inventory().usedSlots());
        assertEquals(10, p.health().max());
    }

    @Test
    void addExp_levelsGrantPointsAndHeal() {
        DungeonPlayer p = player();
        p.health().damage(5);
        p.addExp(15);
        assertEquals(2, p.level());
        assertEquals(2, p.availablePoints());
        assertEquals(p.health().max(), p.health().current());
    }

    @Test
    @Tag("regression")
    @DisplayName("D-04: negative or over-budget point spends are rejected")
    void spendPoints_invalidAmounts_rejected() {
        DungeonPlayer p = player();
        p.addExp(5);
        assertFalse(p.spendPoints(WAR, -5));
        assertFalse(p.spendPoints(WAR, 0));
        assertFalse(p.spendPoints(WAR, 2));
        assertEquals(1, p.availablePoints());
        assertTrue(p.spendPoints(WAR, 1));
        assertEquals(0, p.availablePoints());
    }

    @Test
    @Tag("regression")
    @DisplayName("D-02/D-03: reset removes stats, keeps unspent points, and charges gold")
    void resetPoints_removesStats_keepsUnspent_chargesGold() {
        DungeonPlayer p = player();
        p.addExp(15); // 2 points
        p.spendPoints(HEALTH, 1);
        assertEquals(12, p.health().max());
        assertEquals(1, p.availablePoints());

        assertFalse(p.resetPoints(), "no gold yet");
        assertEquals(12, p.health().max());

        p.wallet().add(100);
        assertTrue(p.resetPoints());
        assertEquals(75, p.wallet().gold());
        assertEquals(10, p.health().max());
        assertEquals(2, p.availablePoints());
        assertEquals(0, p.spentPoints());

        // repeat many times: no stat farming
        for (int i = 0; i < 5; i++) {
            p.wallet().add(50);
            p.spendPoints(WAR, 2);
            p.resetPoints();
        }
        assertEquals(0, p.stats().get(StatType.WAR_DAMAGE));
        assertEquals(2, p.availablePoints());
    }

    @Test
    @Tag("regression")
    @DisplayName("D-09: melee damage uses the equipped weapon's class")
    void attackDamage_usesWeaponClass() {
        DungeonPlayer p = player();
        assertEquals(0, p.attackDamage());
        p.addExp(15);
        p.spendPoints(WAR, 2);
        p.inventory().add(ItemInstance.of(content.item("t_staff")));
        p.equipGear(find(p, "t_staff"));
        assertEquals(4, p.attackDamage(), "staff uses mage damage, ignoring war points");
        p.equipGear(find(p, "t_sword"));
        assertEquals(7, p.attackDamage(), "sword 5 + 2 war points");
        assertTrue(p.inventory().count("t_staff") == 1, "old weapon returned to inventory");
    }

    @Test
    @Tag("regression")
    @DisplayName("D-07: mage spells scale with mage points")
    void mageSpell_scalesWithMagePoints() {
        DungeonPlayer p = player();
        p.addExp(15);
        p.spendPoints(MAGE, 2);
        ItemInstance mageBolt = ItemInstance.of(content.item("t_mage_bolt"));
        assertEquals(5, p.spellDamage(mageBolt));
        assertEquals(3, p.spellDamage(find(p, "t_bolt")), "war spell ignores mage points");
    }

    @Test
    void equip_levelTooLow_rejectedAndKept() {
        DungeonPlayer p = player();
        p.inventory().add(ItemInstance.of(content.item("t_big_sword")));
        assertEquals(DungeonPlayer.EquipOutcome.LEVEL_TOO_LOW, p.equipGear(find(p, "t_big_sword")));
        assertNull(p.weapon());
        assertEquals(1, p.inventory().count("t_big_sword"));
    }

    @Test
    void equipSpell_swapsOldSpellBackToInventory() {
        DungeonPlayer p = player();
        assertEquals(DungeonPlayer.EquipOutcome.OK, p.equipSpell(find(p, "t_bolt"), 0));
        assertEquals(DungeonPlayer.EquipOutcome.OK, p.equipSpell(find(p, "t_heal"), 0));
        assertEquals("t_heal", p.spell(0).definitionId());
        assertEquals(1, p.inventory().count("t_bolt"));
    }

    @Test
    void unequip_fullInventory_refused() {
        DungeonPlayer p = player();
        p.equipGear(find(p, "t_sword"));
        while (!p.inventory().isFull()) {
            p.inventory().add(ItemInstance.of(content.item("t_helm")));
        }
        assertEquals(DungeonPlayer.EquipOutcome.INVENTORY_FULL, p.unequipGear(ItemSlot.WEAPON));
        assertEquals("t_sword", p.weapon().definitionId());
    }
}
