package mappa.core.content;

import java.util.List;
import java.util.Set;

/**
 * An open-world biome from data/explore/biomes.json. New open-world characteristics
 * go in {@code traits} (free-form) or as new hazard/resource entries.
 */
public record BiomeDefinition(String id, String name, int dangerMin, int dangerMax, int weight,
                              List<ResourceYield> resources, List<SpawnEntry> mobs, List<Hazard> hazards,
                              int hungerDrainPerHour, int thirstDrainPerHour, Set<String> traits,
                              String description) {

    /** A gatherable resource: item id, relative weight, and yield range per gather. */
    public record ResourceYield(String itemId, int weight, int min, int max) {
    }

    /** A random event while exploring; damage is applied to health. */
    public record Hazard(String id, String description, double chance, int damage) {
    }

    public BiomeDefinition {
        resources = List.copyOf(resources);
        mobs = List.copyOf(mobs);
        hazards = List.copyOf(hazards);
        traits = Set.copyOf(traits);
        description = description == null ? "" : description;
    }
}
