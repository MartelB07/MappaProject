package mappa.adventure;

import mappa.core.content.ContentRegistry;
import mappa.core.inventory.Equipment;
import mappa.core.inventory.Inventory;
import mappa.core.inventory.Wallet;
import mappa.core.items.ItemInstance;
import mappa.core.progression.LevelUpResult;
import mappa.core.progression.Progression;
import mappa.core.stats.Health;
import mappa.core.stats.StatType;
import mappa.core.stats.Stats;

import java.util.Map;

/** A Mappa Adventure character. Stats come from level (base) plus equipped gear. */
public class AdventurePlayer {
    private final String name;
    private final AdventureConfig config;
    private final Stats stats = new Stats();
    private final Health health;
    private final Progression progression;
    private final Equipment equipment;
    private final Inventory inventory;
    private final Wallet wallet;

    /** A new character with the configured starting gear and items. */
    public AdventurePlayer(String name, AdventureConfig config, ContentRegistry content) {
        this(name, config, content, true);
    }

    private AdventurePlayer(String name, AdventureConfig config, ContentRegistry content, boolean starterKit) {
        this.name = name;
        this.config = config;
        this.progression = new Progression(content.levelCurve(config.levelCurve()));
        applyLevelStats();
        this.health = new Health(stats);
        this.equipment = new Equipment(stats, health);
        this.inventory = new Inventory(config.inventorySize());
        this.wallet = new Wallet(starterKit ? config.startingGold() : 0);
        if (starterKit) {
            for (String id : config.startingEquipment()) {
                equipment.equip(ItemInstance.of(content.item(id)), 0);
            }
            for (Map.Entry<String, Integer> e : config.startingItems().entrySet()) {
                inventory.add(ItemInstance.of(content.item(e.getKey())), e.getValue());
            }
        }
        health.fill();
    }

    /** An empty character (no gear, no items, no gold) for loading a save into. */
    static AdventurePlayer blank(String name, AdventureConfig config, ContentRegistry content) {
        return new AdventurePlayer(name, config, content, false);
    }

    /** Sets level and EXP from a save; base stats follow the level. */
    void restoreProgress(int level, int exp) {
        progression.restore(level, exp);
        applyLevelStats();
        health.fill();
    }

    /** Developer start: max level and lots of gold (only via the --dev launch flag). */
    public void makeDeveloper() {
        progression.restore(progression.curve().maxLevel(), 0);
        applyLevelStats();
        health.fill();
        wallet.add(99_999);
    }

    public String name() {
        return name;
    }

    public Stats stats() {
        return stats;
    }

    public Health health() {
        return health;
    }

    public Progression progression() {
        return progression;
    }

    public int level() {
        return progression.level();
    }

    public Equipment equipment() {
        return equipment;
    }

    public Inventory inventory() {
        return inventory;
    }

    public Wallet wallet() {
        return wallet;
    }

    public int damage() {
        return stats.get(StatType.DAMAGE);
    }

    public int defense() {
        return stats.get(StatType.DEFENSE);
    }

    /**
     * Adds EXP. Each level gained raises base health to the level's table value, adds the
     * level number to base damage, gives gold, and fully heals.
     */
    public LevelUpResult addExp(int amount) {
        LevelUpResult r = progression.addExp(amount);
        if (r.leveledUp()) {
            for (int lvl = r.fromLevel() + 1; lvl <= r.toLevel(); lvl++) {
                wallet.add((long) lvl * config.goldPerLevelUp());
            }
            applyLevelStats();
            health.fill();
        }
        return r;
    }

    /** Health added by reaching {@code level} from the level before it. */
    public int healthGainAt(int level) {
        int[] t = config.healthByLevel();
        return level <= 0 ? 0 : t[level] - t[level - 1];
    }

    private void applyLevelStats() {
        int level = progression.level();
        stats.setBase(StatType.MAX_HEALTH, config.healthByLevel()[level]);
        stats.setBase(StatType.DAMAGE, level * (level + 1) / 2); // +1, +2, ... +level
    }

    public long reviveCost() {
        return (long) config.reviveCostPerLevel() * Math.max(1, level());
    }
}
