package mappa.explore;

import com.google.gson.JsonObject;
import mappa.core.content.ContentRegistry;

/** Tunable numbers for Mappa Explore, from data/explore/config.json. */
public record ExploreConfig(String levelCurve, int startingHealth, int maxHunger, int maxThirst, int hoursPerAction,
                            int hoursPerDay, int nightStartsAt, int nightEndsAt, int starvationDamagePerHour,
                            int foodRestore, int drinkRestore, int inventorySize, int worldSize) {

    public static ExploreConfig from(ContentRegistry content) {
        JsonObject o = content.config("explore");
        return new ExploreConfig(o.get("levelCurve").getAsString(), o.get("startingHealth").getAsInt(),
                o.get("maxHunger").getAsInt(), o.get("maxThirst").getAsInt(), o.get("hoursPerAction").getAsInt(),
                o.get("hoursPerDay").getAsInt(), o.get("nightStartsAt").getAsInt(), o.get("nightEndsAt").getAsInt(),
                o.get("starvationDamagePerHour").getAsInt(), o.get("foodRestore").getAsInt(),
                o.get("drinkRestore").getAsInt(), o.get("inventorySize").getAsInt(), o.get("worldSize").getAsInt());
    }
}
