package mappa.core.items;

import mappa.core.stats.StatType;

/** Character class an item or mob belongs to. */
public enum ClassTag {
    WAR(StatType.WAR_DAMAGE),
    MAGE(StatType.MAGE_DAMAGE),
    GUARD(null),
    ANY(null);

    private final StatType damageStat;

    ClassTag(StatType damageStat) {
        this.damageStat = damageStat;
    }

    /** The damage stat this class scales with, or null if it has none. */
    public StatType damageStat() {
        return damageStat;
    }
}
