package mappa.core.content;

import mappa.core.items.ItemDefinition;
import mappa.core.items.ItemSlot;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against the REAL game data. This test grows with the game: every new JSON file
 * is loaded and validated automatically once it is listed in data/index.json.
 */
class ContentValidationTest {
    private static final Path DATA = Path.of("src/main/resources/data");

    @Test
    void allFiles_loadAndValidateWithoutErrors() {
        assertDoesNotThrow(() -> JsonContentLoader.loadDirectory(DATA));
    }

    @Test
    void everyJsonFileUnderData_isListedInIndex() throws IOException {
        List<String> indexed = JsonContentLoader.indexedFiles(p -> Files.newInputStream(DATA.resolve(p)));
        Set<String> onDisk = new HashSet<>();
        try (Stream<Path> files = Files.walk(DATA)) {
            files.filter(f -> f.toString().endsWith(".json"))
                    .map(f -> DATA.relativize(f).toString().replace('\\', '/'))
                    .filter(f -> !f.equals(JsonContentLoader.INDEX))
                    .forEach(onDisk::add);
        }
        Set<String> missing = new HashSet<>(onDisk);
        indexed.forEach(missing::remove);
        assertTrue(missing.isEmpty(), "Files not listed in data/index.json: " + missing);
        assertEquals(onDisk.size(), new HashSet<>(indexed).size(), "index lists a file twice or a missing file");
    }

    @Test
    @Tag("regression")
    @DisplayName("D-01/D-12: every dungeon difficulty has a droppable reward")
    void everyDifficulty_hasDroppableReward() {
        ContentRegistry c = TestContent.real();
        for (DungeonDefinition d : c.dungeons()) {
            List<ItemDefinition> pool = ContentValidator.rewardPool(c, d);
            d.difficulties().forEach((diff, s) -> assertTrue(
                    pool.stream().anyMatch(i -> i.levelReq() <= s.rewardLevelCap()), d.id() + " " + diff));
        }
    }

    @Test
    @Tag("regression")
    @DisplayName("D-06: every consumable and healing spell heals a positive amount")
    void healingItems_healPositive() {
        for (ItemDefinition i : TestContent.real().items()) {
            if (i.slot() == ItemSlot.CONSUMABLE || i.hasTag("healing")) {
                assertTrue(i.effect().heal() > 0 || i.effect().damage() > 0, i.id());
            }
        }
    }

    @Test
    @Tag("regression")
    @DisplayName("D-15: item names are unique within each game mode")
    void itemNames_unique() {
        Set<String> seen = new HashSet<>();
        for (ItemDefinition i : TestContent.real().items()) {
            String mode = i.id().substring(0, i.id().indexOf('_'));
            assertTrue(seen.add(mode + ":" + i.name()), "duplicate name " + i.name());
        }
    }

    @Test
    @Tag("regression")
    @DisplayName("A-08/A-09: regions link both ways and always have a mob to fight")
    void regions_twoWayAndFightable() {
        ContentRegistry c = TestContent.real();
        for (RegionDefinition r : c.regions()) {
            for (String n : r.neighbors()) {
                assertTrue(c.region(n).neighbors().contains(r.id()), r.id() + " <-> " + n);
            }
            assertTrue(r.mobs().stream().anyMatch(s -> s.minLevel() <= r.levelReq()), r.id());
        }
    }

    @Test
    void dungeonUnlockChain_followsOrder() {
        List<DungeonDefinition> list = TestContent.real().dungeons();
        assertFalse(list.isEmpty());
        assertEquals(null, list.get(0).unlockRequires());
    }
}
