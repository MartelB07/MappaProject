package mappa;

import mappa.core.io.Menu;
import mappa.core.ui.Tone;
import mappa.core.ui.Ui;

/** Descriptions of the three game modes, one banner each in that mode's color. */
public final class InfoPage {
    private InfoPage() {
    }

    public static void show(Menu menu) {
        Ui ui = menu.ui();
        ui.banner(Tone.SUCCESS, "Mappa Adventure",
                "A roguelike where you fight monsters and level up until you reach the max level (Level 10)"
                        + " and can face the Boss in the Hidden Village. Travel between areas with different"
                        + " enemies, buy and sell at the shop, and test your luck with chests.",
                "Best for simple combat gameplay.");
        ui.banner(Tone.MAGIC, "Mappa Dungeons",
                "A dungeon crawler: clear rooms of mobs and a boss within a limited number of turns, using"
                        + " melee, spells and healing.",
                "Regular runs: respawn at the start of your current room for a turn penalty, win 2 items.",
                "Hardcore runs: one life, win 3 items.",
                "Loot ranges from Common to Legendary (Legendary only on Nightmare). Clear a dungeon to unlock"
                        + " the next one.",
                "Best for players who love collecting rare items.");
        ui.banner(Tone.INFO, "Mappa Explore (prototype)",
                "A survival game: gather resources, eat and drink to stay alive, and explore an open world of"
                        + " different biomes for as many days as you can.",
                "Best for players who want a more laid back survival experience.");
        menu.pause();
    }
}
