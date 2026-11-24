package mappa.explore;

import mappa.core.content.BiomeDefinition;
import mappa.core.content.ContentRegistry;
import mappa.core.content.SpawnEntry;
import mappa.core.inventory.ItemStack;
import mappa.core.io.Ansi;
import mappa.core.io.GameIO;
import mappa.core.io.Menu;
import mappa.core.items.ItemInstance;
import mappa.core.mobs.Mob;
import mappa.core.mobs.MobFactory;
import mappa.core.random.Rng;
import mappa.core.stats.StatType;

import java.util.ArrayList;
import java.util.List;

/**
 * Mappa Explore (early prototype): survive as many days as you can in a world built
 * from data/explore/biomes.json. Every action takes time and drains hunger and thirst.
 */
public class ExploreGame {
    private final ContentRegistry content;
    private final ExploreConfig config;
    private final Rng rng;
    private final GameIO io;
    private final Menu menu;
    private List<BiomeDefinition> world;
    private int position;
    private SurvivalState state;

    public ExploreGame(ContentRegistry content, Rng rng, GameIO io) {
        this.content = content;
        this.config = ExploreConfig.from(content);
        this.rng = rng;
        this.io = io;
        this.menu = new Menu(io);
    }

    public SurvivalState state() {
        return state;
    }

    public void run() {
        io.println();
        io.println(Ansi.color("Mappa Explore is an early prototype.", Ansi.Color.YELLOW));
        world = WorldGenerator.generate(content, config.worldSize(), rng);
        position = world.size() / 2;
        state = new SurvivalState(config);
        while (state.health().isAlive()) {
            BiomeDefinition here = world.get(position);
            io.println();
            io.println("Day " + state.day() + ", " + String.format("%02d:00", state.hour())
                    + (state.isNight() ? Ansi.color(" (night)", Ansi.Color.BLUE) : "") + " - " + here.name());
            io.println("Health: " + Ansi.health(state.health().current(), state.health().max())
                    + "   Hunger: " + state.hunger() + "/" + config.maxHunger()
                    + "   Thirst: " + state.thirst() + "/" + config.maxThirst());
            int c = menu.choose("", "give up", "Gather resources", "Explore the area", "Eat", "Drink",
                    "Travel", "Inventory");
            switch (c) {
                case Menu.CANCEL -> {
                    if (menu.confirm("Give up? Your progress will be lost.")) {
                        io.println("You survived " + state.day() + " day(s).");
                        return;
                    }
                }
                case 1 -> gather(here);
                case 2 -> explore(here);
                case 3 -> consume("explore:food", true);
                case 4 -> consume("explore:drink", false);
                case 5 -> travel();
                case 6 -> showInventory();
                default -> { }
            }
        }
        io.println(Ansi.color("You didn't make it. You survived " + state.day() + " day(s).", Ansi.Color.RED));
    }

    private void passTime(BiomeDefinition biome) {
        state.passTime(config.hoursPerAction(), biome.hungerDrainPerHour(), biome.thirstDrainPerHour());
    }

    private void gather(BiomeDefinition here) {
        if (here.resources().isEmpty()) {
            io.println("There is nothing to gather here.");
            return;
        }
        BiomeDefinition.ResourceYield y = rng.weighted(here.resources(), BiomeDefinition.ResourceYield::weight);
        int amount = rng.between(y.min(), y.max());
        int added = state.inventory().add(ItemInstance.of(content.item(y.itemId())), amount);
        io.println("You gathered " + content.item(y.itemId()).name() + " x" + added + "."
                + (added < amount ? " (inventory full)" : ""));
        passTime(here);
    }

    private void explore(BiomeDefinition here) {
        passTime(here);
        for (BiomeDefinition.Hazard h : here.hazards()) {
            if (rng.chance(h.chance())) {
                io.println(h.description() + " -" + state.health().damage(h.damage()) + " health.");
            }
        }
        List<SpawnEntry> spawns = new ArrayList<>();
        for (SpawnEntry s : here.mobs()) {
            if (!s.nightOnly() || state.isNight()) {
                spawns.add(s);
            }
        }
        if (!spawns.isEmpty() && state.health().isAlive() && rng.chance(0.4)) {
            SpawnEntry s = rng.weighted(spawns, SpawnEntry::weight);
            Mob mob = MobFactory.spawn(content.mob(s.mobId()), rng.between(s.minLevel(), s.maxLevel()), 1.0, rng);
            fight(mob);
        } else if (state.health().isAlive()) {
            io.println("You explore " + here.name() + " but find nothing of note.");
        }
    }

    private void fight(Mob mob) {
        io.println("A " + mob.label() + " appears!");
        while (mob.isAlive() && state.health().isAlive()) {
            io.println(mob.label() + " " + Ansi.health(mob.health(), mob.maxHealth())
                    + "   You " + Ansi.health(state.health().current(), state.health().max()));
            if (menu.choose("", null, "Fight", "Run") == 2) {
                if (rng.chance(0.5)) {
                    io.println("You got away.");
                    return;
                }
                io.println("You couldn't get away!");
            } else {
                io.println(mob.name() + " took " + mob.takeDamage(state.stats().get(StatType.DAMAGE)) + " damage.");
            }
            if (mob.isAlive()) {
                io.println("You took " + state.health().damage(mob.damage()) + " damage.");
            }
        }
        if (!mob.isAlive()) {
            io.println("You defeated the " + mob.name() + ".");
        }
    }

    private void consume(String tag, boolean food) {
        List<ItemStack> options = state.inventory().find(i -> i.definition().hasTag(tag));
        if (options.isEmpty()) {
            io.println("You have nothing to " + (food ? "eat" : "drink") + ". Try gathering.");
            return;
        }
        ItemInstance item = options.get(0).item();
        state.inventory().remove(item);
        if (food || item.definition().hasTag("explore:food")) {
            state.eat(config.foodRestore());
        }
        if (!food || item.definition().hasTag("explore:drink")) {
            state.drink(config.drinkRestore());
        }
        state.health().heal(item.healAmount());
        io.println("You consumed " + item.name() + ".");
    }

    private void travel() {
        List<String> labels = new ArrayList<>();
        List<Integer> targets = new ArrayList<>();
        if (position > 0) {
            labels.add("West to " + world.get(position - 1).name());
            targets.add(position - 1);
        }
        if (position < world.size() - 1) {
            labels.add("East to " + world.get(position + 1).name());
            targets.add(position + 1);
        }
        int pick = menu.chooseFrom("Where to?", labels, "stay");
        if (pick != Menu.CANCEL) {
            passTime(world.get(position));
            position = targets.get(pick - 1);
            io.println("You arrive in " + world.get(position).name() + ".");
        }
    }

    private void showInventory() {
        io.println();
        if (state.inventory().isEmpty()) {
            io.println("Your pack is empty.");
        }
        for (ItemStack s : state.inventory().stacks()) {
            io.println("  " + s.item().name() + " x" + s.count());
        }
    }
}
