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

import java.util.ArrayList;
import java.util.List;

/** Mappa Adventure: travel regions, fight, shop, open chests, beat the final boss. */
public class AdventureGame {
    private final ContentRegistry content;
    private final AdventureConfig config;
    private final Rng rng;
    private final GameIO io;
    private final Menu menu;
    private final boolean devMode;
    private final RegionGraph regions;
    private final Shop shop;
    private final ChestService chests;
    private AdventurePlayer player;
    private RegionDefinition location;
    private boolean won;

    public AdventureGame(ContentRegistry content, Rng rng, GameIO io, boolean devMode) {
        this.content = content;
        this.config = AdventureConfig.from(content);
        this.rng = rng;
        this.io = io;
        this.menu = new Menu(io);
        this.devMode = devMode;
        this.regions = new RegionGraph(content, config);
        this.shop = new Shop(content, config, io);
        this.chests = new ChestService(content, config, rng, io);
    }

    public AdventurePlayer player() {
        return player;
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
        io.println();
        io.println("Running Mappa Adventure...");
        player = new AdventurePlayer(menu.readName("Give your player a name: ", 20), config, content);
        if (devMode) {
            player.makeDeveloper();
            io.println(Ansi.color("DEVELOPER MODE", Ansi.Color.RED));
        }
        location = regions.start();
        io.println();
        io.println("Welcome " + player.name() + ", let's begin your adventure.");
        while (true) {
            io.println();
            io.println("You are in " + location.name() + ".");
            List<String> options = new ArrayList<>(List.of("Open Stats", "Open Inventory", "Shop", "Travel", "Fight"));
            if (location.hasChests()) {
                options.add("Look for Chest");
            }
            int c = menu.chooseFrom("", options, "exit the game");
            boolean alive = switch (c) {
                case Menu.CANCEL -> {
                    if (menu.confirm("Are you sure you want to exit? Your data will be lost.")) {
                        io.println("Returning to home page...");
                        yield false;
                    }
                    yield true;
                }
                case 1 -> {
                    stats();
                    yield true;
                }
                case 2 -> {
                    inventory();
                    yield true;
                }
                case 3 -> {
                    shop.visit(player, menu);
                    yield true;
                }
                case 4 -> {
                    travel();
                    yield true;
                }
                case 5 -> fight(regions.encounter(location, player.level(), rng));
                case 6 -> lookForChest();
                default -> true;
            };
            if (!alive) {
                return;
            }
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
        io.println("You won the fight! You've earned " + mob.exp() + " exp and " + mob.gold() + " gold!");
        player.wallet().add(mob.gold());
        LevelUpResult lu = player.addExp(mob.exp());
        for (int lvl = lu.fromLevel() + 1; lvl <= lu.toLevel(); lvl++) {
            io.println(Ansi.color("You leveled up! You are now level " + lvl + ".", Ansi.Color.YELLOW)
                    + " Health +" + player.healthGainAt(lvl) + ", damage +" + lvl + ", gold +"
                    + (lvl * config.goldPerLevelUp()) + ".");
        }
        if (lu.reachedMax()) {
            io.println("You've reached the max level.");
        }
        chests.onFightWon(player, mob.level());
        if (mob.isBoss() && location.isFinal()) {
            won = true;
            io.println();
            io.println(Ansi.color("*** You defeated the " + mob.name() + " and saved the land! You win! ***",
                    Ansi.Color.GOLD));
            return menu.confirm("Keep playing?");
        }
        return true;
    }

    /** One revive offer; returns true if the player revived. */
    private boolean reviveOrDie() {
        long cost = player.reviveCost();
        if (player.wallet().canAfford(cost) && menu.confirm("You were defeated. Revive for " + cost + " gold?")) {
            player.wallet().trySpend(cost);
            player.health().fill();
            io.println("You revived!");
            return true;
        }
        io.println(Ansi.color("You Died.", Ansi.Color.RED));
        return false;
    }

    private void stats() {
        io.println();
        io.println("Name: " + player.name());
        io.println("Health: " + player.health().current() + "/" + player.health().max());
        io.println("Level: " + player.level());
        io.println("Exp: " + (player.progression().isMaxLevel() ? "MAX"
                : player.progression().exp() + "/" + player.progression().expForNext()));
        for (ItemSlot slot : List.of(ItemSlot.WEAPON, ItemSlot.HELMET, ItemSlot.CHEST, ItemSlot.BOOTS)) {
            ItemInstance i = player.equipment().get(slot);
            io.println(label(slot) + ": " + (i == null ? "-" : i.name()));
        }
        io.println("Estimated Damage: " + player.damage());
        io.println("Gold: " + player.wallet().gold());
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
            io.println();
            io.println("Inventory (" + player.inventory().usedSlots() + "/" + player.inventory().capacity() + " slots):");
            for (ItemStack s : player.inventory().stacks()) {
                String stats = s.item().statSummary();
                io.println("  " + s.item().displayName() + (s.count() > 1 ? " x" + s.count() : "")
                        + (stats.isEmpty() ? "" : "  (" + stats + ")"));
            }
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
            io.println("No item to equip.");
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
            io.println("Sorry, you need to be level " + item.levelReq() + ".");
            return;
        }
        player.inventory().remove(item);
        var result = player.equipment().equip(item, player.level());
        if (result.previous() != null) {
            player.inventory().add(result.previous());
            io.println("You switched out " + result.previous().name() + " for " + item.name()
                    + ". Sell what you don't need for extra gold.");
        } else {
            io.println("You equipped " + item.name() + ".");
        }
    }

