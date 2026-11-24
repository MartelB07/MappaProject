package mappa.core.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import mappa.core.io.Ansi;
import mappa.core.items.ClassTag;
import mappa.core.items.ItemDefinition;
import mappa.core.items.ItemEffect;
import mappa.core.items.ItemSlot;
import mappa.core.items.Rarity;
import mappa.core.mobs.MobDefinition;
import mappa.core.mobs.StatGrowth;
import mappa.core.progression.LevelCurve;
import mappa.core.stats.StatType;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads every content file listed in {@code index.json} into a {@link ContentRegistry},
 * then runs {@link ContentValidator}. All problems are collected and reported together.
 *
 * <p>To add content: create the JSON file and add its path to {@code data/index.json}
 * under the right type.</p>
 */
public final class JsonContentLoader {

    /** Supplies a file's contents by path relative to the data root. */
    @FunctionalInterface
    public interface Source {
        InputStream open(String relativePath) throws IOException;
    }

    public static final String INDEX = "index.json";

    private final Source source;
    private final List<String> problems = new ArrayList<>();
    private final ContentRegistry registry = new ContentRegistry();

    private JsonContentLoader(Source source) {
        this.source = source;
    }

    /** Loads from the classpath, e.g. {@code loadClasspath("data")}. */
    public static ContentRegistry loadClasspath(String root) {
        ClassLoader cl = JsonContentLoader.class.getClassLoader();
        return load(path -> {
            InputStream in = cl.getResourceAsStream(root + "/" + path);
            if (in == null) {
                throw new IOException("Not found on classpath: " + root + "/" + path);
            }
            return in;
        });
    }

    /** Loads from a directory on disk (used by tests). */
    public static ContentRegistry loadDirectory(Path root) {
        return load(path -> Files.newInputStream(root.resolve(path)));
    }

    public static ContentRegistry load(Source source) {
        JsonContentLoader loader = new JsonContentLoader(source);
        loader.loadAll();
        if (!loader.problems.isEmpty()) {
            throw new ContentException(loader.problems);
        }
        List<String> validation = ContentValidator.validate(loader.registry);
        if (!validation.isEmpty()) {
            throw new ContentException(validation);
        }
        return loader.registry;
    }

