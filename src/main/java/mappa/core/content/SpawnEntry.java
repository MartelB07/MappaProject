package mappa.core.content;

/**
 * A mob that can appear somewhere (region, biome) with a weight and level range.
 *
 * @param nightOnly only spawns at night (Explore)
 */
public record SpawnEntry(String mobId, int weight, int minLevel, int maxLevel, boolean nightOnly) {
}
