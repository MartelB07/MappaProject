package mappa.core.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsTest {

    @Test
    void get_noModifiers_returnsBase() {
        Stats s = new Stats();
        s.setBase(StatType.MAX_HEALTH, 50);
        assertEquals(50, s.get(StatType.MAX_HEALTH));
        assertEquals(0, s.get(StatType.DAMAGE));
    }

    @Test
    void addAll_flatAndPercent_flatAppliedBeforePercent() {
        Stats s = new Stats();
        s.setBase(StatType.DAMAGE, 10);
        s.addAll("sword", List.of(StatModifier.flat(StatType.DAMAGE, 10, "sword"),
                StatModifier.percent(StatType.DAMAGE, 0.5, "sword")));
        assertEquals(30, s.get(StatType.DAMAGE)); // (10 + 10) * 1.5
    }

    @Test
    void addAll_mismatchedSource_throws() {
        Stats s = new Stats();
        assertThrows(IllegalArgumentException.class,
                () -> s.addAll("a", List.of(StatModifier.flat(StatType.DAMAGE, 1, "b"))));
    }

    @Test
    @Tag("regression")
    @DisplayName("D-08/A-12: 1000 equip/unequip cycles return exactly to base")
    void removeSource_afterManyCycles_noDrift() {
        Stats s = new Stats();
        s.setBase(StatType.WAR_DAMAGE, 7);
        s.setBase(StatType.MAGE_DAMAGE, 3);
        for (int i = 0; i < 1000; i++) {
            String src = (i % 2 == 0) ? "warHelm" : "mageHelm";
            StatType t = (i % 2 == 0) ? StatType.WAR_DAMAGE : StatType.MAGE_DAMAGE;
            s.add(StatModifier.flat(t, 5 + i % 3, src));
            s.removeSource(src);
        }
        assertEquals(7, s.get(StatType.WAR_DAMAGE));
        assertEquals(3, s.get(StatType.MAGE_DAMAGE));
        assertTrue(s.modifiers().isEmpty());
    }

    @Test
    void removeSource_unknownId_noChange() {
        Stats s = new Stats();
        s.add(StatModifier.flat(StatType.DEFENSE, 2, "x"));
        assertEquals(0, s.removeSource("nope"));
        assertEquals(2, s.get(StatType.DEFENSE));
        assertTrue(s.hasSource("x"));
        assertFalse(s.hasSource("nope"));
    }

    @Test
    @Tag("regression")
    @DisplayName("A-13: losing max health never kills a living player")
    void health_maxDropsBelowCurrent_clampsToAtLeastOne() {
        Stats s = new Stats();
        s.setBase(StatType.MAX_HEALTH, 10);
        Health h = new Health(s);
        s.add(StatModifier.flat(StatType.MAX_HEALTH, 50, "armor"));
        h.onMaxChanged(10);
        assertEquals(60, h.current());
        h.damage(58); // 2 hp left
        int oldMax = h.max();
        s.removeSource("armor");
        h.onMaxChanged(oldMax);
        assertEquals(2, h.current());
        h.damage(1);
        oldMax = h.max();
        s.setBase(StatType.MAX_HEALTH, 0);
        h.onMaxChanged(oldMax);
        assertEquals(1, h.current());
        assertTrue(h.isAlive());
    }

    @Test
    void health_heal_cappedAtMax_returnsAmountHealed() {
        Stats s = new Stats();
        s.setBase(StatType.MAX_HEALTH, 10);
        Health h = new Health(s);
        h.damage(4);
        assertEquals(4, h.heal(100));
        assertEquals(10, h.current());
        assertEquals(0, h.heal(5));
    }

    @Test
    void health_dead_staysDeadWhenMaxChanges() {
        Stats s = new Stats();
        s.setBase(StatType.MAX_HEALTH, 10);
        Health h = new Health(s);
        h.damage(10);
        s.setBase(StatType.MAX_HEALTH, 20);
        h.onMaxChanged(10);
        assertFalse(h.isAlive());
    }
}
