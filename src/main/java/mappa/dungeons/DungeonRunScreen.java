package mappa.dungeons;

import mappa.core.io.Ansi;
import mappa.core.io.GameIO;
import mappa.core.io.Menu;
import mappa.core.items.ItemInstance;

import java.util.ArrayList;
import java.util.List;

/** Menus for playing a {@link DungeonRun}. */
final class DungeonRunScreen {
    private final DungeonRun run;
    private final Menu menu;
    private final GameIO io;
    private final DungeonInventoryScreen inventory;

    DungeonRunScreen(DungeonRun run, Menu menu) {
        this.run = run;
        this.menu = menu;
        this.io = menu.io();
        this.inventory = new DungeonInventoryScreen(run.player(), menu);
    }

    void play() {
        banner();
        while (run.inProgress()) {
            showRoom();
            List<String> options = new ArrayList<>(List.of("Attack", "Use Spell", "Open Inventory (changing gear uses a turn)",
                    "Open Attributes"));
            boolean canMove = run.canMoveOn();
            if (canMove) {
                options.add("Move On");
            } else if (run.isBossRoom()) {
                options.add("Dodge");
            }
            int c = menu.chooseFrom("", options, "leave the dungeon");
            switch (c) {
                case Menu.CANCEL -> {
                    if (menu.confirm("Leave the dungeon? You won't get any rewards.")) {
                        run.abandon();
                    }
                }
                case 1 -> attack();
                case 2 -> spell();
                case 3 -> {
                    if (inventory.manage()) {
                        run.spendTurn();
                    }
                }
                case 4 -> inventory.attributes();
                case 5 -> {
                    if (canMove) {
                        run.moveOn();
                    } else {
                        run.dodge();
                    }
                }
                default -> { }
            }
        }
        summary();
    }

    private void banner() {
        String title = "  " + run.dungeon().name() + "  " + run.difficulty().colored() + "  " + run.mode().colored() + "  ";
        io.println();
        io.println("--------------------------------------------");
        io.println(title);
        io.println("  " + run.mode().description());
        io.println("--------------------------------------------");
        menu.pause();
    }

    private void showRoom() {
        io.println();
        String lives = run.livesLeft() < 0 ? "unlimited" : String.valueOf(run.livesLeft());
        io.println("TURNS LEFT: " + run.turnsLeft() + "   ROOM: " + (run.room() + 1) + "/" + run.roomCount()
                + "   LIVES: " + lives);
        io.println("--------------------------------------------");
        for (DungeonRun.RoomMob m : run.mobs()) {
            if (!m.mob().isAlive()) {
                continue;
            }
            io.println((m.mob().isBoss() ? Ansi.color(" BOSS ", Ansi.Color.RED) : " ") + m.mob().label()
                    + "  Health: " + Ansi.health(m.mob().health(), m.mob().maxHealth()));
            io.println("   Attack: " + m.telegraph());
        }
        if (run.roomCleared() && run.canMoveOn()) {
            io.println(Ansi.color(" Room cleared! Move on when you're ready.", Ansi.Color.GREEN));
        }
        io.println("--------------------------------------------");
        DungeonPlayer p = run.player();
        io.println(" [Level " + p.level() + "] " + p.name() + "  Health: " + Ansi.health(p.health().current(), p.health().max()));
    }

    private void attack() {
        if (run.player().attackDamage() <= 0) {
            io.println("You have no weapon equipped.");
            return;
        }
        List<DungeonRun.RoomMob> alive = run.aliveMobs();
        if (alive.isEmpty()) {
            io.println("No mob to attack.");
            return;
        }
        List<String> labels = new ArrayList<>();
        for (DungeonRun.RoomMob m : alive) {
            labels.add(m.mob().label() + "  " + Ansi.health(m.mob().health(), m.mob().maxHealth()));
        }
        int pick = menu.chooseFrom("Which mob would you like to attack?", labels, "cancel");
        if (pick != Menu.CANCEL) {
            run.attack(pick - 1);
        }
    }

    private void spell() {
        List<String> labels = new ArrayList<>();
        List<Integer> slots = new ArrayList<>();
        for (int s = 0; s < DungeonPlayer.SPELL_SLOTS; s++) {
            ItemInstance spell = run.player().spell(s);
            if (spell != null) {
                int wait = run.cooldownRemaining(spell);
                labels.add(spell.displayName() + "  (" + spell.statSummary() + ")"
                        + (wait > 0 ? Ansi.color("  ready in " + wait + " turn(s)", Ansi.Color.GRAY) : ""));
                slots.add(s);
            }
        }
        if (labels.isEmpty()) {
            io.println("You have no spells equipped.");
            return;
        }
        int pick = menu.chooseFrom("Which spell would you like to use?", labels, "cancel");
        if (pick != Menu.CANCEL) {
            run.castSpell(slots.get(pick - 1));
        }
    }

    private void summary() {
        io.println();
        switch (run.status()) {
            case CLEARED -> {
                io.println(Ansi.color("Dungeon cleared!", Ansi.Color.GREEN) + " (" + run.mode().name() + ": "
                        + run.mode().rewardItems() + " reward items)");
                for (ItemInstance i : run.rewards()) {
                    io.println("You received " + DungeonInventoryScreen.describe(i, 1));
                }
                for (ItemInstance i : run.rewardsLost()) {
                    io.println(Ansi.color("No inventory space for " + i.displayName(), Ansi.Color.RED));
                }
            }
            case DIED -> io.println("You were defeated. No rewards this time.");
            case OUT_OF_TURNS -> io.println("You ran out of turns. No rewards this time.");
            case ABANDONED -> io.println("You left the dungeon.");
            default -> { }
        }
        io.println("Returning to lobby...");
    }
}
