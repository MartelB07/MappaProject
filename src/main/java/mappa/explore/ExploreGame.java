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
import mappa.core.save.SaveException;
import mappa.core.save.SaveMenu;
import mappa.core.save.SaveSettings;
import mappa.core.save.SaveStore;
import mappa.core.stats.StatType;
import mappa.core.ui.Tone;
import mappa.core.ui.Ui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mappa Explore (early prototype): survive as many days as you can in a world built
 * from data/explore/biomes.json. Every action takes time and drains hunger and thirst.
 */
public class ExploreGame {
    /** Accent color for every Explore screen. */
    public static final Ansi.Color ACCENT = Ansi.Color.CYAN;

    private final ContentRegistry content;
    private final ExploreConfig config;
    private final Rng rng;
    private final GameIO io;
    private final Menu menu;
    private final Ui ui;
    private int shownDay;
    private boolean shownNight;
    private final SaveStore saves;
    private final SaveSettings saveSettings;
    private List<BiomeDefinition> world;
    private int position;
    private SurvivalState state;
    private int slot;
    private int lastSavedDay;

    /** A game without saving (used by tests). */
    public ExploreGame(ContentRegistry content, Rng rng, GameIO io) {
        this(content, rng, io, null);
    }

    /** @param savesRoot folder that holds all save files, or null to play without saving */
    public ExploreGame(ContentRegistry content, Rng rng, GameIO io, Path savesRoot) {
        this.content = content;
        this.config = ExploreConfig.from(content);
        this.rng = rng;
        this.io = io;
        this.ui = new Ui(io, ACCENT);
        this.menu = new Menu(ui);
        this.saveSettings = SaveSettings.from(content.config("explore"));
        this.saves = savesRoot == null ? null
                : new SaveStore(savesRoot, ExploreSave.MODE, saveSettings.slots(), ExploreSave.FORMAT);
    }

    public SurvivalState state() {
        return state;
    }

    public int position() {
        return position;
    }

    public List<BiomeDefinition> world() {
        return List.copyOf(world);
    }

    /** The slot this game saves to (0 = not saving). */
    public int slot() {
        return slot;
    }

    public void run() {
        ui.banner(Tone.INFO, "Mappa Explore", "Survive as many days as you can.",
                Ansi.color("Early prototype.", Ansi.Color.YELLOW));
        if (!startOrLoad()) {
            return;
        }
        shownDay = state.day();
        shownNight = state.isNight();
        arrivalBanner(world.get(position));
        while (state.health().isAlive()) {
            BiomeDefinition here = world.get(position);
            timeEvents();
            hud(here);
            List<String> options = new ArrayList<>(List.of("Gather resources", "Explore the area", "Eat", "Drink",
                    "Travel", "Inventory"));
            if (saves != null) {
                options.add("Save Game");
            }
            int c = menu.chooseFrom("What will you do?", options, saves != null ? "save and exit" : "give up");
            switch (c) {
                case Menu.CANCEL -> {
                    if (saves != null) {
                        autosave();
                        ui.success("Game saved to slot " + slot + ". Returning to home page...");
                        return;
                    }
                    if (menu.confirm("Give up? Your progress will be lost.")) {
                        ui.muted("You survived " + state.day() + " day(s).");
                        return;
                    }
                }
                case 1 -> gather(here);
                case 2 -> explore(here);
                case 3 -> consume("explore:food", true);
                case 4 -> consume("explore:drink", false);
                case 5 -> travel();
                case 6 -> showInventory();
                case 7 -> {
                    autosave();
                    ui.success("Game saved to slot " + slot + ".");
                }
                default -> { }
            }
            if (state.health().isAlive() && state.day() != lastSavedDay) {
                autosave(); // once at the start of each new day
            }
        }
        ui.banner(Tone.DANGER, "You didn't make it.", "You survived " + state.day() + " day(s).");
        if (saves != null && slot != 0) {
            if (saveSettings.deleteOnDeath()) {
                saves.delete(slot);
                ui.muted("Your save in slot " + slot + " has been deleted.");
            } else {
                ui.muted("Your last save in slot " + slot + " is still there.");
            }
        }
    }

