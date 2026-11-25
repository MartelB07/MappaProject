package mappa.dungeons;

import mappa.core.io.Ansi;
import mappa.core.io.Menu;
import mappa.core.items.ItemInstance;
import mappa.core.ui.Text;
import mappa.core.ui.Tone;
import mappa.core.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** Menus for playing a {@link DungeonRun}. */
final class DungeonRunScreen {
    private final DungeonRun run;
    private final Menu menu;
    private final Ui ui;
    private final DungeonInventoryScreen inventory;

    DungeonRunScreen(DungeonRun run, Menu menu) {
        this.run = run;
        this.menu = menu;
        this.ui = menu.ui();
        this.inventory = new DungeonInventoryScreen(run.player(), menu);
    }

    void play() {
        banner();
        while (run.inProgress()) {
            showRoom();
            List<String> options = new ArrayList<>(List.of("Attack", "Use Spell",
                    "Open Inventory (changing gear uses a turn)", "Open Attributes"));
            boolean canMove = run.canMoveOn();
            if (canMove) {
                options.add("Move On");
            } else if (run.isBossRoom()) {
                options.add("Dodge");
            }
            int c = menu.chooseFrom("Your move:", options, "leave the dungeon");
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
        List<String> lines = new ArrayList<>();
        if (!run.dungeon().description().isEmpty()) {
            lines.add(run.dungeon().description());
        }
        lines.add("Difficulty: " + run.difficulty().colored() + "   Mode: " + run.mode().colored());
        lines.add(run.mode().description());
        lines.add("Turns: " + run.turnsLeft() + "   Rooms: " + run.roomCount());
        ui.banner(Tone.MAGIC, "Entering " + run.dungeon().name(), lines);
        menu.pause();
    }

    private void showRoom() {
        String lives = run.livesLeft() < 0 ? "unlimited" : String.valueOf(run.livesLeft());
        Ansi.Color turnColor = run.turnsLeft() <= 10 ? Ansi.Color.RED
                : run.turnsLeft() <= 25 ? Ansi.Color.YELLOW : Ansi.Color.DEFAULT;
        DungeonPlayer p = run.player();

        List<DungeonRun.RoomMob> alive = run.aliveMobs();
        int nameWidth = Text.visibleWidth(p.name()) + 10;
        for (DungeonRun.RoomMob m : alive) {
            nameWidth = Math.max(nameWidth, Text.visibleWidth(m.mob().label()) + (m.mob().isBoss() ? 5 : 0));
        }
        List<String> lines = new ArrayList<>();
        lines.add("Turns left " + Ansi.bold(String.valueOf(run.turnsLeft()), turnColor)
                + "   Room " + (run.room() + 1) + "/" + run.roomCount() + "   Lives " + lives);
        lines.add("");
        for (DungeonRun.RoomMob m : alive) {
            String name = (m.mob().isBoss() ? Ansi.bold("BOSS ", Ansi.Color.RED) : "") + m.mob().label();
            lines.add(Text.padRight(name, nameWidth) + "  " + ui.bar(m.mob().health(), m.mob().maxHealth(), 12)
                    + "  " + m.telegraph(ui.glyphs()));
        }
        if (alive.isEmpty() && run.canMoveOn()) {
            lines.add(Ansi.color(ui.symbol(Tone.SUCCESS) + " Room cleared! Move on when you're ready.",
                    Ansi.Color.GREEN));
        }
        lines.add("");
        lines.add(Text.padRight(Ansi.bold("[Level " + p.level() + "] " + p.name()), nameWidth) + "  "
                + ui.bar(p.health().current(), p.health().max(), 12));
        ui.header(run.dungeon().name() + (run.isBossRoom() ? "  -  Boss Room" : ""), lines);
    }

    private void attack() {
        if (run.player().attackDamage() <= 0) {
            ui.warn("You have no weapon equipped.");
            return;
        }
        List<DungeonRun.RoomMob> alive = run.aliveMobs();
        if (alive.isEmpty()) {
            ui.warn("No mob to attack.");
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
                        + (wait > 0 ? Ansi.color("  ready in " + wait + " turn(s)", Ansi.Color.GRAY)
                        : Ansi.color("  ready", Ansi.Color.GREEN)));
                slots.add(s);
            }
        }
        if (labels.isEmpty()) {
            ui.warn("You have no spells equipped.");
            return;
        }
        int pick = menu.chooseFrom("Which spell would you like to use?", labels, "cancel");
        if (pick != Menu.CANCEL) {
            run.castSpell(slots.get(pick - 1));
        }
    }

    private void summary() {
        switch (run.status()) {
            case CLEARED -> {
                List<String> lines = new ArrayList<>();
                lines.add(run.mode().name() + " run: " + run.mode().rewardItems() + " reward items");
                for (ItemInstance i : run.rewards()) {
                    lines.add(ui.symbol(Tone.REWARD) + " " + DungeonInventoryScreen.describe(i, 1));
                }
                for (ItemInstance i : run.rewardsLost()) {
                    lines.add(Ansi.color("No inventory space for " + i.displayName(), Ansi.Color.RED));
                }
                ui.banner(Tone.REWARD, run.dungeon().name() + " cleared!", lines);
            }
            case DIED -> ui.banner(Tone.DANGER, "Defeated", "You were defeated in " + run.dungeon().name()
                    + ". No rewards this time.");
            case OUT_OF_TURNS -> ui.banner(Tone.DANGER, "Out of turns",
                    "You ran out of turns in " + run.dungeon().name() + ". No rewards this time.");
            case ABANDONED -> ui.warn("You left the dungeon.");
            default -> { }
        }
        ui.muted("Returning to lobby...");
    }
}
