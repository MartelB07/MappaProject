package mappa.core.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Breaks a copy of the test data in specific ways and checks the error messages. */
class JsonContentLoaderTest {
    private static final List<String> FILES = List.of("index.json", "rarities.json", "difficulties.json",
            "level_curves.json", "items.json", "mobs.json", "dungeon_a.json", "dungeon_b.json", "regions.json",
            "chests.json", "biomes.json", "dungeons_config.json", "adventure_config.json", "explore_config.json");

    @TempDir
    Path dir;

    private void copyTestData() throws IOException {
        for (String f : FILES) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("testdata/" + f)) {
                Files.copy(in, dir.resolve(f));
            }
        }
    }

    private void replace(String file, String from, String to) throws IOException {
        Path p = dir.resolve(file);
        String s = Files.readString(p);
        assertTrue(s.contains(from), "test setup: '" + from + "' not in " + file);
        Files.writeString(p, s.replace(from, to));
    }

    private ContentException loadFails() {
        return assertThrows(ContentException.class, () -> JsonContentLoader.loadDirectory(dir));
    }

    @Test
    void validCopy_loads() throws IOException {
        copyTestData();
        assertEquals(2, JsonContentLoader.loadDirectory(dir).dungeons().size());
    }

    @Test
    void malformedJson_reportsFileName() throws IOException {
        copyTestData();
        Files.writeString(dir.resolve("mobs.json"), "[ { \"id\": ");
        assertTrue(loadFails().getMessage().contains("mobs.json"));
    }

    @Test
    void unknownStatType_reportsValidValues() throws IOException {
        copyTestData();
        replace("items.json", "\"WAR_DAMAGE\": 5", "\"WAR_DAMGE\": 5");
        String msg = loadFails().getMessage();
        assertTrue(msg.contains("WAR_DAMGE") && msg.contains("t_sword") && msg.contains("MAGE_DAMAGE"), msg);
    }

    @Test
    void missingRequiredField_reported() throws IOException {
        copyTestData();
        replace("items.json", "\"name\": \"Test Sword\",", "");
        assertTrue(loadFails().getMessage().contains("missing required field \"name\""));
    }

    @Test
    void duplicateId_reported() throws IOException {
        copyTestData();
        replace("items.json", "\"id\": \"t_staff\"", "\"id\": \"t_sword\"");
        assertTrue(loadFails().getMessage().contains("duplicate item id 't_sword'"));
    }

    @Test
    void unknownMobInWave_reported() throws IOException {
        copyTestData();
        replace("dungeon_a.json", "[ [\"t_grunt\"] ]", "[ [\"t_gruntt\"] ]");
        assertTrue(loadFails().getMessage().contains("unknown mob 't_gruntt'"));
    }

    @Test
    void unlockLoop_reported() throws IOException {
        copyTestData();
        replace("dungeon_a.json", "\"order\": 1,", "\"order\": 1, \"unlockRequires\": \"dungeon_b\",");
        assertTrue(loadFails().getMessage().contains("loop"));
    }

    @Test
    void rewardCapTooLow_reported() throws IOException {
        copyTestData();
        replace("dungeon_b.json", "\"rewardLevelCap\": 5", "\"rewardLevelCap\": 1");
        assertTrue(loadFails().getMessage().contains("no reward item"));
    }

    @Test
    void unreachableRegion_reported() throws IOException {
        copyTestData();
        replace("regions.json", "\"neighbors\": [\"t_town\"] },\n  { \"id\": \"t_town\"",
                "\"neighbors\": [] },\n  { \"id\": \"t_town\"");
        assertTrue(loadFails().getMessage().contains("not reachable"));
    }

    @Test
    void overlappingRarities_reported() throws IOException {
        copyTestData();
        replace("rarities.json", "[1.15, 1.50]", "[1.10, 1.50]");
        assertTrue(loadFails().getMessage().contains("overlaps"));
    }

    @Test
    void unlistedType_reported() throws IOException {
        copyTestData();
        replace("index.json", "\"rarities\":", "\"raritys\": [], \"rarities\":");
        assertTrue(loadFails().getMessage().contains("unknown content type 'raritys'"));
    }
}
