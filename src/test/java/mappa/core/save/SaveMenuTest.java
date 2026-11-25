package mappa.core.save;

import com.google.gson.JsonObject;
import mappa.core.io.Menu;
import mappa.testsupport.ScriptedIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(5)
class SaveMenuTest {
    @TempDir
    Path root;

    private SaveStore store() {
        return new SaveStore(root, "adventure", 3, 1);
    }

    private SaveMenu.Choice run(SaveStore store, String... input) {
        return new SaveMenu(store, new Menu(ScriptedIO.of(input))).start("Saves");
    }

    @Test
    void noSaves_onlyNewGame_thenPickSlot() {
        SaveMenu.Choice c = run(store(), "1", "2");
        assertEquals(SaveMenu.Choice.Kind.NEW_GAME, c.kind());
        assertEquals(2, c.slot());
    }

    @Test
    void existingSave_canBeContinued() {
        SaveStore s = store();
        s.save(3, "Hero", new JsonObject());
        SaveMenu.Choice c = run(s, "2"); // 1 = New game, 2 = Continue slot 3
        assertEquals(SaveMenu.Choice.Kind.LOAD, c.kind());
        assertEquals(3, c.slot());
    }

    @Test
    void newGameOnUsedSlot_asksBeforeOverwriting() {
        SaveStore s = store();
        s.save(1, "Hero", new JsonObject());
        // New game -> slot 1 -> "No" (back to menu) -> New game -> slot 2
        SaveMenu.Choice c = run(s, "1", "1", "2", "1", "2");
        assertEquals(2, c.slot());
        assertTrue(s.exists(1));
    }

    @Test
    void deleteSave_removesFile() {
        SaveStore s = store();
        s.save(2, "Hero", new JsonObject());
        // 3 = Delete a save -> slot 2 -> Yes -> back out
        assertEquals(SaveMenu.Choice.BACK, run(s, "3", "2", "1", "0"));
        assertFalse(s.exists(2));
    }

    @Test
    void unreadableSave_cannotBeLoaded_butCanBeDeleted() throws IOException {
        SaveStore s = store();
        Files.createDirectories(root.resolve("adventure"));
        Files.writeString(root.resolve("adventure/slot1.json"), "garbage");
        ScriptedIO io = ScriptedIO.of("2", "3", "1", "1", "0");
        assertEquals(SaveMenu.Choice.BACK, new SaveMenu(s, new Menu(io)).start("Saves"));
        assertTrue(io.outputContains("can't be loaded"));
        assertFalse(s.exists(1));
    }
}
