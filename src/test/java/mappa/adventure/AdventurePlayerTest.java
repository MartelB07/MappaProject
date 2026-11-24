package mappa.adventure;

import mappa.core.content.ContentRegistry;
import mappa.core.io.InputClosedException;
import mappa.testsupport.FixedRng;
import mappa.testsupport.ScriptedIO;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdventurePlayerTest {
    private final ContentRegistry content = TestContent.small();
    private final AdventureConfig config = AdventureConfig.from(content);

    @Test
    void newPlayer_startsWithConfiguredGear() {
        AdventurePlayer p = new AdventurePlayer("Ari", config, content);
        assertEquals(10, p.health().max());
        assertEquals(4, p.damage(), "club only at level 0");
        assertEquals(3, p.inventory().count("t_potion"));
    }

    @Test
    void levelUp_raisesHealthAndDamage_andGivesGold() {
        AdventurePlayer p = new AdventurePlayer("Ari", config, content);
        p.addExp(15); // levels 1 and 2
        assertEquals(2, p.level());
        assertEquals(30, p.health().max());
        assertEquals(30, p.health().current());
        assertEquals(4 + 1 + 2, p.damage());
        assertEquals(10 + 20, p.wallet().gold());
    }

    @Test
    void reviveCost_scalesWithLevel() {
        AdventurePlayer p = new AdventurePlayer("Ari", config, content);
        assertEquals(35, p.reviveCost());
        p.addExp(15);
        assertEquals(70, p.reviveCost());
    }

    @Test
    @Timeout(5)
    @Tag("regression")
    @DisplayName("S-01: the old password name no longer unlocks developer mode")
    void devMode_onlyViaFlag_notPlayerName() {
        ScriptedIO io = ScriptedIO.of("Password123  ", "0", "1");
        AdventureGame game = new AdventureGame(content, new FixedRng(), io, false);
        game.run();
        assertEquals(0, game.player().wallet().gold());
        assertEquals(0, game.player().level());

        AdventureGame dev = new AdventureGame(content, new FixedRng(), ScriptedIO.of("Dev", "0", "1"), true);
        dev.run();
        assertEquals(99_999, dev.player().wallet().gold());
    }

    @Test
    @Timeout(5)
    void run_scriptEnds_throwsInsteadOfHanging() {
        AdventureGame game = new AdventureGame(content, new FixedRng(), ScriptedIO.of("Ari"), false);
        assertThrows(InputClosedException.class, game::run);
    }
}
