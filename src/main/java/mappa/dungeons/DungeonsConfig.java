package mappa.dungeons;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mappa.core.content.ContentException;
import mappa.core.content.ContentRegistry;
import mappa.core.io.Ansi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tunable numbers for Mappa Dungeons, read from data/dungeons/config.json. */
public record DungeonsConfig(String levelCurve, int startingHealth, int healthPerPoint, int damagePerPoint,
                             int pointsPerLevel, int resetCostPerPoint, int inventorySize, int sellPercent,
                             int startingGold, List<String> startingItems, List<RunMode> runModes) {

    public static DungeonsConfig from(ContentRegistry content) {
        JsonObject o = content.config("dungeons");
        List<String> problems = new ArrayList<>();
        List<String> starting = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("startingItems")) {
            starting.add(e.getAsString());
        }
        List<RunMode> modes = new ArrayList<>();
        if (!o.has("runModes") || o.getAsJsonArray("runModes").isEmpty()) {
            problems.add("config 'dungeons': runModes must list at least one mode");
        } else {
            for (JsonElement e : o.getAsJsonArray("runModes")) {
                JsonObject m = e.getAsJsonObject();
                String id = m.get("id").getAsString();
                RunMode mode = new RunMode(id, m.get("name").getAsString(),
                        m.has("color") ? Ansi.Color.parse(m.get("color").getAsString()) : Ansi.Color.DEFAULT,
                        intOr(m, "lives", 0), intOr(m, "deathTurnPenalty", 0), intOr(m, "respawnHealthPercent", 100),
                        intOr(m, "rewardItems", 1), m.has("description") ? m.get("description").getAsString() : "");
                problems.addAll(validate(mode));
                if (modes.stream().anyMatch(x -> x.id().equals(id))) {
                    problems.add("config 'dungeons': duplicate run mode id '" + id + "'");
                }
                modes.add(mode);
            }
        }
        if (!problems.isEmpty()) {
            throw new ContentException(problems);
        }
        return new DungeonsConfig(o.get("levelCurve").getAsString(), o.get("startingHealth").getAsInt(),
                o.get("healthPerPoint").getAsInt(), o.get("damagePerPoint").getAsInt(),
                o.get("pointsPerLevel").getAsInt(), o.get("resetCostPerPoint").getAsInt(),
                o.get("inventorySize").getAsInt(), o.get("sellPercent").getAsInt(), o.get("startingGold").getAsInt(),
                List.copyOf(starting), List.copyOf(modes));
    }

    static List<String> validate(RunMode m) {
        List<String> p = new ArrayList<>();
        String w = "run mode '" + m.id() + "'";
        if (m.lives() < 0) {
            p.add(w + ": lives must be >= 0 (0 = unlimited)");
        }
        if (m.deathTurnPenalty() < 0) {
            p.add(w + ": deathTurnPenalty must be >= 0");
        }
        if (m.respawnHealthPercent() < 1 || m.respawnHealthPercent() > 100) {
            p.add(w + ": respawnHealthPercent must be 1..100");
        }
        if (m.rewardItems() < 0) {
            p.add(w + ": rewardItems must be >= 0");
        }
        return p;
    }

    public RunMode runMode(String id) {
        for (RunMode m : runModes) {
            if (m.id().equalsIgnoreCase(id)) {
                return m;
            }
        }
        throw new ContentException("Unknown run mode '" + id.toUpperCase(Locale.ROOT) + "'");
    }

    private static int intOr(JsonObject o, String f, int def) {
        return o.has(f) ? o.get(f).getAsInt() : def;
    }
}
