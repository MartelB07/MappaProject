package mappa.explore;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mappa.core.content.BiomeDefinition;
import mappa.core.content.ContentRegistry;
import mappa.core.save.ItemCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Mappa Explore save contains and how it is restored.
 * Format 1: clock and needs, inventory, the world (biome ids) and the current position.
 */
final class ExploreSave {
    static final String MODE = "explore";
    static final int FORMAT = 1;

    private ExploreSave() {
    }

    static String summary(SurvivalState s, BiomeDefinition here) {
        return "Day " + s.day() + " - " + here.name() + " - Health " + s.health().current() + "/" + s.health().max();
    }

    static JsonObject write(SurvivalState s, List<BiomeDefinition> world, int position) {
        JsonObject survivor = new JsonObject();
        survivor.addProperty("day", s.day());
        survivor.addProperty("hour", s.hour());
        survivor.addProperty("health", s.health().current());
        survivor.addProperty("hunger", s.hunger());
        survivor.addProperty("thirst", s.thirst());

        JsonArray biomes = new JsonArray();
        world.forEach(b -> biomes.add(b.id()));

        JsonObject data = new JsonObject();
        data.add("survivor", survivor);
        data.add("inventory", ItemCodec.encodeInventory(s.inventory()));
        data.add("world", biomes);
        data.addProperty("position", position);
        return data;
    }

    /** Result of loading: the state, the world, and where the player stands. */
    record Loaded(SurvivalState state, List<BiomeDefinition> world, int position) {
    }

    static Loaded read(JsonObject data, ContentRegistry content, ExploreConfig config, List<String> warnings) {
        SurvivalState s = new SurvivalState(config);
        JsonObject sj = data.getAsJsonObject("survivor");
        s.restore(intOr(sj, "day", 1), intOr(sj, "hour", 8), intOr(sj, "health", 0),
                intOr(sj, "hunger", config.maxHunger()), intOr(sj, "thirst", config.maxThirst()));
        ItemCodec.decodeInventory(data.getAsJsonArray("inventory"), s.inventory(), content, warnings);

        List<BiomeDefinition> world = new ArrayList<>();
        BiomeDefinition fallback = content.biomes().iterator().next();
        for (JsonElement el : data.has("world") ? data.getAsJsonArray("world") : new JsonArray()) {
            String id = el.getAsString();
            try {
                world.add(content.biome(id));
            } catch (RuntimeException e) {
                warnings.add("Biome '" + id + "' no longer exists and was replaced with " + fallback.name() + ".");
                world.add(fallback);
            }
        }
        if (world.isEmpty()) {
            warnings.add("The saved world was empty; you start in " + fallback.name() + ".");
            world.add(fallback);
        }
        int position = Math.max(0, Math.min(world.size() - 1, intOr(data, "position", 0)));
        return new Loaded(s, world, position);
    }

    private static int intOr(JsonObject o, String field, int def) {
        return o.has(field) ? o.get(field).getAsInt() : def;
    }
}
