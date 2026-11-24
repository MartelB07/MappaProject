package mappa.dungeons;

import mappa.core.io.Ansi;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonsConfigTest {

    @Test
    void realConfig_hasRegularAndHardcoreModes() {
        DungeonsConfig c = DungeonsConfig.from(TestContent.real());
        RunMode regular = c.runMode("REGULAR");
        RunMode hardcore = c.runMode("HARDCORE");
        assertTrue(regular.unlimitedLives());
        assertTrue(regular.deathTurnPenalty() > 0);
        assertEquals(2, regular.rewardItems());
        assertEquals(1, hardcore.lives());
        assertEquals(3, hardcore.rewardItems());
        assertTrue(hardcore.rewardItems() > regular.rewardItems(), "hardcore must pay more than regular");
    }

    @Test
    void isOutOfLives_respectsLimit() {
        RunMode hc = new RunMode("H", "H", Ansi.Color.RED, 1, 0, 100, 3, "");
        assertFalse(hc.isOutOfLives(0));
        assertTrue(hc.isOutOfLives(1));
        RunMode reg = new RunMode("R", "R", Ansi.Color.GREEN, 0, 10, 100, 2, "");
        assertFalse(reg.isOutOfLives(1000));
    }

    @Test
    void validate_badValues_reported() {
        RunMode bad = new RunMode("B", "B", Ansi.Color.DEFAULT, -1, -1, 0, -1, "");
        assertEquals(4, DungeonsConfig.validate(bad).size());
    }
}
