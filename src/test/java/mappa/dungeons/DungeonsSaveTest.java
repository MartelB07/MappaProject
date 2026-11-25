package mappa.dungeons;

import com.google.gson.JsonObject;
import mappa.core.content.ContentRegistry;
import mappa.core.items.ItemInstance;
import mappa.testsupport.FixedRng;
import mappa.testsupport.ScriptedIO;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static mappa.dungeons.DungeonPlayer.Attribute.MAGE;
import static mappa.dungeons.DungeonPlayer.Attribute.WAR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(5)
class DungeonsSaveTest {
    private final ContentRegistry content = TestContent.small();
    private final DungeonsConfig config = DungeonsConfig.from(content);
    private final List<String> warnings = new ArrayList<>();

    @TempDir
    Path root;

    private static ItemInstance find(DungeonPlayer p, String defId) {
        return p.inventory().find(i -> i.definitionId().equals(defId)).get(0).item();
    }

    private DungeonPlayer richPlayer() {
        DungeonPlayer p = new DungeonPlayer("Tess", config, content);
        p.addExp(15); // level 2, 2 points
        p.spendPoints(WAR, 1);
        p.wallet().add(30);
        p.equipGear(find(p, "t_sword"));
        p.equipSpell(find(p, "t_bolt"), 0);
        p.equipSpell(find(p, "t_heal"), 1);
        p.inventory().add(ItemInstance.rolled(content.item("t_helm"), content.rarity("RARE"), 1.2));
        p.markCleared("dungeon_a");
        return p;
    }

    @Test
    @Tag("regression")
    @DisplayName("ST-14: everything about a Dungeons character survives save and load")
    void fullState_roundTrips() {
        DungeonPlayer p = richPlayer();
        DungeonPlayer q = DungeonsSave.read(DungeonsSave.write(p), content, config, warnings);
        assertTrue(warnings.isEmpty(), warnings.toString());
        assertEquals("Tess", q.name());
        assertEquals(2, q.level());
        assertEquals(p.progression().exp(), q.progression().exp());
        assertEquals(1, q.points(WAR));
        assertEquals(0, q.points(MAGE));
        assertEquals(1, q.availablePoints());
        assertEquals(30, q.wallet().gold());
        assertEquals(p.attackDamage(), q.attackDamage());
        assertEquals(p.health().max(), q.health().max());
        assertEquals("t_bolt", q.spell(0).definitionId());
        assertEquals("t_heal", q.spell(1).definitionId());
        assertEquals("RARE", find(q, "t_helm").rarity().id());
        assertTrue(q.hasCleared("dungeon_a"));
        assertTrue(DungeonsGame.isUnlocked(q, content.dungeon("dungeon_b")), "unlocks survive a reload");
    }

    @Test
    void pointsBeyondWhatTheLevelEarned_areDropped() {
        JsonObject data = DungeonsSave.write(richPlayer());
        data.getAsJsonObject("player").getAsJsonObject("points").addProperty("WAR", 50);
        DungeonPlayer q = DungeonsSave.read(data, content, config, warnings);
        assertEquals(2, q.points(WAR) + q.availablePoints());
    }

    @Test
    void deletedDungeon_inClearedList_isReported() {
        JsonObject data = DungeonsSave.write(richPlayer());
        data.getAsJsonArray("cleared").add("dungeon_zzz");
        DungeonsSave.read(data, content, config, warnings);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("dungeon_zzz"));
    }

    @Test
    void newGame_saveAndExit_thenContinue() {
        new DungeonsGame(content, new FixedRng(), ScriptedIO.of("1", "2", "Tess", "0"), false, root).run();

        ScriptedIO io = ScriptedIO.of("2", "0");
        DungeonsGame g = new DungeonsGame(content, new FixedRng(), io, false, root);
        g.run();
        assertTrue(io.outputContains("Slot 2: Tess - Level 0 - 0/2 dungeons cleared"));
        assertEquals("Tess", g.player().name());
        assertEquals(2, g.slot());
        assertEquals(3, g.player().inventory().usedSlots(), "starter items came back");
    }

    @Test
    void playingWithoutSaves_neverWritesFiles() {
        new DungeonsGame(content, new FixedRng(), ScriptedIO.of("Tess", "0", "1"), false).run();
        assertFalse(root.resolve("dungeons").toFile().exists());
    }
}
