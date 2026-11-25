package mappa.adventure;

import mappa.core.content.ContentRegistry;
import mappa.core.content.RegionDefinition;
import mappa.core.inventory.ItemStack;
import mappa.core.io.Ansi;
import mappa.core.io.GameIO;
import mappa.core.io.Menu;
import mappa.core.items.ItemInstance;
import mappa.core.items.ItemSlot;
import mappa.core.mobs.Mob;
import mappa.core.mobs.MobFactory;
import mappa.core.progression.LevelUpResult;
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

/** Mappa Adventure: travel regions, fight, shop, open chests, beat the final boss. */
public class AdventureGame {
    /** Accent color for every Adventure screen. */
    public static final Ansi.Color ACCENT = Ansi.Color.GREEN;

    private final ContentRegistry content;
    private final AdventureConfig config;
    private final Rng rng;
    private final GameIO io;
    private final Ui ui;
    private final Menu menu;
    private final boolean devMode;
    private final RegionGraph regions;
    private final Shop shop;
    private final ChestService chests;
    private final SaveStore saves;
    private final SaveSettings saveSettings;
    private AdventurePlayer player;
    private RegionDefinition location;
    private boolean won;
    private boolean died;
    private int slot;

    /** A game without saving (used by tests). */
    public AdventureGame(ContentRegistry content, Rng rng, GameIO io, boolean devMode) {
        this(content, rng, io, devMode, null);
    }

    /** @param savesRoot folder that holds all save files, or null to play without saving */
    public AdventureGame(ContentRegistry content, Rng rng, GameIO io, boolean devMode, Path savesRoot) {
        this.content = content;
        this.config = AdventureConfig.from(content);
        this.rng = rng;
        this.io = io;
        this.ui = new Ui(io, ACCENT);
        this.menu = new Menu(ui);
        this.devMode = devMode;
        this.regions = new RegionGraph(content, config);
        this.shop = new Shop(content, config, io);
        this.chests = new ChestService(content, config, rng, io);
        this.saveSettings = SaveSettings.from(content.config("adventure"));
        this.saves = savesRoot == null ? null
                : new SaveStore(savesRoot, AdventureSave.MODE, saveSettings.slots(), AdventureSave.FORMAT);
    }

    public AdventurePlayer player() {
        return player;
    }

    /** The slot this game saves to (0 = not saving). */
    public int slot() {
        return slot;
    }

    RegionGraph regions() {
        return regions;
    }

    ChestService chests() {
        return chests;
    }

    /** Used when loading a save. */
    void restore(AdventurePlayer player, RegionDefinition location, boolean won) {
        this.player = player;
        this.location = location;
        this.won = won;
    }

    /** Test hook: start from a prepared player in a given region instead of calling {@link #run()}. */
    AdventureGame withPlayer(AdventurePlayer player, String regionId) {
        this.player = player;
        this.location = regions.region(regionId);
        return this;
    }

    AdventureConfig config() {
        return config;
    }

    public RegionDefinition location() {
        return location;
    }

    public boolean hasWon() {
        return won;
    }

    public void run() {
        if (!startOrLoad()) {
            return;
        }
        while (true) {
            hud();
            List<String> options = new ArrayList<>(List.of("Open Stats", "Open Inventory", "Shop", "Travel", "Fight"));
            if (location.hasChests()) {
                options.add("Look for Chest");
            }
            if (saves != null) {
                options.add("Save Game");
            }
            int c = menu.chooseFrom("What would you like to do?", options,
                    saves != null ? "save and exit" : "exit the game");
            String action = c == Menu.CANCEL ? "Exit" : options.get(c - 1);
            boolean keepPlaying = switch (action) {
                case "Exit" -> {
                    if (saves != null) {
                        autosave();
                        ui.success("Game saved to slot " + slot + ". Returning to home page...");
                        yield false;
                    }
                    if (menu.confirm("Are you sure you want to exit? Your data will be lost.")) {
                        ui.muted("Returning to home page...");
                        yield false;
                    }
                    yield true;
                }
                case "Open Stats" -> {
                    stats();
                    yield true;
                }
                case "Open Inventory" -> {
                    inventory();
                    yield true;
                }
                case "Shop" -> {
                    shop.visit(player, menu);
                    yield true;
                }
                case "Travel" -> {
                    travel();
                    yield true;
                }
                case "Fight" -> fight(regions.encounter(location, player.level(), rng));
                case "Look for Chest" -> lookForChest();
                case "Save Game" -> {
                    autosave();
                    ui.success("Game saved to slot " + slot + ".");
                    yield true;
                }
                default -> true;
            };
            if (died) {
                onPermanentDeath();
                return;
            }
            if (!keepPlaying) {
                autosave(); // e.g. stopping after beating the boss
                return;
            }
            if (!"Open Stats".equals(action) && !"Save Game".equals(action)) {
                autosave();
            }
        }
    }

