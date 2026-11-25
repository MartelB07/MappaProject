package mappa.explore;

import com.google.gson.JsonObject;
import mappa.core.content.BiomeDefinition;
import mappa.core.content.ContentRegistry;
import mappa.core.random.SeededRng;
import mappa.core.save.SaveStore;
import mappa.testsupport.ScriptedIO;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(5)
class ExploreSaveTest {
    private final ContentRegistry content = TestContent.small();
    private final ExploreConfig config = ExploreConfig.from(content);

    @TempDir
    Path root;

    private ExploreGame game(ScriptedIO io) {
        return new ExploreGame(content, new SeededRng(1), io, root);
    }

    @Test
    void gather_saveAndExit_thenContinue_restoresEverything() {
        ExploreGame first = game(ScriptedIO.of("1", "1", "1", "0")); // new game, slot 1, gather, save & exit
        first.run();

        ExploreGame second = game(ScriptedIO.of("2", "0"));
        second.run();
        assertEquals(first.state().day(), second.state().day());
        assertEquals(10, second.state().hour());
        assertEquals(first.state().hunger(), second.state().hunger());
        assertEquals(first.state().thirst(), second.state().thirst());
        assertEquals(2, second.state().inventory().count("t_wood"));
        assertEquals(first.position(), second.position());
        assertEquals(first.world(), second.world());
    }

    @Test
    void slotCount_comesFromConfig() {
        ScriptedIO io = ScriptedIO.of("1", "0", "0"); // New game -> slot list -> cancel -> back
        game(io).run();
        assertTrue(io.outputContains("Slot 2: (empty)"));
        assertFalse(io.outputContains("Slot 3"));
    }

    @Test
    @DisplayName("Dying in Explore deletes the save (deleteOnDeath: true)")
    void death_deletesSave() {
        SurvivalState starving = new SurvivalState(config);
        starving.restore(3, 8, 1, 0, 0);
        new SaveStore(root, ExploreSave.MODE, 2, ExploreSave.FORMAT)
                .save(1, "x", ExploreSave.write(starving, List.of(content.biome("t_forest")), 0));

        ScriptedIO io = ScriptedIO.of("2", "1"); // continue, gather -> starve
        game(io).run();
        assertTrue(io.outputContains("survived 3 day(s)"));
        assertTrue(io.outputContains("has been deleted"));
        assertFalse(Files.exists(root.resolve("explore/slot1.json")));
    }

    @Test
    void deletedBiome_isReplaced() {
        SurvivalState s = new SurvivalState(config);
        JsonObject data = ExploreSave.write(s, List.of(content.biome("t_forest")), 0);
        data.getAsJsonArray("world").add("lava_lake");
        data.addProperty("position", 1);
        List<String> warnings = new ArrayList<>();
        ExploreSave.Loaded loaded = ExploreSave.read(data, content, config, warnings);
        assertEquals(2, loaded.world().size());
        assertEquals(1, loaded.position());
        BiomeDefinition replacement = loaded.world().get(1);
        assertEquals("t_forest", replacement.id());
        assertTrue(warnings.get(0).contains("lava_lake"));
    }
}
