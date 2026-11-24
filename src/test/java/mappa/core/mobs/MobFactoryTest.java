package mappa.core.mobs;

import mappa.core.content.ContentRegistry;
import mappa.testsupport.FixedRng;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobFactoryTest {
    private final ContentRegistry content = TestContent.small();

    @Test
    @Tag("regression")
    @DisplayName("D-13: spawned mobs carry their real level")
    void spawn_setsRequestedLevel() {
        Mob m = MobFactory.spawn(content.mob("t_grunt"), 7, 1.0, new FixedRng());
        assertEquals(7, m.level());
    }

    @Test
    void spawn_atBaseLevel_usesBaseStats() {
        Mob m = MobFactory.spawn(content.mob("t_grunt"), 1, 1.0, new FixedRng());
        assertEquals(10, m.maxHealth());
        assertEquals(4, m.damage());
    }

    @Test
    void spawn_higherLevel_neverWeaker() {
        MobDefinition def = content.mob("t_grunt");
        Mob prev = MobFactory.spawn(def, 0, 1.0, new FixedRng());
        for (int lvl = 1; lvl <= 60; lvl++) {
            Mob m = MobFactory.spawn(def, lvl, 1.0, new FixedRng());
            assertTrue(m.maxHealth() >= prev.maxHealth(), "level " + lvl);
            assertTrue(m.damage() >= prev.damage(), "level " + lvl);
            prev = m;
        }
    }

    @Test
    void spawn_higherDifficulty_neverWeaker() {
        MobDefinition def = content.mob("t_grunt");
        Mob easy = MobFactory.spawn(def, 1, 1.0, new FixedRng());
        Mob hard = MobFactory.spawn(def, 1, 2.0, new FixedRng());
        assertEquals(easy.maxHealth() * 2, hard.maxHealth());
        assertEquals(easy.exp() * 2, hard.exp());
    }

    @Test
    void spawn_goldRange_usesRng() {
        Mob m = MobFactory.spawn(content.mob("t_boss"), 1, 1.0, new FixedRng());
        assertEquals(10, m.gold());
    }

    @Test
    void spawn_zeroScale_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> MobFactory.spawn(content.mob("t_grunt"), 1, 0, new FixedRng()));
    }

    @Test
    void takeDamage_alwaysAtLeastOne_neverBelowZero() {
        Mob m = MobFactory.spawn(content.mob("t_grunt"), 1, 1.0, new FixedRng());
        assertEquals(10, m.takeDamage(500));
        assertEquals(0, m.health());
        assertEquals(0, m.takeDamage(5));
    }
}
