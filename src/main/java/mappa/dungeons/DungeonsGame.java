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
import mappa.core.save.SaveException;
import mappa.core.save.SaveMenu;
import mappa.core.save.SaveSettings;
import mappa.core.save.SaveStore;
import mappa.core.ui.Tone;
import mappa.core.ui.Ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Mappa Dungeons lobby: character management and dungeon selection. */
public class DungeonsGame {
    /** Accent color for every Dungeons screen. */
    public static final Ansi.Color ACCENT = Ansi.Color.PURPLE;

    private final ContentRegistry content;
    private final DungeonsConfig config;
    private final Rng rng;
    private final Menu menu;
    private final Ui ui;
    private final GameIO io;
    private final boolean devMode;
    private final SaveStore saves;
    private DungeonPlayer player;
    private int slot;

    /** A game without saving (used by tests). */
    public DungeonsGame(ContentRegistry content, Rng rng, GameIO io, boolean devMode) {
        this(content, rng, io, devMode, null);
    }

    /** @param savesRoot folder that holds all save files, or null to play without saving */
    public DungeonsGame(ContentRegistry content, Rng rng, GameIO io, boolean devMode, Path savesRoot) {
        this.content = content;
        this.config = DungeonsConfig.from(content);
        this.rng = rng;
        this.io = io;
        this.ui = new Ui(io, ACCENT);
        this.menu = new Menu(ui);
        this.devMode = devMode;
        SaveSettings settings = SaveSettings.from(content.config("dungeons"));
        this.saves = savesRoot == null ? null
                : new SaveStore(savesRoot, DungeonsSave.MODE, settings.slots(), DungeonsSave.FORMAT);
    }

    public DungeonPlayer player() {
        return player;
    }

    /** The slot this game saves to (0 = not saving). */
    public int slot() {
        return slot;
    }

    public void run() {
        ui.banner(Tone.MAGIC, "Mappa Dungeons", "Clear rooms, beat the boss, collect rare loot.");
        if (!startOrLoad()) {
            return;
        }
        DungeonInventoryScreen screens = new DungeonInventoryScreen(player, menu);
        ui.banner(Tone.MAGIC, "Welcome to the Lobby " + player.name(),
                "Get ready here, then pick a dungeon. Your health is restored every time you return.");
        while (true) {
            player.health().fill();
            lobbyHud();
            List<String> options = new ArrayList<>(List.of("Open Stats", "Open Inventory", "Open Attributes",
                    "Test Damage", "Sell Items", "Start a Dungeon"));
            if (saves != null) {
                options.add("Save Game");
            }
            int c = menu.chooseFrom("What would you like to do?", options,
                    saves != null ? "save and exit" : "exit the game");
            switch (c) {
                case Menu.CANCEL -> {
                    if (saves != null) {
                        autosave();
                        ui.success("Game saved to slot " + slot + ". Returning to home page...");
                        return;
                    }
                    if (menu.confirm("Are you sure you want to exit? Your data will be lost.")) {
                        ui.muted("Returning to home page...");
                        return;
                    }
                }
                case 1 -> screens.showStats();
                case 2 -> screens.manage();
                case 3 -> screens.attributes();
                case 4 -> testDamage();
                case 5 -> sell();
                case 6 -> startDungeon();
                case 7 -> ui.success("Game saved to slot " + slot + ".");
                default -> { }
            }
            autosave(); // lobby only: never mid-run
        }
    }

    /** Shows the save screen (if saving) and sets up the player. Returns false if the player backed out. */
    private boolean startOrLoad() {
        if (saves == null) {
            newCharacter();
            return true;
        }
        while (true) {
            SaveMenu.Choice choice = new SaveMenu(saves, menu).start("Mappa Dungeons");
            switch (choice.kind()) {
                case BACK -> {
                    return false;
                }
                case NEW_GAME -> {
                    slot = choice.slot();
                    newCharacter();
                    autosave();
                    return true;
                }
                case LOAD -> {
                    try {
                        List<String> warnings = new ArrayList<>();
                        player = DungeonsSave.read(saves.load(choice.slot()), content, config, warnings);
                        slot = choice.slot();
                        SaveMenu.showWarnings(ui, warnings);
                        ui.banner(Tone.SUCCESS, "Welcome back " + player.name() + "!", "Loaded from slot " + slot + ".");
                        return true;
                    } catch (SaveException e) {
                        ui.danger(e.getMessage());
                    } catch (RuntimeException e) {
                        ui.danger("Slot " + choice.slot() + " couldn't be loaded (" + e.getMessage() + ").");
                    }
                }
                default -> throw new IllegalStateException();
            }
        }
    }