    /** Status strip above the main menu: where you are and how you're doing. */
    private void hud() {
        String exp = player.progression().isMaxLevel() ? "MAX"
                : ui.bar(player.progression().exp(), player.progression().expForNext(), 10, Ansi.Color.CYAN)
                + " " + player.progression().exp() + "/" + player.progression().expForNext();
        ui.header(location.name(),
                Ansi.bold(player.name()) + "  Lv " + player.level() + "   HP "
                        + ui.bar(player.health().current(), player.health().max(), 14),
                "Gold " + Ansi.color(String.valueOf(player.wallet().gold()), Ansi.Color.GOLD) + "   EXP " + exp
                        + (won ? "   " + Ansi.color("Boss beaten", Ansi.Color.GOLD) : ""));
    }

    /** Shows the save screen (if saving) and sets up the player. Returns false if the player backed out. */
    private boolean startOrLoad() {
        ui.banner(Tone.SUCCESS, "Mappa Adventure", "Travel, fight, loot chests, and defeat the Boss.");
        if (saves == null) {
            newCharacter();
            return true;
        }
        while (true) {
            SaveMenu.Choice choice = new SaveMenu(saves, menu).start("Mappa Adventure");
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
                        List<String> warnings = AdventureSave.read(saves.load(choice.slot()), this, content, config);
                        slot = choice.slot();
                        SaveMenu.showWarnings(ui, warnings);
                        ui.banner(Tone.SUCCESS, "Welcome back " + player.name() + "!",
                                "You are in " + location.name() + ". Loaded from slot " + slot + ".");
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
        player = new AdventurePlayer(menu.readName("Give your player a name:", 20), config, content);
        if (devMode) {
            player.makeDeveloper();
            ui.danger("DEVELOPER MODE");
        }
        location = regions.start();
        won = false;
        List<String> lines = new ArrayList<>();
        if (!location.description().isEmpty()) {
            lines.add(location.description());
        }
        lines.add("Your adventure begins in " + location.name() + ".");
        ui.banner(Tone.SUCCESS, "Welcome " + player.name() + "!", lines);
    }

    /** Saves to the current slot (does nothing when playing without saves). */
    void autosave() {
        if (saves == null || slot == 0 || died) {
            return;
        }
        try {
            saves.save(slot, AdventureSave.summary(this), AdventureSave.write(this));
        } catch (SaveException e) {
            ui.warn("Warning: " + e.getMessage());
        }
    }

    private void onPermanentDeath() {
        if (saves == null || slot == 0) {
            return;
        }
        if (saveSettings.deleteOnDeath()) {
            saves.delete(slot);
            ui.muted("Your save in slot " + slot + " has been deleted.");
        } else {
            ui.muted("Your last save in slot " + slot + " is still there. Continue from it to try again.");
        }
    }

    /** Runs a fight and its aftermath. Returns false if the game is over (died or quit after winning). */
    boolean fight(Mob mob) {
        AdventureCombat.Outcome outcome = new AdventureCombat(player, mob, config, content, rng, io).fight(menu);
        switch (outcome) {
            case WON -> {
                return onWin(mob);
            }
            case DIED -> {
                return reviveOrDie();
            }
            default -> {
                return true;
            }
        }
    }

    private boolean onWin(Mob mob) {
        ui.success("You won the fight against " + mob.name() + "!  +" + mob.exp() + " EXP  +" + mob.gold() + " gold");
        player.wallet().add(mob.gold());
        LevelUpResult lu = player.addExp(mob.exp());
        for (int lvl = lu.fromLevel() + 1; lvl <= lu.toLevel(); lvl++) {
            ui.banner(Tone.REWARD, "LEVEL UP!  You are now level " + lvl,
                    "Health +" + player.healthGainAt(lvl) + "   Damage +" + lvl + "   Gold +"
                            + (lvl * config.goldPerLevelUp()));
        }
        if (lu.reachedMax()) {
            ui.reward("You've reached the max level. The Boss awaits.");
        }
        chests.onFightWon(player, mob.level());
        if (mob.isBoss() && location.isFinal()) {
            won = true;
            ui.banner(Tone.REWARD, "VICTORY!  You win!",
                    "You defeated the " + mob.name() + " and saved the land.",
                    "Level " + player.level() + "   Gold " + player.wallet().gold());
            return menu.confirm("Keep playing?");
        }
        return true;
    }

    /** One revive offer; returns true if the player revived. */
    private boolean reviveOrDie() {
        long cost = player.reviveCost();
        if (player.wallet().canAfford(cost)) {
            ui.banner(Tone.DANGER, "You were defeated...", "You can pay " + cost + " gold to get back up.");
            if (menu.confirm("Revive for " + cost + " gold?")) {
                player.wallet().trySpend(cost);
                player.health().fill();
                ui.banner(Tone.SUCCESS, "You revived!", "Health restored. Gold left: " + player.wallet().gold());
                return true;
            }
        }
        ui.banner(Tone.DANGER, "You Died.", player.name() + " fell at level " + player.level() + " in "
                + location.name() + ".");
        died = true;
        return false;
    }

    private void stats() {
        List<String> lines = new ArrayList<>();
        lines.add("Health: " + ui.bar(player.health().current(), player.health().max(), 14));
        lines.add("Level:  " + player.level() + "   Exp: " + (player.progression().isMaxLevel() ? "MAX"
                : player.progression().exp() + "/" + player.progression().expForNext()));
        for (ItemSlot slot : List.of(ItemSlot.WEAPON, ItemSlot.HELMET, ItemSlot.CHEST, ItemSlot.BOOTS)) {
            ItemInstance i = player.equipment().get(slot);
            lines.add(label(slot) + ": " + (i == null ? "-" : i.name()));
        }
        lines.add("Estimated Damage: " + player.damage());
        lines.add("Gold: " + player.wallet().gold());
        ui.header(player.name() + "'s Stats", lines);
        menu.pause();
    }

    private static String label(ItemSlot slot) {
        return switch (slot) {
            case WEAPON -> "Weapon";
            case HELMET -> "Helmet";
            case CHEST -> "Chestplate";
            case BOOTS -> "Boots";
            default -> slot.name();
        };
    }

    private void inventory() {
        while (true) {
            List<String> lines = new ArrayList<>();
            for (ItemStack s : player.inventory().stacks()) {
                String stats = s.item().statSummary();
                lines.add(s.item().displayName() + (s.count() > 1 ? " x" + s.count() : "")
                        + (stats.isEmpty() ? "" : Ansi.color("  (" + stats + ")", Ansi.Color.GRAY)));
            }
            if (lines.isEmpty()) {
                lines.add("(empty)");
            }
            ui.header("Inventory  " + player.inventory().usedSlots() + "/" + player.inventory().capacity()
                    + " slots", lines);
            int c = menu.choose("", "go back", "Equip an item", "Drink potions");
            if (c == Menu.CANCEL) {
                return;
            }
            if (c == 1) {
                equip();
            } else {
                drink();
            }
        }
    }

    private void equip() {
        List<ItemStack> gear = player.inventory().find(i -> i.slot().isGear());
        if (gear.isEmpty()) {
            ui.warn("No item to equip.");
            return;
        }
        List<String> labels = new ArrayList<>();
        for (ItemStack s : gear) {
            labels.add(s.item().displayName() + "  (" + s.item().statSummary() + ")");
        }
        int pick = menu.chooseFrom("Which item would you like to equip?", labels, "cancel");
        if (pick == Menu.CANCEL) {
            return;
        }
        ItemInstance item = gear.get(pick - 1).item();
        if (item.levelReq() > player.level()) {
            ui.warn("Sorry, you need to be level " + item.levelReq() + " to equip " + item.name() + ".");
            return;
        }
        player.inventory().remove(item);
        var result = player.equipment().equip(item, player.level());
        if (result.previous() != null) {
            player.inventory().add(result.previous());
            ui.success("You switched out " + result.previous().name() + " for " + item.name()
                    + ". Sell what you don't need for extra gold.");
        } else {
            ui.success("You equipped " + item.name() + ".");
        }
    }

    private void drink() {
        List<ItemStack> potions = player.inventory().ofSlot(ItemSlot.CONSUMABLE);
        if (potions.isEmpty()) {
            ui.warn("You have no potions.");
            return;
        }
        List<String> labels = new ArrayList<>();
        for (ItemStack s : potions) {
            labels.add(s.item().name() + " x" + s.count() + "  (+" + s.item().healAmount() + " health each)");
        }
        int pick = menu.chooseFrom("Which potion?", labels, "cancel");
        if (pick == Menu.CANCEL) {
            return;
        }
        ItemInstance potion = potions.get(pick - 1).item();
        if (potion.levelReq() > player.level()) {
            ui.warn("You need to be level " + potion.levelReq() + " to use this potion.");
            return;
        }
        int amount = menu.readInt("How many? (0 to cancel)", 1, 99, true);
        if (amount != Menu.CANCEL) {
            AdventureCombat.drinkPotions(player, potion.definition(), amount, io);
        }
    }

    private void travel() {
        List<RegionDefinition> options = regions.neighbors(location);
        List<String> labels = new ArrayList<>();
        for (RegionDefinition r : options) {
            String req = "[Level Req: " + r.levelReq() + "]";
            labels.add(r.name() + " " + (r.levelReq() > player.level() ? Ansi.color(req, Ansi.Color.RED)
                    : Ansi.color(req, Ansi.Color.GRAY)));
        }
        int pick = menu.chooseFrom("Where would you like to travel?", labels, "cancel");
        if (pick == Menu.CANCEL) {
            return;
        }
        RegionDefinition dest = options.get(pick - 1);
        if (dest.levelReq() > player.level()) {
            ui.warn("You have to be level " + dest.levelReq() + " to travel to " + dest.name() + ".");
            return;
        }
        location = dest;
        List<String> lines = new ArrayList<>();
        if (!dest.description().isEmpty()) {
            lines.add(dest.description());
        }
        if (dest.hasChests()) {
            lines.add(Ansi.color("Chests can be found here.", Ansi.Color.GOLD));
        }
        if (dest.isFinal()) {
            lines.add(Ansi.color("The Boss lives here.", Ansi.Color.RED));
        }
        ui.banner(dest.isFinal() ? Tone.DANGER : Tone.INFO, "You arrived in " + dest.name(), lines);
    }

    private boolean lookForChest() {
        ChestService.FoundChest found = chests.search(location);
        if (found == null) {
            ui.muted("No chest found. Try fighting a bit more and then try again.");
            return true;
        }
        ui.reward("You found a [Level " + found.level() + "] Chest!");
        ItemStack key = chests.bestKey(player, found.level());
        if (key == null) {
            ui.warn("You need a key of level " + found.level() + " or higher to open it.");
            return true;
        }
        if (!menu.confirm("Use your " + key.item().name() + " to open it?")) {
            return true;
        }
        if (chests.open(player, found, key) == ChestService.Result.MONSTER) {
            Mob base = regions.encounter(location, player.level(), rng);
            double mult = rng.between(config.chestMonsterGoldMin(), config.chestMonsterGoldMax());
            Mob monster = MobFactory.spawn(base.definition(), "Chest Monster", base.level(), 1.0, rng);
            ui.banner(Tone.DANGER, "It was a trap!", "A monster jumps out of the chest.");
            boolean alive = fight(monster);
            if (alive && !monster.isAlive()) {
                long bonus = Math.round(monster.gold() * (mult - 1));
                if (bonus > 0) {
                    player.wallet().add(bonus);
                    ui.reward("The chest monster was guarding " + bonus + " extra gold.");
                }
            }
            return alive;
        }
        return true;
    }
}