    /** Shows the save screen (if saving) and sets up the world. Returns false if the player backed out. */
    private boolean startOrLoad() {
        if (saves == null) {
            newWorld();
            return true;
        }
        while (true) {
            SaveMenu.Choice choice = new SaveMenu(saves, menu).start("Mappa Explore");
            switch (choice.kind()) {
                case BACK -> {
                    return false;
                }
                case NEW_GAME -> {
                    slot = choice.slot();
                    newWorld();
                    autosave();
                    return true;
                }
                case LOAD -> {
                    try {
                        List<String> warnings = new ArrayList<>();
                        ExploreSave.Loaded loaded = ExploreSave.read(saves.load(choice.slot()), content, config,
                                warnings);
                        state = loaded.state();
                        world = new ArrayList<>(loaded.world());
                        position = loaded.position();
                        slot = choice.slot();
                        lastSavedDay = state.day();
                        SaveMenu.showWarnings(ui, warnings);
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

    private void newWorld() {
        world = WorldGenerator.generate(content, config.worldSize(), rng);
        position = world.size() / 2;
        state = new SurvivalState(config);
    }

    /** Saves to the current slot (does nothing when playing without saves). */
    void autosave() {
        if (saves == null || slot == 0 || !state.health().isAlive()) {
            return;
        }
        try {
            saves.save(slot, ExploreSave.summary(state, world.get(position)), ExploreSave.write(state, world, position));
            lastSavedDay = state.day();
        } catch (SaveException e) {
            ui.warn("Warning: " + e.getMessage());
        }
    }

    /** Status strip: time, place, and the three survival bars. */
    private void hud(BiomeDefinition here) {
        String time = String.format("%02d:00", state.hour());
        String clock = state.isNight() ? Ansi.color(ui.glyphs().moon() + " " + time + " night", Ansi.Color.BLUE)
                : Ansi.color(ui.glyphs().sun() + " " + time, Ansi.Color.GOLD);
        ui.header("Day " + state.day() + "  -  " + here.name(),
                clock,
                "Health " + ui.bar(state.health().current(), state.health().max(), 12),
                "Hunger " + ui.bar(state.hunger(), config.maxHunger(), 12),
                "Thirst " + ui.bar(state.thirst(), config.maxThirst(), 12));
    }

    /** Big notices at sunrise of a new day and at nightfall, and warnings when needs run low. */
    private void timeEvents() {
        if (state.day() > shownDay && !state.isNight()) {
            shownDay = state.day();
            ui.banner(Tone.REWARD, ui.glyphs().sun(), "Day " + state.day(),
                    List.of("The sun rises. You survived another night."));
        }
        if (state.isNight() && !shownNight) {
            ui.banner(Tone.MAGIC, ui.glyphs().moon(), "Night falls",
                    List.of("Dangerous creatures come out after dark."));
        }
        shownNight = state.isNight();
        if (state.hunger() <= config.maxHunger() / 4) {
            ui.warn(state.hunger() == 0 ? "You are starving! Eat something now." : "You are getting hungry.");
        }
        if (state.thirst() <= config.maxThirst() / 4) {
            ui.warn(state.thirst() == 0 ? "You are dying of thirst! Drink something now." : "You are getting thirsty.");
        }
    }

    private void arrivalBanner(BiomeDefinition biome) {
        java.util.List<String> lines = new ArrayList<>();
        if (!biome.description().isEmpty()) {
            lines.add(biome.description());
        }
        ui.banner(Tone.INFO, "You arrive in " + biome.name(), lines);
    }

    private void passTime(BiomeDefinition biome) {
        state.passTime(config.hoursPerAction(), biome.hungerDrainPerHour(), biome.thirstDrainPerHour());
    }

    private void gather(BiomeDefinition here) {
        if (here.resources().isEmpty()) {
            ui.warn("There is nothing to gather here.");
            return;
        }
        BiomeDefinition.ResourceYield y = rng.weighted(here.resources(), BiomeDefinition.ResourceYield::weight);
        int amount = rng.between(y.min(), y.max());
        int added = state.inventory().add(ItemInstance.of(content.item(y.itemId())), amount);
        ui.success("You gathered " + content.item(y.itemId()).name() + " x" + added + "."
                + (added < amount ? " (inventory full)" : ""));
        passTime(here);
    }

    private void explore(BiomeDefinition here) {
        passTime(here);
        for (BiomeDefinition.Hazard h : here.hazards()) {
            if (rng.chance(h.chance())) {
                ui.danger(h.description() + " -" + state.health().damage(h.damage()) + " health.");
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
            ui.muted("You explore " + here.name() + " but find nothing of note.");
        }
    }

    private void fight(Mob mob) {
        ui.banner(Tone.WARNING, "A " + mob.name() + " appears!", "Level " + mob.level() + "   Health " + mob.maxHealth());
        while (mob.isAlive() && state.health().isAlive()) {
            ui.header(ui.glyphs().swords() + " Battle", mob.label() + "  " + ui.bar(mob.health(), mob.maxHealth(), 12),
                    "You  " + ui.bar(state.health().current(), state.health().max(), 12));
            if (menu.choose("Your move:", null, "Fight", "Run") == 2) {
                if (rng.chance(0.5)) {
                    ui.success("You got away.");
                    return;
                }
                ui.warn("You couldn't get away!");
            } else {
                ui.success(mob.name() + " took " + mob.takeDamage(state.stats().get(StatType.DAMAGE)) + " damage.");
            }
            if (mob.isAlive()) {
                ui.danger("You took " + state.health().damage(mob.damage()) + " damage.");
            }
        }
        if (!mob.isAlive()) {
            ui.reward("You defeated the " + mob.name() + ".");
        }
    }

    private void consume(String tag, boolean food) {
        List<ItemStack> options = state.inventory().find(i -> i.definition().hasTag(tag));
        if (options.isEmpty()) {
            ui.warn("You have nothing to " + (food ? "eat" : "drink") + ". Try gathering.");
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
        ui.success("You consumed " + item.name() + ".");
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
            arrivalBanner(world.get(position));
        }
    }

    private void showInventory() {
        List<String> lines = new ArrayList<>();
        for (ItemStack s : state.inventory().stacks()) {
            lines.add(s.item().name() + " x" + s.count());
        }
        if (lines.isEmpty()) {
            lines.add("Your pack is empty.");
        }
        ui.header("Pack  " + state.inventory().usedSlots() + "/" + state.inventory().capacity() + " slots", lines);
    }
}