    private void newCharacter() {
        ui.info("New players: open your inventory and equip a starter weapon and spell first.");
        player = new DungeonPlayer(menu.readName("Give your player a name: ", 20), config, content);
        if (devMode) {
            player.wallet().add(99_999);
            ui.danger("DEVELOPER MODE");
        }
    }

    /** Saves to the current slot (does nothing when playing without saves). */
    void autosave() {
        if (saves == null || slot == 0) {
            return;
        }
        try {
            saves.save(slot, DungeonsSave.summary(player, content), DungeonsSave.write(player));
        } catch (SaveException e) {
            ui.warn("Warning: " + e.getMessage());
        }
    }

    private void lobbyHud() {
        long cleared = content.dungeons().stream().filter(d -> player.hasCleared(d.id())).count();
        String points = player.availablePoints() > 0
                ? Ansi.bold(player.availablePoints() + " unspent point(s)", Ansi.Color.GOLD) : "no unspent points";
        ui.header("Lobby",
                Ansi.bold(player.name()) + "  Lv " + player.level() + "   HP "
                        + ui.bar(player.health().current(), player.health().max(), 14),
                "Gold " + Ansi.color(String.valueOf(player.wallet().gold()), Ansi.Color.GOLD) + "   Dungeons cleared "
                        + cleared + "/" + content.dungeons().size() + "   " + points);
    }

    private void testDamage() {
        List<String> lines = new ArrayList<>();
        lines.add("Melee damage per hit: " + player.attackDamage()
                + (player.weapon() == null ? " (no weapon equipped)" : " with " + player.weapon().name()));
        for (int s = 0; s < DungeonPlayer.SPELL_SLOTS; s++) {
            ItemInstance spell = player.spell(s);
            if (spell != null) {
                String what = spell.healAmount() > 0 ? "heals " + spell.healAmount()
                        : "deals " + player.spellDamage(spell) + " to " + spell.effect().target().name().toLowerCase();
                lines.add("Spell " + (s + 1) + " (" + spell.name() + "): " + what
                        + ", cooldown " + spell.effect().cooldown() + " turn(s)");
            }
        }
        lines.add("Defense: " + player.defense() + "   Max health: " + player.health().max());
        ui.header("Damage Test", lines);
    }

    private void sell() {
        while (true) {
            List<ItemStack> stacks = new ArrayList<>(player.inventory().stacks());
            if (stacks.isEmpty()) {
                ui.warn("No item to sell.");
                return;
            }
            List<String> labels = new ArrayList<>();
            for (ItemStack s : stacks) {
                labels.add(DungeonInventoryScreen.describe(s.item(), s.count()) + "  -> "
                        + s.item().sellValue(config.sellPercent()) + " gold");
            }
            ui.header("Sell Items", "Items sell for " + config.sellPercent() + "% of their value.   Gold: "
                    + Ansi.color(String.valueOf(player.wallet().gold()), Ansi.Color.GOLD));
            int pick = menu.chooseFrom("Which item would you like to sell?", labels, "go back");
            if (pick == Menu.CANCEL) {
                return;
            }
            ItemInstance item = stacks.get(pick - 1).item();
            int value = item.sellValue(config.sellPercent());
            if (menu.confirm("Sell " + item.displayName() + " for " + value + " gold?")) {
                player.inventory().remove(item);
                player.wallet().add(value);
                ui.success("You sold " + item.name() + " for " + value + " gold.");
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
            ui.warn("You need to clear " + content.dungeon(dungeon.unlockRequires()).name() + " first.");
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
            ui.warn("You need to be level " + req + " to play this difficulty.");
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
