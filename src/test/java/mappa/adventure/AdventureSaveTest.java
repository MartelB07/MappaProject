package mappa.adventure;

import com.google.gson.JsonObject;
import mappa.core.content.ContentRegistry;
import mappa.core.items.ItemInstance;
import mappa.core.items.ItemSlot;
import mappa.core.save.SaveStore;
import mappa.testsupport.FixedRng;
import mappa.testsupport.ScriptedIO;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(5)
class AdventureSaveTest {
    private final ContentRegistry content = TestContent.small();
    private final AdventureConfig config = AdventureConfig.from(content);

    @TempDir
    Path root;

    private AdventureGame game(ScriptedIO io) {
        return new AdventureGame(content, new FixedRng(), io, false, root);
    }

    private AdventureGame plainGame() {
        return new AdventureGame(content, new FixedRng(), new ScriptedIO(), false);
    }

    private AdventurePlayer richPlayer() {
        AdventurePlayer p = new AdventurePlayer("Ari", config, content);
        p.addExp(7); // level 1, 2 exp left, +10 gold
        p.wallet().add(42);
        ItemInstance rareHelm = ItemInstance.rolled(content.item("t_shop_helm"), content.rarity("RARE"), 1.3);
        p.equipment().equip(rareHelm, p.level());
        p.health().damage(5);
        return p;
    }

    @Test
    @Tag("regression")
    @DisplayName("ST-14: everything about an Adventure character survives save and load")
    void fullState_roundTrips() {
        AdventurePlayer p = richPlayer();
        AdventureGame g = plainGame().withPlayer(p, "t_town");
        g.chests().setCountdown(4);

        AdventureGame loaded = plainGame();
        List<String> warnings = AdventureSave.read(AdventureSave.write(g), loaded, content, config);

        assertTrue(warnings.isEmpty(), warnings.toString());
        AdventurePlayer q = loaded.player();
        assertEquals("Ari", q.name());
        assertEquals(1, q.level());
        assertEquals(2, q.progression().exp());
        assertEquals(p.wallet().gold(), q.wallet().gold());
        assertEquals(p.health().max(), q.health().max());
        assertEquals(p.health().current(), q.health().current());
        assertEquals(p.damage(), q.damage());
        assertEquals(3, q.inventory().count("t_potion"));
        ItemInstance helm = q.equipment().get(ItemSlot.HELMET);
        assertEquals("RARE", helm.rarity().id());
        assertEquals(p.equipment().get(ItemSlot.HELMET).instanceId(), helm.instanceId());
        assertEquals("t_club", q.equipment().get(ItemSlot.WEAPON).definitionId());
        assertEquals("t_town", loaded.location().id());
        assertEquals(4, loaded.chests().countdown());
    }

    @Test
    void deletedRegion_sendsPlayerToStart() {
        JsonObject data = AdventureSave.write(plainGame().withPlayer(richPlayer(), "t_town"));
        data.addProperty("location", "atlantis");
        AdventureGame loaded = plainGame();
        List<String> warnings = AdventureSave.read(data, loaded, content, config);
        assertEquals("t_home", loaded.location().id());
        assertTrue(warnings.get(0).contains("atlantis"));
    }

    @Test
    void regionAboveLevel_sendsPlayerToStart() {
        JsonObject data = AdventureSave.write(plainGame().withPlayer(richPlayer(), "t_castle"));
        AdventureGame loaded = plainGame();
        assertEquals(1, AdventureSave.read(data, loaded, content, config).size());
        assertEquals("t_home", loaded.location().id());
    }

    @Test
    void newGame_saveAndExit_thenContinue() {
        ScriptedIO first = ScriptedIO.of("1", "1", "Ari", "0");
        game(first).run();
        assertTrue(first.outputContains("Game saved to slot 1"));
        assertTrue(Files.isRegularFile(root.resolve("adventure/slot1.json")));

        ScriptedIO second = ScriptedIO.of("2", "0");
        AdventureGame g = game(second);
        g.run();
        assertTrue(second.outputContains("Continue  Slot 1: Ari - Level 0 - Test Home"));
        assertTrue(second.outputContains("Welcome back Ari"));
        assertEquals("Ari", g.player().name());
        assertEquals(1, g.slot());
    }

    @Test
    void backingOutOfSaveMenu_returnsWithoutPlaying() {
        AdventureGame g = game(ScriptedIO.of("0"));
        g.run();
        assertEquals(null, g.player());
    }

    @Test
    @DisplayName("Dying in Adventure keeps the last save (deleteOnDeath: false)")
    void death_keepsLastSave() {
        AdventurePlayer weak = new AdventurePlayer("Ari", config, content);
        weak.health().set(1);
        new SaveStore(root, AdventureSave.MODE, 3, AdventureSave.FORMAT)
                .save(1, "Ari", AdventureSave.write(plainGame().withPlayer(weak, "t_home")));

        ScriptedIO io = ScriptedIO.of("2", "5", "1"); // continue, fight, attack -> the rat kills you
        game(io).run();
        assertTrue(io.outputContains("You Died"));
        assertTrue(io.outputContains("last save in slot 1 is still there"));
        assertTrue(Files.isRegularFile(root.resolve("adventure/slot1.json")));
    }
}