    private void drink() {
        List<ItemStack> potions = player.inventory().ofSlot(ItemSlot.CONSUMABLE);
        if (potions.isEmpty()) {
            io.println("You have no potions.");
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
            io.println("You need to be level " + potion.levelReq() + " to use this potion.");
            return;
        }
        int amount = menu.readInt("How many? (0 to cancel): ", 1, 99, true);
        if (amount != Menu.CANCEL) {
            AdventureCombat.drinkPotions(player, potion.definition(), amount, io);
        }
    }

    private void travel() {
        List<RegionDefinition> options = regions.neighbors(location);
        List<String> labels = new ArrayList<>();
        for (RegionDefinition r : options) {
            labels.add(r.name() + " [Level Req: " + r.levelReq() + "]");
        }
        int pick = menu.chooseFrom("Where would you like to travel?", labels, "cancel");
        if (pick == Menu.CANCEL) {
            return;
        }
        RegionDefinition dest = options.get(pick - 1);
        if (dest.levelReq() > player.level()) {
            io.println("You have to be level " + dest.levelReq() + " to travel there.");
            return;
        }
        location = dest;
        io.println("You are now in " + location.name() + ".");
    }

    private boolean lookForChest() {
        ChestService.FoundChest found = chests.search(location);
        if (found == null) {
            io.println("No chest found. Try fighting a bit more and then try again.");
            return true;
        }
        io.println("You found a [Level " + found.level() + "] Chest.");
        ItemStack key = chests.bestKey(player, found.level());
        if (key == null) {
            io.println("You need a key of level " + found.level() + " or higher to open it.");
            return true;
        }
        if (!menu.confirm("Use your " + key.item().name() + " to open it?")) {
            return true;
        }
        if (chests.open(player, found, key) == ChestService.Result.MONSTER) {
            Mob base = regions.encounter(location, player.level(), rng);
            double mult = rng.between(config.chestMonsterGoldMin(), config.chestMonsterGoldMax());
            Mob monster = MobFactory.spawn(base.definition(), "Chest Monster", base.level(), 1.0, rng);
            io.println("It was a trap! A monster jumps out of the chest.");
            boolean alive = fight(monster);
            if (alive && !monster.isAlive()) {
                long bonus = Math.round(monster.gold() * (mult - 1));
                if (bonus > 0) {
                    player.wallet().add(bonus);
                    io.println("The chest monster was guarding " + bonus + " extra gold.");
                }
            }
            return alive;
        }
        return true;
    }
}
