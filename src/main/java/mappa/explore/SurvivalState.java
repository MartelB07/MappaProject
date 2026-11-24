package mappa.explore;

import mappa.core.inventory.Inventory;
import mappa.core.stats.Health;
import mappa.core.stats.StatType;
import mappa.core.stats.Stats;

/** Hunger, thirst, health and the clock for one Explore survivor. */
public class SurvivalState {
    private final ExploreConfig config;
    private final Stats stats = new Stats();
    private final Health health;
    private final Inventory inventory;
    private int hunger;
    private int thirst;
    private int hour = 8;
    private int day = 1;

    public SurvivalState(ExploreConfig config) {
        this.config = config;
        stats.setBase(StatType.MAX_HEALTH, config.startingHealth());
        stats.setBase(StatType.DAMAGE, 3);
        this.health = new Health(stats);
        this.inventory = new Inventory(config.inventorySize());
        this.hunger = config.maxHunger();
        this.thirst = config.maxThirst();
    }

    /**
     * Advances time, draining hunger/thirst at the biome's rates. Each hour spent at 0
     * hunger or 0 thirst costs health.
     */
    public void passTime(int hours, int hungerPerHour, int thirstPerHour) {
        for (int h = 0; h < hours && health.isAlive(); h++) {
            hunger = Math.max(0, hunger - hungerPerHour);
            thirst = Math.max(0, thirst - thirstPerHour);
            if (hunger == 0 || thirst == 0) {
                health.damage(config.starvationDamagePerHour());
            }
            hour++;
            if (hour >= config.hoursPerDay()) {
                hour = 0;
                day++;
            }
        }
    }

    public boolean isNight() {
        return hour >= config.nightStartsAt() || hour < config.nightEndsAt();
    }

    public void eat(int amount) {
        hunger = Math.min(config.maxHunger(), hunger + amount);
    }

    public void drink(int amount) {
        thirst = Math.min(config.maxThirst(), thirst + amount);
    }

    public Stats stats() {
        return stats;
    }

    public Health health() {
        return health;
    }

    public Inventory inventory() {
        return inventory;
    }

    public int hunger() {
        return hunger;
    }

    public int thirst() {
        return thirst;
    }

    public int hour() {
        return hour;
    }

    public int day() {
        return day;
    }
}
