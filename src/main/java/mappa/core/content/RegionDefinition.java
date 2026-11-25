package mappa.core.content;

import java.util.List;
import java.util.Set;

/**
 * An Adventure region from data/adventure/regions.json.
 *
 * @param neighbors     region ids reachable from here (made two-way when loaded)
 * @param chestLevelMin lowest chest level found here
 * @param chestLevelMax highest chest level found here
 * @param isFinal       defeating a boss here wins the game
 * @param description   optional flavor text shown when arriving ("" if none)
 */
public record RegionDefinition(String id, String name, int levelReq, boolean hasChests, int chestLevelMin,
                               int chestLevelMax, List<SpawnEntry> mobs, Set<String> neighbors,
                               boolean isFinal, String description) {
    public RegionDefinition {
        mobs = List.copyOf(mobs);
        neighbors = Set.copyOf(neighbors);
        description = description == null ? "" : description;
    }

    RegionDefinition withNeighbors(Set<String> newNeighbors) {
        return new RegionDefinition(id, name, levelReq, hasChests, chestLevelMin, chestLevelMax, mobs,
                newNeighbors, isFinal, description);
    }
}
