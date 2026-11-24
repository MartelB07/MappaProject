package mappa.explore;

import mappa.core.content.BiomeDefinition;
import mappa.core.content.ContentRegistry;
import mappa.core.random.Rng;

import java.util.ArrayList;
import java.util.List;

/** Builds a world as a path of biome tiles, weighted by each biome's "weight". */
public final class WorldGenerator {
    private WorldGenerator() {
    }

    public static List<BiomeDefinition> generate(ContentRegistry content, int size, Rng rng) {
        List<BiomeDefinition> biomes = new ArrayList<>(content.biomes());
        if (biomes.isEmpty()) {
            throw new IllegalStateException("No biomes defined in data/explore/biomes.json");
        }
        List<BiomeDefinition> world = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            world.add(rng.weighted(biomes, BiomeDefinition::weight));
        }
        return world;
    }
}
