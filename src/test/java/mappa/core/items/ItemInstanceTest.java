package mappa.core.items;

import mappa.core.stats.StatModifier;
import mappa.core.stats.StatType;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemInstanceTest {

    @Test
    @Tag("regression")
    @DisplayName("ST-05: two instances of one definition are independent")
    void create_twoInstances_independent() {
        ItemDefinition def = TestContent.small().item("t_helm");
        Rarity rare = TestContent.small().rarity("RARE");
        ItemInstance a = ItemInstance.of(def);
        ItemInstance b = ItemInstance.rolled(def, rare, 1.5);
        assertNotEquals(a.instanceId(), b.instanceId());
        assertEquals(10, a.stat(StatType.MAX_HEALTH));
        assertEquals(15, b.stat(StatType.MAX_HEALTH));
        assertEquals(10, def.stat(StatType.MAX_HEALTH));
    }

    @Test
    void definition_isImmutable() {
        Map<StatType, Integer> stats = new EnumMap<>(StatType.class);
        stats.put(StatType.DAMAGE, 5);
        ItemDefinition def = new ItemDefinition("x", "x", ItemSlot.WEAPON, null, 0, 0, stats, null, null, null);
        stats.put(StatType.DAMAGE, 999);
        assertEquals(5, def.stat(StatType.DAMAGE));
        assertThrows(UnsupportedOperationException.class, () -> def.stats().put(StatType.DAMAGE, 1));
    }

    @Test
    void modifiers_taggedWithInstanceId() {
        ItemInstance i = ItemInstance.of(TestContent.small().item("t_helm"));
        List<StatModifier> mods = i.modifiers();
        assertEquals(2, mods.size());
        mods.forEach(m -> assertEquals(i.instanceId(), m.sourceId()));
    }

    @Test
    @Tag("regression")
    @DisplayName("ST-12: sell value is whole gold")
    void sellValue_isWholeGold() {
        ItemInstance i = ItemInstance.of(TestContent.small().item("t_potion")); // price 5
        assertEquals(3, i.sellValue(75)); // floor(3.75)
    }
}
