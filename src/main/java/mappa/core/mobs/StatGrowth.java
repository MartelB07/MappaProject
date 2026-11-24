package mappa.core.mobs;

/**
 * A mob stat that grows with level: base * (1 + perLevel * (level - baseLevel)).
 *
 * @param perLevel fractional growth per level above the mob's base level, e.g. 0.10
 */
public record StatGrowth(int base, double perLevel) {
    public int at(int levelsAboveBase) {
        double factor = 1.0 + perLevel * Math.max(0, levelsAboveBase);
        return (int) Math.round(base * factor);
    }
}
