package mappa.core.content;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One dungeon from data/dungeons/dungeons/*.json.
 *
 * @param order          sort order in the dungeon list
 * @param unlockRequires id of a dungeon that must be cleared first, or null
 * @param difficulties   difficulty id → settings; only listed difficulties are playable
 * @param waves          groups of mob ids fought in order before the boss
 * @param rewardTags     items with any of these tags can drop
 * @param rewardItems    extra item ids that can drop
 */
public record DungeonDefinition(String id, String name, int order, String unlockRequires,
                                Map<String, Settings> difficulties, int turnLimit,
                                List<List<String>> waves, String boss,
                                Set<String> rewardTags, Set<String> rewardItems, String description) {

    /**
     * @param levelReq       player level needed to enter
     * @param mobLevel       level mobs spawn at
     * @param rewardLevelCap highest item level that can drop
     */
    public record Settings(int levelReq, int mobLevel, int rewardLevelCap) {
    }

    public DungeonDefinition {
        difficulties = Map.copyOf(difficulties);
        waves = waves.stream().map(List::copyOf).toList();
        rewardTags = Set.copyOf(rewardTags);
        rewardItems = Set.copyOf(rewardItems);
        description = description == null ? "" : description;
    }
}