    /** Lists every file path named in the index (used by tests to find unlisted files). */
    public static List<String> indexedFiles(Source source) {
        JsonContentLoader loader = new JsonContentLoader(source);
        JsonObject index = loader.read(INDEX).getAsJsonObject();
        List<String> files = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : index.getAsJsonObject("files").entrySet()) {
            for (JsonElement f : e.getValue().getAsJsonArray()) {
                files.add(f.getAsString());
            }
        }
        return files;
    }

    // ------------------------------------------------------------------ loading

    private void loadAll() {
        JsonElement indexEl;
        try {
            indexEl = read(INDEX);
        } catch (ContentException e) {
            problems.addAll(e.problems());
            return;
        }
        JsonObject files = indexEl.getAsJsonObject().getAsJsonObject("files");
        if (files == null) {
            problems.add(INDEX + ": missing \"files\" object");
            return;
        }
        // Load in dependency-friendly order (rarities/difficulties first).
        String[] order = {"rarities", "difficulties", "level_curves", "items", "mobs", "dungeons", "regions",
                "chests", "biomes", "configs"};
        Set<String> known = Set.of(order);
        for (String type : files.keySet()) {
            if (!known.contains(type)) {
                problems.add(INDEX + ": unknown content type '" + type + "' (expected one of " + known + ")");
            }
        }
        for (String type : order) {
            JsonArray list = files.getAsJsonArray(type);
            if (list == null) {
                continue;
            }
            for (JsonElement f : list) {
                String path = f.getAsString();
                JsonElement root;
                try {
                    root = read(path);
                } catch (ContentException e) {
                    problems.addAll(e.problems());
                    continue;
                }
                loadFile(type, path, root);
            }
        }
        makeRegionLinksTwoWay();
    }

    private JsonElement read(String path) {
        try (InputStream in = source.open(path); Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r);
        } catch (IOException e) {
            throw new ContentException(path + ": cannot read file (" + e.getMessage() + ")");
        } catch (JsonParseException | IllegalStateException e) {
            throw new ContentException(path + ": malformed JSON (" + rootCause(e).getMessage() + ")");
        }
    }

    private void loadFile(String type, String path, JsonElement root) {
        switch (type) {
            case "level_curves" -> eachEntry(path, root.getAsJsonObject(), this::levelCurve);
            case "dungeons" -> single(path, root, this::dungeon);
            case "configs" -> single(path, root, this::config);
            default -> {
                if (!root.isJsonArray()) {
                    problems.add(path + ": expected a JSON array of " + type);
                    return;
                }
                JsonArray arr = root.getAsJsonArray();
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = arr.get(i).getAsJsonObject();
                    Ctx ctx = new Ctx(path + " [" + i + (o.has("id") ? " '" + o.get("id").getAsString() + "'" : "") + "]");
                    try {
                        switch (type) {
                            case "rarities" -> rarity(ctx, o);
                            case "difficulties" -> difficulty(ctx, o);
                            case "items" -> item(ctx, o);
                            case "mobs" -> mob(ctx, o);
                            case "regions" -> region(ctx, o);
                            case "chests" -> chest(ctx, o);
                            case "biomes" -> biome(ctx, o);
                            default -> throw new IllegalStateException(type);
                        }
                    } catch (RuntimeException e) {
                        problems.add(ctx.where + ": " + rootCause(e).getMessage());
                    }
                }
            }
        }
    }

    private interface EntryLoader {
        void load(Ctx ctx, String key, JsonObject o);
    }

    private interface SingleLoader {
        void load(Ctx ctx, JsonObject o);
    }

    private void eachEntry(String path, JsonObject root, EntryLoader loader) {
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            Ctx ctx = new Ctx(path + " ['" + e.getKey() + "']");
            try {
                loader.load(ctx, e.getKey(), e.getValue().getAsJsonObject());
            } catch (RuntimeException ex) {
                problems.add(ctx.where + ": " + rootCause(ex).getMessage());
            }
        }
    }

    private void single(String path, JsonElement root, SingleLoader loader) {
        Ctx ctx = new Ctx(path);
        try {
            loader.load(ctx, root.getAsJsonObject());
        } catch (RuntimeException ex) {
            problems.add(path + ": " + rootCause(ex).getMessage());
        }
    }

    // ------------------------------------------------------------------ parsers

    private void rarity(Ctx c, JsonObject o) {
        String id = c.str(o, "id");
        double[] mult = c.doublePair(o, "multiplier");
        put(c, registry.rarities, "rarity", id, new Rarity(id, c.str(o, "name"), color(c, o), c.integer(o, "rank"),
                c.integer(o, "weight"), mult[0], mult[1]));
    }

    private void difficulty(Ctx c, JsonObject o) {
        String id = c.str(o, "id");
        put(c, registry.difficulties, "difficulty", id, new Difficulty(id, c.str(o, "name"), color(c, o),
                c.integer(o, "rank"), c.dbl(o, "mobScale"), c.strSet(o, "allowedRarities")));
    }

    private void levelCurve(Ctx c, String id, JsonObject o) {
        LevelCurve curve;
        if (o.has("thresholds")) {
            JsonArray arr = o.getAsJsonArray("thresholds");
            int[] t = new int[arr.size()];
            for (int i = 0; i < t.length; i++) {
                t[i] = arr.get(i).getAsInt();
            }
            curve = new LevelCurve(t);
        } else if (o.has("formula")) {
            JsonObject f = o.getAsJsonObject("formula");
            curve = LevelCurve.formula(c.integer(f, "maxLevel"), c.dbl(f, "base"), c.dbl(f, "growth"));
        } else {
            throw new IllegalArgumentException("needs \"thresholds\" or \"formula\"");
        }
        put(c, registry.levelCurves, "level curve", id, curve);
    }

    private void item(Ctx c, JsonObject o) {
        String id = c.str(o, "id");
        ItemEffect effect = null;
        if (o.has("effect")) {
            JsonObject e = o.getAsJsonObject("effect");
            ItemEffect.Target target = e.has("target") ? c.enumValue(ItemEffect.Target.class, e, "target") : null;
            effect = new ItemEffect(c.optInt(e, "heal", 0), c.optInt(e, "damage", 0), target,
                    c.optInt(e, "cooldown", 0));
        }
        ClassTag tag = o.has("class") ? c.enumValue(ClassTag.class, o, "class") : ClassTag.ANY;
        put(c, registry.items, "item", id, new ItemDefinition(id, c.str(o, "name"),
                c.enumValue(ItemSlot.class, o, "slot"), tag, c.optInt(o, "levelReq", 0), c.optInt(o, "price", 0),
                statMap(c, o), effect, c.optStrSet(o, "tags"), c.optStr(o, "fixedRarity", null)));
    }

    private Map<StatType, Integer> statMap(Ctx c, JsonObject o) {
        Map<StatType, Integer> stats = new EnumMap<>(StatType.class);
        if (o.has("stats")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("stats").entrySet()) {
                stats.put(c.parseEnum(StatType.class, e.getKey(), "stats"), e.getValue().getAsInt());
            }
        }
        return stats;
    }

    private void mob(Ctx c, JsonObject o) {
        String id = c.str(o, "id");
        Map<StatType, StatGrowth> stats = new EnumMap<>(StatType.class);
        if (o.has("stats")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("stats").entrySet()) {
                StatType type = c.parseEnum(StatType.class, e.getKey(), "stats");
                JsonElement v = e.getValue();
                if (v.isJsonPrimitive()) {
                    stats.put(type, new StatGrowth(v.getAsInt(), 0.0));
                } else {
                    JsonObject g = v.getAsJsonObject();
                    stats.put(type, new StatGrowth(c.integer(g, "base"), c.optDbl(g, "perLevel", 0.0)));
                }
            }
        }
        int[] gold = o.has("gold") && o.get("gold").isJsonArray() ? c.intPair(o, "gold")
                : new int[]{c.optInt(o, "gold", 0), c.optInt(o, "gold", 0)};
        put(c, registry.mobs, "mob", id, new MobDefinition(id, c.str(o, "name"), c.optStr(o, "archetype", null),
                c.optBool(o, "boss", false), c.optInt(o, "baseLevel", 0), stats, c.optInt(o, "exp", 0),
                gold[0], gold[1], c.optInt(o, "attackEvery", 3)));
    }

    private void dungeon(Ctx c, JsonObject o) {
        String id = c.str(o, "id");
        Map<String, DungeonDefinition.Settings> diffs = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("difficulties").entrySet()) {
            JsonObject d = e.getValue().getAsJsonObject();
            int levelReq = c.integer(d, "levelReq");
            diffs.put(e.getKey(), new DungeonDefinition.Settings(levelReq, c.optInt(d, "mobLevel", levelReq),
                    c.integer(d, "rewardLevelCap")));
        }
        List<List<String>> waves = new ArrayList<>();
        for (JsonElement w : o.getAsJsonArray("waves")) {
            List<String> wave = new ArrayList<>();
            for (JsonElement m : w.getAsJsonArray()) {
                wave.add(m.getAsString());
            }
            waves.add(wave);
        }
        JsonObject pool = o.has("rewardPool") ? o.getAsJsonObject("rewardPool") : new JsonObject();
        put(c, registry.dungeons, "dungeon", id, new DungeonDefinition(id, c.str(o, "name"), c.optInt(o, "order", 0),
                c.optStr(o, "unlockRequires", null), diffs, c.optInt(o, "turnLimit", 75), waves, c.str(o, "boss"),
                c.optStrSet(pool, "tags"), c.optStrSet(pool, "items")));
    }

    private void region(Ctx c, JsonObject o) {
        String id = c.str(o, "id");
        int[] chestLevel = o.has("chestLevel") ? c.intPair(o, "chestLevel") : new int[]{1, 1};
        put(c, registry.regions, "region", id, new RegionDefinition(id, c.str(o, "name"), c.optInt(o, "levelReq", 0),
                c.optBool(o, "hasChests", false), chestLevel[0], chestLevel[1], spawns(c, o),
                c.optStrSet(o, "neighbors"), c.optBool(o, "isFinal", false)));
    }

    private List<SpawnEntry> spawns(Ctx c, JsonObject o) {
        List<SpawnEntry> list = new ArrayList<>();
        if (o.has("mobs")) {
            for (JsonElement el : o.getAsJsonArray("mobs")) {
                JsonObject m = el.getAsJsonObject();
                int[] range = c.intPair(m, "levelRange");
                list.add(new SpawnEntry(c.str(m, "id"), c.optInt(m, "weight", 1), range[0], range[1],
                        c.optBool(m, "nightOnly", false)));
            }
        }
        return list;
    }

    private void chest(Ctx c, JsonObject o) {
        String id = c.str(o, "id");
        int[] amount = o.has("amount") ? c.intPair(o, "amount") : new int[]{1, 1};
        put(c, registry.chests, "chest", id, new ChestDefinition(id, c.str(o, "name"),
                c.enumValue(ChestDefinition.Kind.class, o, "kind"), c.optInt(o, "weight", 1),
                c.optStrSet(o, "itemTags"), amount[0], amount[1]));
    }

    private void biome(Ctx c, JsonObject o) {
        String id = c.str(o, "id");
        int[] danger = o.has("danger") ? c.intPair(o, "danger") : new int[]{1, 1};
        List<BiomeDefinition.ResourceYield> resources = new ArrayList<>();
        if (o.has("resources")) {
            for (JsonElement el : o.getAsJsonArray("resources")) {
                JsonObject r = el.getAsJsonObject();
                int[] y = c.intPair(r, "yield");
                resources.add(new BiomeDefinition.ResourceYield(c.str(r, "item"), c.optInt(r, "weight", 1), y[0], y[1]));
            }
        }
        List<BiomeDefinition.Hazard> hazards = new ArrayList<>();
        if (o.has("hazards")) {
            for (JsonElement el : o.getAsJsonArray("hazards")) {
                JsonObject h = el.getAsJsonObject();
                hazards.add(new BiomeDefinition.Hazard(c.str(h, "id"), c.optStr(h, "description", c.str(h, "id")),
                        c.dbl(h, "chance"), c.optInt(h, "damage", 0)));
            }
        }
        JsonObject survival = o.has("survival") ? o.getAsJsonObject("survival") : new JsonObject();
        put(c, registry.biomes, "biome", id, new BiomeDefinition(id, c.str(o, "name"), danger[0], danger[1],
                c.optInt(o, "weight", 1), resources, spawns(c, o), hazards,
                c.optInt(survival, "hungerDrainPerHour", 0), c.optInt(survival, "thirstDrainPerHour", 0),
                c.optStrSet(o, "traits")));
    }

    private void config(Ctx c, JsonObject o) {
        String id = c.str(o, "id");
        put(c, registry.configs, "config", id, o);
    }

    private Ansi.Color color(Ctx c, JsonObject o) {
        return o.has("color") ? c.enumValue(Ansi.Color.class, o, "color") : Ansi.Color.DEFAULT;
    }

    private <T> void put(Ctx c, Map<String, T> map, String kind, String id, T value) {
        if (map.containsKey(id)) {
            problems.add(c.where + ": duplicate " + kind + " id '" + id + "' (already defined in "
                    + registry.origins.get(kind + ":" + id) + ")");
            return;
        }
        map.put(id, value);
        registry.origins.put(kind + ":" + id, c.where);
        registry.origins.putIfAbsent(id, c.where);
    }

    private void makeRegionLinksTwoWay() {
        Map<String, Set<String>> links = new HashMap<>();
        for (RegionDefinition r : registry.regions.values()) {
            links.computeIfAbsent(r.id(), k -> new LinkedHashSet<>()).addAll(r.neighbors());
            for (String n : r.neighbors()) {
                links.computeIfAbsent(n, k -> new LinkedHashSet<>()).add(r.id());
            }
        }
        for (Map.Entry<String, RegionDefinition> e : new HashMap<>(registry.regions).entrySet()) {
            registry.regions.put(e.getKey(), e.getValue().withNeighbors(links.get(e.getKey())));
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? t : cur;
    }

    /** Field accessors that produce readable errors naming the missing/bad field. */
    private static final class Ctx {
        final String where;

        Ctx(String where) {
            this.where = where;
        }

        JsonElement req(JsonObject o, String field) {
            JsonElement e = o.get(field);
            if (e == null || e.isJsonNull()) {
                throw new IllegalArgumentException("missing required field \"" + field + "\"");
            }
            return e;
        }

        String str(JsonObject o, String f) {
            return req(o, f).getAsString();
        }

        String optStr(JsonObject o, String f, String def) {
            return o.has(f) && !o.get(f).isJsonNull() ? o.get(f).getAsString() : def;
        }

        int integer(JsonObject o, String f) {
            try {
                return req(o, f).getAsInt();
            } catch (NumberFormatException | UnsupportedOperationException e) {
                throw new IllegalArgumentException("field \"" + f + "\" must be a whole number");
            }
        }

        int optInt(JsonObject o, String f, int def) {
            return o.has(f) ? integer(o, f) : def;
        }

        double dbl(JsonObject o, String f) {
            return req(o, f).getAsDouble();
        }

        double optDbl(JsonObject o, String f, double def) {
            return o.has(f) ? dbl(o, f) : def;
        }

        boolean optBool(JsonObject o, String f, boolean def) {
            return o.has(f) ? req(o, f).getAsBoolean() : def;
        }

        int[] intPair(JsonObject o, String f) {
            JsonElement e = req(o, f);
            if (e.isJsonPrimitive()) {
                int v = e.getAsInt();
                return new int[]{v, v};
            }
            JsonArray a = e.getAsJsonArray();
            if (a.size() != 2) {
                throw new IllegalArgumentException("field \"" + f + "\" must be [min, max]");
            }
            return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()};
        }

        double[] doublePair(JsonObject o, String f) {
            JsonArray a = req(o, f).getAsJsonArray();
            if (a.size() != 2) {
                throw new IllegalArgumentException("field \"" + f + "\" must be [min, max]");
            }
            return new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()};
        }

        Set<String> strSet(JsonObject o, String f) {
            Set<String> s = new LinkedHashSet<>();
            for (JsonElement e : req(o, f).getAsJsonArray()) {
                s.add(e.getAsString());
            }
            return s;
        }

        Set<String> optStrSet(JsonObject o, String f) {
            return o.has(f) ? strSet(o, f) : new HashSet<>();
        }

        <E extends Enum<E>> E enumValue(Class<E> type, JsonObject o, String f) {
            return parseEnum(type, str(o, f), f);
        }

        <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
            try {
                return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                List<String> allowed = new ArrayList<>();
                for (E v : type.getEnumConstants()) {
                    allowed.add(v.name());
                }
                throw new IllegalArgumentException("\"" + field + "\": unknown value '" + raw + "', expected one of "
                        + allowed);
            }
        }
    }
}
