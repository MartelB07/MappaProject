package mappa.core.content;

import com.google.gson.JsonObject;
import mappa.core.items.ItemDefinition;
import mappa.core.items.Rarity;
import mappa.core.items.RarityRoller;
import mappa.core.mobs.MobDefinition;
import mappa.core.progression.LevelCurve;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All loaded game content, looked up by id. Built by {@link JsonContentLoader};
 * read-only afterwards.
 */
public class ContentRegistry {
    final Map<String, Rarity> rarities = new LinkedHashMap<>();
    final Map<String, Difficulty> difficulties = new LinkedHashMap<>();
    final Map<String, LevelCurve> levelCurves = new LinkedHashMap<>();
    final Map<String, ItemDefinition> items = new LinkedHashMap<>();
    final Map<String, MobDefinition> mobs = new LinkedHashMap<>();
    final Map<String, DungeonDefinition> dungeons = new LinkedHashMap<>();
    final Map<String, RegionDefinition> regions = new LinkedHashMap<>();
    final Map<String, ChestDefinition> chests = new LinkedHashMap<>();
    final Map<String, BiomeDefinition> biomes = new LinkedHashMap<>();
    final Map<String, JsonObject> configs = new LinkedHashMap<>();
    /** Which file each id came from, for error messages. */
    final Map<String, String> origins = new LinkedHashMap<>();

    ContentRegistry() {
    }

    // ---- lookups (throw with a clear message when missing) ----

    public Rarity rarity(String id) {
        return require(rarities, id, "rarity");
    }

    public Difficulty difficulty(String id) {
        return require(difficulties, id, "difficulty");
    }

    public LevelCurve levelCurve(String id) {
        return require(levelCurves, id, "level curve");
    }

    public ItemDefinition item(String id) {
        return require(items, id, "item");
    }

    public MobDefinition mob(String id) {
        return require(mobs, id, "mob");
    }

    public DungeonDefinition dungeon(String id) {
        return require(dungeons, id, "dungeon");
    }

    public RegionDefinition region(String id) {
        return require(regions, id, "region");
    }

    public BiomeDefinition biome(String id) {
        return require(biomes, id, "biome");
    }

    public JsonObject config(String id) {
        return require(configs, id, "config");
    }

    public boolean hasItem(String id) {
        return items.containsKey(id);
    }

    // ---- collections ----

    public Map<String, Rarity> raritiesById() {
        return Collections.unmodifiableMap(rarities);
    }

    public RarityRoller rarityRoller() {
        return new RarityRoller(rarities.values());
    }

    /** Difficulties ordered by rank (easiest first). */
    public List<Difficulty> difficulties() {
        List<Difficulty> list = new ArrayList<>(difficulties.values());
        list.sort(Comparator.comparingInt(Difficulty::rank));
        return list;
    }

    public Collection<ItemDefinition> items() {
        return Collections.unmodifiableCollection(items.values());
    }

    public List<ItemDefinition> itemsWithTag(String tag) {
        return items.values().stream().filter(i -> i.hasTag(tag)).toList();
    }

    public Collection<MobDefinition> mobs() {
        return Collections.unmodifiableCollection(mobs.values());
    }

    /** Dungeons ordered by their {@code order} field. */
    public List<DungeonDefinition> dungeons() {
        List<DungeonDefinition> list = new ArrayList<>(dungeons.values());
        list.sort(Comparator.comparingInt(DungeonDefinition::order));
        return list;
    }

    public Collection<RegionDefinition> regions() {
        return Collections.unmodifiableCollection(regions.values());
    }

    public Collection<ChestDefinition> chests() {
        return Collections.unmodifiableCollection(chests.values());
    }

    public Collection<BiomeDefinition> biomes() {
        return Collections.unmodifiableCollection(biomes.values());
    }

    public String originOf(String id) {
        return origins.getOrDefault(id, "?");
    }

    private static <T> T require(Map<String, T> map, String id, String kind) {
        T value = map.get(id);
        if (value == null) {
            throw new ContentException("Unknown " + kind + " id: '" + id + "'");
        }
        return value;
    }
}
