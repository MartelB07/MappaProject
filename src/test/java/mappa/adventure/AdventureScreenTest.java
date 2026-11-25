package mappa.adventure;

import mappa.core.content.ContentRegistry;
import mappa.core.save.SaveStore;
import mappa.testsupport.FixedRng;
import mappa.testsupport.ScriptedIO;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks what is actually on screen after an input (the screen is wiped after every input). */
@Timeout(5)
class AdventureScreenTest {
    private final ContentRegistry content = TestContent.small();
    private final AdventureConfig config = AdventureConfig.from(content);

    @TempDir
    Path root;

    @Test
    @DisplayName("Returning home shows only the arrival notice and the Home options")
    void travelHome_screenShowsArrivalAndHomeMenuOnly() {
        AdventurePlayer p = new AdventurePlayer("Ari", config, content);
        p.addExp(5); // level 1, so the town is reachable
        AdventureGame setup = new AdventureGame(content, new FixedRng(), new ScriptedIO(), false).withPlayer(p, "t_town");
        new SaveStore(root, AdventureSave.MODE, 3, AdventureSave.FORMAT)
                .save(1, "Ari", AdventureSave.write(setup));

        // 0: continue slot 1   1: Travel   2: Test Home   3: save and exit
        ScriptedIO io = ScriptedIO.of("2", "4", "1", "0");
        new AdventureGame(content, new FixedRng(), io, false, root).run();

        String screen = io.screenBefore(3);
        assertTrue(screen.contains("You arrived in Test Home"), screen);
        assertTrue(screen.contains("── Test Home") || screen.contains("-- Test Home"), "header for the new place");
        assertTrue(screen.contains("What would you like to do?"));
        assertFalse(screen.contains("Where would you like to travel?"), "the previous menu is gone");
        assertFalse(screen.contains("Test Town ─") || screen.contains("-- Test Town"), "the old header is gone");
    }

    @Test
    void levelUp_showsBoxedBanner() {
        ScriptedIO io = new ScriptedIO();
        AdventurePlayer p = new AdventurePlayer("Ari", config, content);
        p.addExp(4); // one more EXP reaches level 1
        AdventureGame g = new AdventureGame(content, new FixedRng(), io, false).withPlayer(p, "t_home");
        io.then("1"); // one attack defeats the weakened king (+1 EXP)
        g.fight(mappa.core.mobs.MobFactory.spawn(content.mob("t_king"), 0, 0.1, new FixedRng()));
        String out = io.plainOutput();
        assertTrue(out.contains("LEVEL UP!  You are now level 1"), out);
        assertTrue(out.contains("╔") || out.contains("+="), "banner is boxed");
    }
}
