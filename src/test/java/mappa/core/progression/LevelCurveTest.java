package mappa.core.progression;

import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelCurveTest {
    private final LevelCurve curve = new LevelCurve(new int[]{0, 5, 10, 20});

    @Test
    @Tag("regression")
    @DisplayName("A-05: one big EXP gain can give several levels")
    void addExp_enoughForTwoLevels_gainsTwoLevels() {
        Progression p = new Progression(curve);
        LevelUpResult r = p.addExp(16);
        assertEquals(2, r.levelsGained());
        assertEquals(2, p.level());
        assertEquals(1, p.exp());
    }

    @Test
    void addExp_leftoverCarriesOver() {
        Progression p = new Progression(curve);
        p.addExp(4);
        assertEquals(0, p.level());
        p.addExp(3);
        assertEquals(1, p.level());
        assertEquals(2, p.exp());
    }

    @Test
    @Tag("regression")
    @DisplayName("A-05: reaching max level is reported exactly once")
    void addExp_atMaxLevel_flagsReachedMaxOnce() {
        Progression p = new Progression(curve);
        LevelUpResult first = p.addExp(1000);
        assertTrue(first.reachedMax());
        assertTrue(p.isMaxLevel());
        assertEquals(0, p.exp());
        LevelUpResult second = p.addExp(50);
        assertFalse(second.reachedMax());
        assertFalse(second.leveledUp());
        assertEquals(0, p.expForNext());
    }

    @Test
    void addExp_negative_throws() {
        assertThrows(IllegalArgumentException.class, () -> new Progression(curve).addExp(-1));
    }

    @Test
    void constructor_nonPositiveThreshold_throws() {
        assertThrows(IllegalArgumentException.class, () -> new LevelCurve(new int[]{0, 5, 0}));
    }

    @Test
    void formula_thresholdsStrictlyIncreasing() {
        LevelCurve f = LevelCurve.formula(30, 1, 1.01);
        int[] t = f.thresholds();
        assertEquals(30, f.maxLevel());
        for (int i = 2; i < t.length; i++) {
            assertTrue(t[i] > t[i - 1], "level " + i);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"adventure", "dungeons", "explore"})
    void realCurves_everyThresholdPositive(String id) {
        LevelCurve c = TestContent.real().levelCurve(id);
        for (int lvl = 1; lvl <= c.maxLevel(); lvl++) {
            assertTrue(c.expToReach(lvl) > 0);
        }
    }
}
