package mappa.core.mobs;

/** A spawned mob with its own health. Created by {@link MobFactory}. */
public class Mob {
    private final MobDefinition definition;
    private final String name;
    private final int level;
    private final int maxHealth;
    private final int damage;
    private final int defense;
    private final int critChance;
    private final int dodgeChance;
    private final int exp;
    private final int gold;
    private int health;

    public Mob(MobDefinition definition, String name, int level, int maxHealth, int damage, int defense,
               int critChance, int dodgeChance, int exp, int gold) {
        this.definition = definition;
        this.name = name;
        this.level = level;
        this.maxHealth = Math.max(1, maxHealth);
        this.damage = Math.max(0, damage);
        this.defense = Math.max(0, defense);
        this.critChance = critChance;
        this.dodgeChance = dodgeChance;
        this.exp = Math.max(0, exp);
        this.gold = Math.max(0, gold);
        this.health = this.maxHealth;
    }

    public MobDefinition definition() {
        return definition;
    }

    public String name() {
        return name;
    }

    public int level() {
        return level;
    }

    public int maxHealth() {
        return maxHealth;
    }

    public int health() {
        return health;
    }

    public int damage() {
        return damage;
    }

    public int defense() {
        return defense;
    }

    public int critChance() {
        return critChance;
    }

    public int dodgeChance() {
        return dodgeChance;
    }

    public int exp() {
        return exp;
    }

    public int gold() {
        return gold;
    }

    public boolean isBoss() {
        return definition.boss();
    }

    public boolean isAlive() {
        return health > 0;
    }

    /** Applies damage after defense; returns damage dealt. */
    public int takeDamage(int amount) {
        int dealt = Math.max(0, Math.min(health, amount - defense));
        if (amount > 0 && dealt == 0 && health > 0) {
            dealt = 1; // every hit does at least 1
        }
        health -= dealt;
        return dealt;
    }

    public String label() {
        return "[Level " + level + "] " + name;
    }
}
