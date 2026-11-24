package mappa.dungeons;

import mappa.core.content.ContentRegistry;
import mappa.core.content.Difficulty;
import mappa.core.content.DungeonDefinition;
import mappa.core.inventory.ItemStack;
import mappa.core.io.Ansi;
import mappa.core.io.GameIO;
import mappa.core.io.Menu;
import mappa.core.items.ItemInstance;
import mappa.core.random.Rng;

import java.util.ArrayList;
import java.util.List;

/** Mappa Dungeons lobby: character management and dungeon selection. */
public class DungeonsGame {
    private final ContentRegistry content;
    private final DungeonsConfig config;
    private final Rng rng;
    private final Menu menu;
    private final GameIO io;
    private final boolean devMode;
    private DungeonPlayer player;

    public DungeonsGame(ContentRegistry content, Rng rng, GameIO io, boolean devMode) {
        this.content = content;
        this.config = DungeonsConfig.from(content);
        this.rng = rng;
        this.io = io;
        this.menu = new Menu(io);
        this.devMode = devMode;
    }

    public DungeonPlayer player() {
        return player;
    }

    public void run() {
        io.println();
        io.println("Running Mappa Dungeons...");
        io.println("*New players: open your inventory and equip a starter weapon and spell first.*");
        player = new DungeonPlayer(menu.readName("Give your player a name: ", 20), config, content);
        if (devMode) {
            player.wallet().add(99_999);
            io.println(Ansi.color("DEVELOPER MODE", Ansi.Color.RED));
        }
        DungeonInventoryScreen screens = new DungeonInventoryScreen(player, menu);
        io.println();
        io.println("Welcome to the Lobby " + player.name());
        while (true) {
            player.health().fill();
            int c = menu.choose("", "exit the game", "Open Stats", "Open Inventory", "Open Attributes",
                    "Test Damage", "Sell Items", "Start a Dungeon");
            switch (c) {
                case Menu.CANCEL -> {
                    if (menu.confirm("Are you sure you want to exit? Your data will be lost.")) {
                        io.println("Returning to home page...");
                        return;
                    }
                }
                case 1 -> screens.showStats();
                case 2 -> screens.manage();
                case 3 -> screens.attributes();
                case 4 -> testDamage();
                case 5 -> sell();
                case 6 -> startDungeon();
                default -> { }
            }
        }
    }

    private void testDamage() {
        io.println();
        io.println("Melee damage per hit: " + player.attackDamage()
                + (player.weapon() == null ? " (no weapon equipped)" : " with " + player.weapon().name()));
        for (int s = 0; s < DungeonPlayer.SPELL_SLOTS; s++) {
            ItemInstance spell = player.spell(s);
            if (spell != null) {
                String what = spell.healAmount() > 0 ? "heals " + spell.healAmount()
                        : "deals " + player.spellDamage(spell) + " to " + spell.effect().target().name().toLowerCase();
                io.println("Spell " + (s + 1) + " (" + spell.name() + "): " + what
                        + ", cooldown " + spell.effect().cooldown() + " turn(s)");
            }
        }
        io.println("Defense: " + player.defense() + "   Max health: " + player.health().max());
    }

    private void sell() {
        while (true) {
            List<ItemStack> stacks = new ArrayList<>(player.inventory().stacks());
            if (stacks.isEmpty()) {
                io.println("No item to sell.");
                return;
            }
            List<String> labels = new ArrayList<>();
            for (ItemStack s : stacks) {
                labels.add(DungeonInventoryScreen.describe(s.item(), s.count()) + "  -> "
                        + s.item().sellValue(config.sellPercent()) + " gold");
            }
            io.println();
            io.println("*Items sell for " + config.sellPercent() + "% of their value.*  Gold: " + player.wallet().gold());
            int pick = menu.chooseFrom("Which item would you like to sell?", labels, "go back");
            if (pick == Menu.CANCEL) {
                return;
            }
            ItemInstance item = stacks.get(pick - 1).item();
            int value = item.sellValue(config.sellPercent());
            if (menu.confirm("Sell " + item.displayName() + " for " + value + " gold?")) {
                player.inventory().remove(item);
                player.wallet().add(value);
                io.println("You sold " + item.name() + " for " + value + " gold.");
            }
        }
    }

    private void startDungeon() {
        List<DungeonDefinition> dungeons = content.dungeons();
        List<String> labels = new ArrayList<>();
        for (DungeonDefinition d : dungeons) {
            String lock = isUnlocked(d) ? "" : Ansi.color("  [Locked: clear "
                    + content.dungeon(d.unlockRequires()).name() + " first]", Ansi.Color.GRAY);
            String cleared = player.hasCleared(d.id()) ? Ansi.color("  (cleared)", Ansi.Color.GREEN) : "";
            labels.add(d.name() + cleared + lock);
        }
        int pick = menu.chooseFrom("Which dungeon would you like to run?", labels, "return");
        if (pick == Menu.CANCEL) {
            return;
        }
        DungeonDefinition dungeon = dungeons.get(pick - 1);
        if (!isUnlocked(dungeon)) {
            io.println("You need to clear " + content.dungeon(dungeon.unlockRequires()).name() + " first.");
            return;
        }
        Difficulty difficulty = selectDifficulty(dungeon);
        if (difficulty == null) {
            return;
        }
        RunMode mode = selectMode();
        if (mode == null) {
            return;
        }
        new DungeonRunScreen(new DungeonRun(content, dungeon, difficulty, mode, player, rng, io), menu).play();
    }

    private boolean isUnlocked(DungeonDefinition d) {
        return isUnlocked(player, d);
    }

    /** A dungeon is playable once the dungeon it requires has been cleared (any difficulty/mode). */
    static boolean isUnlocked(DungeonPlayer player, DungeonDefinition d) {
        return d.unlockRequires() == null || player.hasCleared(d.unlockRequires());
    }

    private Difficulty selectDifficulty(DungeonDefinition dungeon) {
        List<Difficulty> diffs = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Difficulty d : content.difficulties()) {
            DungeonDefinition.Settings s = dungeon.difficulties().get(d.id());
            if (s != null) {
                diffs.add(d);
                labels.add(d.colored() + " [REQ: Level " + s.levelReq() + "]  (items up to level "
                        + s.rewardLevelCap() + ")");
            }
        }
        while (true) {
            int pick = menu.chooseFrom("Which difficulty would you like to run?", labels, "return");
            if (pick == Menu.CANCEL) {
                return null;
            }
            Difficulty d = diffs.get(pick - 1);
            int req = dungeon.difficulties().get(d.id()).levelReq();
            if (player.level() >= req) {
                return d;
            }
            io.println("You need to be level " + req + " to play this difficulty.");
        }
    }

    private RunMode selectMode() {
        List<String> labels = new ArrayList<>();
        for (RunMode m : config.runModes()) {
            labels.add(m.colored() + " - " + m.description());
        }
        int pick = menu.chooseFrom("Which run mode?", labels, "return");
        return pick == Menu.CANCEL ? null : config.runModes().get(pick - 1);
    }
}
