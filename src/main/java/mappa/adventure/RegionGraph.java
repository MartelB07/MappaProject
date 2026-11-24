package mappa.adventure;

import mappa.core.content.ContentRegistry;
import mappa.core.content.RegionDefinition;
import mappa.core.content.SpawnEntry;
import mappa.core.mobs.Mob;
import mappa.core.mobs.MobDefinition;
import mappa.core.mobs.MobFactory;
import mappa.core.random.Rng;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Travel links between Adventure regions and who you can meet in each. */
public class RegionGraph {
    private final ContentRegistry content;
    private final AdventureConfig config;

    public RegionGraph(ContentRegistry content, AdventureConfig config) {
        this.content = content;
        this.config = config;
    }

    public RegionDefinition region(String id) {
        return content.region(id);
    }

    public RegionDefinition start() {
        return content.region(config.startRegion());
    }

    /** Neighboring regions, ordered by level requirement then name. */
    public List<RegionDefinition> neighbors(RegionDefinition from) {
        List<RegionDefinition> list = new ArrayList<>();
        for (String id : from.neighbors()) {
            list.add(content.region(id));
        }
        list.sort(Comparator.comparingInt(RegionDefinition::levelReq).thenComparing(RegionDefinition::name));
        return list;
    }

    /** Spawns a player of this level can meet here (never empty for valid content). */
    public List<SpawnEntry> eligibleSpawns(RegionDefinition region, int playerLevel) {
        return region.mobs().stream().filter(s -> s.minLevel() <= playerLevel).toList();
    }

    /**
     * Picks an encounter. Mobs below the player's level sometimes appear as an
     * "Enhanced" version 1..N levels higher (never lower than the base mob).
     */
    public Mob encounter(RegionDefinition region, int playerLevel, Rng rng) {
        List<SpawnEntry> spawns = eligibleSpawns(region, playerLevel);
        if (spawns.isEmpty()) {
            throw new IllegalStateException("No mob in " + region.id() + " for level " + playerLevel);
        }
        SpawnEntry s = rng.weighted(spawns, SpawnEntry::weight);
        MobDefinition def = content.mob(s.mobId());
        int level = rng.between(s.minLevel(), Math.min(s.maxLevel(), Math.max(s.minLevel(), playerLevel)));
        if (playerLevel > level && rng.chance(config.enhancedChancePercent() / 100.0)) {
            int top = Math.min(level + config.enhancedMaxLevelsAbove(), playerLevel);
            int enhancedLevel = rng.between(level + 1, top);
            return MobFactory.spawn(def, "Enhanced " + def.name(), enhancedLevel, 1.0, rng);
        }
        return MobFactory.spawn(def, level, 1.0, rng);
    }
}
