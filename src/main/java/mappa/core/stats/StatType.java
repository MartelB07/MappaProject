package mappa.core.stats;

/**
 * Every stat any entity can have. Add a constant here to introduce a new stat
 * (e.g. FIRE_RESIST for a volcano world); JSON content can then use it by name.
 */
public enum StatType {
    /** Maximum hit points. */
    MAX_HEALTH,
    /** Generic damage (Adventure player, all mobs). */
    DAMAGE,
    /** Dungeons: warrior-class damage. */
    WAR_DAMAGE,
    /** Dungeons: mage-class damage. */
    MAGE_DAMAGE,
    /** Flat damage reduction. */
    DEFENSE,
    /** Percent chance (0-100) to land a critical hit. */
    CRIT_CHANCE,
    /** Percent chance (0-100) to dodge an attack. */
    DODGE_CHANCE,
    /** Explore survival stats. */
    HUNGER,
    THIRST,
    STAMINA
}
