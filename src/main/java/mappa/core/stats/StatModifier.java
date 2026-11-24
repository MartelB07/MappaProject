package mappa.core.stats;

import java.util.Objects;

/**
 * A change to one stat from one source (an item instance, attribute points, a buff...).
 * Final value = (base + sum of flat) * (1 + sum of percent).
 *
 * @param percent fraction, e.g. 0.10 for +10%
 */
public record StatModifier(StatType stat, int flat, double percent, String sourceId) {
    public StatModifier {
        Objects.requireNonNull(stat, "stat");
        Objects.requireNonNull(sourceId, "sourceId");
    }

    public static StatModifier flat(StatType stat, int amount, String sourceId) {
        return new StatModifier(stat, amount, 0.0, sourceId);
    }

    public static StatModifier percent(StatType stat, double percent, String sourceId) {
        return new StatModifier(stat, 0, percent, sourceId);
    }
}
