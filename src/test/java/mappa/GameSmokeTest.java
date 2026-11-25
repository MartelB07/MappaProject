package mappa;

import mappa.core.random.SeededRng;
import mappa.testsupport.ScriptedIO;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plays short scripted sessions through the real launcher with the real game data. */
@Timeout(10)
class GameSmokeTest {

    @TempDir
    Path savesRoot;

    private ScriptedIO play(String... script) {
        ScriptedIO io = ScriptedIO.of(script);
        new GameLauncher(TestContent.real(), new SeededRng(1234), io, false).run();
        assertEquals(0, io.remainingInput(), "script not fully consumed:\n" + io.output());
        return io;
    }

    private ScriptedIO playWithSaves(String... script) {
        ScriptedIO io = ScriptedIO.of(script);
        new GameLauncher(TestContent.real(), new SeededRng(1234), io, false, savesRoot).run();
        assertEquals(0, io.remainingInput(), "script not fully consumed:\n" + io.output());
        return io;
    }

    @Test
    void saves_eachModeStartsSavesAndContinues() {
        playWithSaves(
                "1", "1", "1", "Hero", "0",   // Adventure: new game in slot 1, save & exit
                "2", "1", "3", "Tess", "0",   // Dungeons: new game in slot 3, save & exit
                "3", "1", "2", "0",           // Explore: new game in slot 2, save & exit
                "0");
        ScriptedIO io = playWithSaves(
                "1", "2", "0",                // Adventure: continue slot 1
                "2", "2", "0",                // Dungeons: continue slot 3
                "3", "2", "0",                // Explore: continue slot 2
                "0");
        assertTrue(io.outputContains("Welcome back Hero"));
        assertTrue(io.outputContains("Continue  Slot 1: Hero - Level 0 - Home"));
        assertTrue(io.outputContains("Welcome to the Lobby Tess"));
        assertTrue(io.outputContains("Continue  Slot 3: Tess - Level 0 - 0/3 dungeons cleared"));
        assertTrue(io.outputContains("Continue  Slot 2: Day 1"));
        assertTrue(Files.isRegularFile(savesRoot.resolve("adventure/slot1.json")));
        assertTrue(Files.isRegularFile(savesRoot.resolve("dungeons/slot3.json")));
        assertTrue(Files.isRegularFile(savesRoot.resolve("explore/slot2.json")));
    }

    @Test
    void launcher_exitOption_terminates() {
        assertTrue(play("0").outputContains("Goodbye!"));
    }

    @Test
    void infoPage_returnsToMenu() {
        ScriptedIO io = play("4", "", "0");
        assertTrue(io.plainOutput().contains("Mappa Dungeons"));
        assertTrue(io.outputContains("Hardcore"));
    }

    @Test
    void adventure_menus_thenExit() {
        ScriptedIO io = play(
                "1", "Hero",      // Mappa Adventure, name
                "1", "",          // stats, press enter
                "4", "0",         // travel menu, cancel
                "3", "3", "0", "0", // shop -> heals -> back -> leave
                "0", "1",         // exit adventure, confirm
                "0");             // exit launcher
        assertTrue(io.outputContains("Welcome Hero"));
        assertTrue(io.outputContains("Weapon: Gloves"));
        assertTrue(io.outputContains("Potion 1"));
    }

    @Test
    void dungeons_equipStartDungeonAttackLeave() {
        ScriptedIO io = play(
                "2", "Hero",                  // Mappa Dungeons, name
                "2", "1", "1", "0",           // inventory: equip item 1 (Starter Sword), back
                "6", "1", "1", "2", "",       // dungeon 1, Easy, Hardcore, banner
                "1", "1",                     // attack first mob
                "0", "1",                     // leave dungeon, confirm
                "0", "1",                     // exit lobby, confirm
                "0");                         // exit launcher
        assertTrue(io.outputContains("Hardcore"));
        assertTrue(io.outputContains("took 2 damage"));
        assertTrue(io.outputContains("You left the dungeon."));
    }

    @Test
    void dungeons_lockedDungeonCannotStart() {
        ScriptedIO io = play("2", "Hero", "6", "2", "0", "1", "0");
        assertTrue(io.outputContains("You need to clear Dungeon 1 first."));
    }

    @Test
    void explore_gatherThenGiveUp() {
        ScriptedIO io = play("3", "1", "0", "1", "0");
        assertTrue(io.outputContains("You gathered"));
        assertTrue(io.outputContains("You survived 1 day(s)."));
    }
}
