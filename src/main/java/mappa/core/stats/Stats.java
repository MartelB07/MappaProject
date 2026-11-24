package mappa.core.stats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Base values plus modifiers. Values are always computed, never stored as running
 * totals, so adding and removing a source always returns exactly to the previous value.
 */
public class Stats {
    private final EnumMap<StatType, Integer> base = new EnumMap<>(StatType.class);
    private final List<StatModifier> modifiers = new ArrayList<>();

    public Stats() {
    }

    public Stats(Map<StatType, Integer> baseValues) {
        base.putAll(baseValues);
    }

    public int base(StatType type) {
        return base.getOrDefault(type, 0);
    }

    public void setBase(StatType type, int value) {
        base.put(type, value);
    }

    public void addBase(StatType type, int delta) {
        base.merge(type, delta, Integer::sum);
    }

    public int get(StatType type) {
        int flat = base(type);
        double pct = 0.0;
        for (StatModifier m : modifiers) {
            if (m.stat() == type) {
                flat += m.flat();
                pct += m.percent();
            }
        }
        return (int) Math.round(flat * (1.0 + pct));
    }

    /** Adds modifiers; their sourceId should equal {@code sourceId} so they can be removed together. */
    public void addAll(String sourceId, Collection<StatModifier> mods) {
        for (StatModifier m : mods) {
            if (!m.sourceId().equals(sourceId)) {
                throw new IllegalArgumentException("Modifier source " + m.sourceId() + " != " + sourceId);
            }
        }
        modifiers.addAll(mods);
    }

    public void add(StatModifier mod) {
        modifiers.add(mod);
    }

    /** Removes every modifier from the given source. Returns how many were removed. */
    public int removeSource(String sourceId) {
        int before = modifiers.size();
        modifiers.removeIf(m -> m.sourceId().equals(sourceId));
        return before - modifiers.size();
    }

    public boolean hasSource(String sourceId) {
        for (StatModifier m : modifiers) {
            if (m.sourceId().equals(sourceId)) {
                return true;
            }
        }
        return false;
    }

    public List<StatModifier> modifiers() {
        return Collections.unmodifiableList(modifiers);
    }
}
