package mappa;

import mappa.core.io.GameIO;
import mappa.core.io.Menu;

/** Descriptions of the three game modes. */
public final class InfoPage {
    private InfoPage() {
    }

    public static void show(Menu menu) {
        GameIO io = menu.io();
        io.println();
        io.println("Mappa Adventure:");
        io.println("A text based roguelike where players fight different monsters and level up until they've reached "
                + "the max level (Level 10) and can attempt to defeat the Boss in the Hidden Village. Players travel "
                + "across areas with different enemies, buy and sell items at the shop, and test their luck with chests.");
        io.println("*Best for simple combat gameplay.*");
        io.println();
        io.println("Mappa Dungeons:");
        io.println("A text based dungeon crawler where players clear rooms of mobs and a boss within a limited number "
                + "of turns, using melee, spells, and healing. Regular runs let you respawn at the start of your "
                + "current room for a turn penalty and reward 2 items; Hardcore runs give one life and reward 3 items. "
                + "Rewards range from Common to Legendary (Legendary items only drop on Nightmare difficulty). Clear a "
                + "dungeon to unlock the next one.");
        io.println("*Best for players who love collecting rare items.*");
        io.println();
        io.println("Mappa Explore (prototype):");
        io.println("A text based survival game in which players gather resources, eat and drink to stay alive, and "
                + "explore an open world of different biomes for as many days as they can.");
        io.println("*Best for players who want a more laid back survival experience.*");
        io.println();
        menu.pause();
    }
}
