package mappa.core.save;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveStoreTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T19:40:12Z"), ZoneOffset.UTC);

    @TempDir
    Path root;

    private SaveStore store(int format) {
        return new SaveStore(root, "adventure", 3, format, CLOCK);
    }

    private static JsonObject data(String name) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        return o;
    }

    @Test
    void saveThenLoad_roundTripsData() {
        SaveStore s = store(1);
        s.save(2, "Hero - Level 7 - Old Town", data("Hero"));
        assertTrue(Files.isRegularFile(root.resolve("adventure/slot2.json")));
        assertEquals("Hero", s.load(2).get("name").getAsString());
        SaveSummary sum = s.summary(2).orElseThrow();
        assertTrue(sum.readable());
        assertEquals("Hero - Level 7 - Old Town (saved 2026-11-24 19:40)", sum.describe());
    }

    @Test
    void save_leavesNoTempFileBehind() throws IOException {
        store(1).save(1, "x", data("a"));
        store(1).save(1, "x", data("b"));
        try (var files = Files.list(root.resolve("adventure"))) {
            assertEquals(1, files.count());
        }
        assertEquals("b", store(1).load(1).get("name").getAsString());
    }

    @Test
    void emptySlot_noSummary_loadFails() {
        SaveStore s = store(1);
        assertTrue(s.summary(1).isEmpty());
        assertFalse(s.anyExist());
        assertThrows(SaveException.class, () -> s.load(1));
    }

    @Test
    void delete_removesSlot() {
        SaveStore s = store(1);
        s.save(3, "x", data("a"));
        assertTrue(s.delete(3));
        assertFalse(s.exists(3));
        assertFalse(s.delete(3));
    }

    @Test
    void damagedFile_isMarkedUnreadable_andLoadExplains() throws IOException {
        SaveStore s = store(1);
        Files.createDirectories(root.resolve("adventure"));
        Files.writeString(root.resolve("adventure/slot1.json"), "{ not json");
        SaveSummary sum = s.summary(1).orElseThrow();
        assertFalse(sum.readable());
        assertTrue(sum.text().contains("unreadable"));
        SaveException e = assertThrows(SaveException.class, () -> s.load(1));
        assertTrue(e.getMessage().contains("damaged"));
    }

    @Test
    void otherModesSave_isRefused() throws IOException {
        new SaveStore(root, "dungeons", 3, 1, CLOCK).save(1, "x", data("a"));
        SaveStore adventure = store(1);
        assertTrue(adventure.summary(1).isEmpty(), "each mode has its own folder");
        Files.createDirectories(root.resolve("adventure"));
        Files.copy(root.resolve("dungeons/slot1.json"), root.resolve("adventure/slot1.json"));
        SaveException e = assertThrows(SaveException.class, () -> adventure.load(1));
        assertTrue(e.getMessage().contains("not 'adventure'"));
    }

    @Test
    void newerFormat_isRefusedNotMisread() {
        store(2).save(1, "Hero", data("Hero"));
        SaveStore older = store(1);
        assertFalse(older.summary(1).orElseThrow().readable());
        SaveException e = assertThrows(SaveException.class, () -> older.load(1));
        assertTrue(e.getMessage().contains("newer version"));
    }

    @Test
    void olderFormat_isUpgradedByMigration() {
        store(1).save(1, "Hero", data("Hero"));
        SaveStore v2 = store(2).withMigration(1, d -> {
            d.addProperty("addedInV2", 42);
            return d;
        });
        JsonObject loaded = v2.load(1);
        assertEquals("Hero", loaded.get("name").getAsString());
        assertEquals(42, loaded.get("addedInV2").getAsInt());
    }

    @Test
    void olderFormat_withoutMigration_explains() {
        store(1).save(1, "Hero", data("Hero"));
        SaveException e = assertThrows(SaveException.class, () -> store(3).load(1));
        assertTrue(e.getMessage().contains("no upgrade"));
    }

    @Test
    void slotOutOfRange_rejected() {
        assertThrows(IllegalArgumentException.class, () -> store(1).file(0));
        assertThrows(IllegalArgumentException.class, () -> store(1).file(4));
    }

    @Test
    void settings_defaultsAndLimits() {
        assertEquals(SaveSettings.DEFAULT, SaveSettings.from(new JsonObject()));
        JsonObject cfg = new JsonObject();
        JsonObject saves = new JsonObject();
        saves.addProperty("slots", 5);
        cfg.add("saves", saves);
        assertEquals(5, SaveSettings.from(cfg).slots());
        saves.addProperty("slots", 0);
        assertThrows(RuntimeException.class, () -> SaveSettings.from(cfg));
    }
}
