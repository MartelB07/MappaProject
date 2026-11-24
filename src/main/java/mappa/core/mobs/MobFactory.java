package mappa.core.mobs;

import mappa.core.random.Rng;
import mappa.core.stats.StatType;

/** Turns a {@link MobDefinition} into a {@link Mob} at a given level and difficulty. */
public final class MobFactory {
    private MobFactory() {
    }

    /**
     * @param level  the mob's level (shown to the player); stats grow above the base level
     * @param scale  difficulty multiplier for health, damage and EXP (1.0 = normal)
     */
    public static Mob spawn(MobDefinition def, int level, double scale, Rng rng) {
        return spawn(def, def.name(), level, scale, rng);
    }

    public static Mob spawn(MobDefinition def, String displayName, int level, double scale, Rng rng) {
        if (scale <= 0) {
            throw new IllegalArgumentException("scale must be > 0");
        }
        int above = Math.max(0, level - def.baseLevel());
        int health = scaled(def.stat(StatType.MAX_HEALTH).at(above), scale);
        int damage = scaled(def.stat(StatType.DAMAGE).at(above), scale);
        int defense = def.stat(StatType.DEFENSE).at(above);
        int crit = def.stat(StatType.CRIT_CHANCE).at(above);
        int dodge = def.stat(StatType.DODGE_CHANCE).at(above);
        int exp = scaled(def.exp(), scale);
        int gold = def.goldMax() > def.goldMin() ? rng.between(def.goldMin(), def.goldMax()) : def.goldMin();
        return new Mob(def, displayName, level, health, damage, defense, crit, dodge, exp, gold);
    }

    private static int scaled(int value, double scale) {
        return (int) Math.round(value * scale);
    }
}
