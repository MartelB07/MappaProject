package mappa.explore;

import mappa.core.content.ContentRegistry;
import mappa.core.random.SeededRng;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalStateTest {
    private final ContentRegistry content = TestContent.small();
    private final ExploreConfig config = ExploreConfig.from(content);

    @Test
    void passTime_drainsHungerAndThirst() {
        SurvivalState s = new SurvivalState(config);
        s.passTime(2, 10, 20);
        assertEquals(80, s.hunger());
        assertEquals(60, s.thirst());
        assertEquals(10, s.hour());
    }

    @Test
    void passTime_atZeroThirst_costsHealth() {
        SurvivalState s = new SurvivalState(config);
        s.passTime(5, 0, 20); // thirst hits 0 on hour 5
        assertEquals(0, s.thirst());
        assertEquals(18, s.health().current());
    }

    @Test
    void passTime_rollsOverToNextDay_andTracksNight() {
        SurvivalState s = new SurvivalState(config);
        assertFalse(s.isNight());
        s.passTime(13, 0, 0); // 08:00 -> 21:00
        assertTrue(s.isNight());
        s.passTime(3, 0, 0); // -> 00:00 next day
        assertEquals(2, s.day());
        assertEquals(0, s.hour());
    }

    @Test
    void eatAndDrink_cappedAtMax() {
        SurvivalState s = new SurvivalState(config);
        s.passTime(1, 10, 10);
        s.eat(1000);
        s.drink(1000);
        assertEquals(100, s.hunger());
        assertEquals(100, s.thirst());
    }

    @Test
    void worldGenerator_buildsWorldOfConfiguredSize() {
        assertEquals(config.worldSize(), WorldGenerator.generate(content, config.worldSize(), new SeededRng(1)).size());
    }
}
