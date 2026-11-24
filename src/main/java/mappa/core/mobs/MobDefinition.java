package mappa.core.mobs;

import mappa.core.stats.StatType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable mob template loaded from JSON.
 *
 * @param archetype   free-form flavour/AI group, e.g. "MAGE", "WARRIOR", "BEAST"
 * @param baseLevel   level at which {@code stats} are exactly their base values
 * @param attackEvery turns between attacks in turn-based dungeons (telegraphed)
 */
public record MobDefinition(String id, String name, String archetype, boolean boss, int baseLevel,
                            Map<StatType, StatGrowth> stats, int exp, int goldMin, int goldMax,
                            int attackEvery) {
    public MobDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        archetype = archetype == null ? "NORMAL" : archetype;
        stats = stats == null || stats.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(stats));
        attackEvery = attackEvery <= 0 ? 3 : attackEvery;
    }

    public StatGrowth stat(StatType type) {
        return stats.getOrDefault(type, new StatGrowth(0, 0));
    }
}
