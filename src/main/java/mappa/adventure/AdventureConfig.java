package mappa.adventure;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mappa.core.content.ContentException;
import mappa.core.content.ContentRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tunable numbers for Mappa Adventure, read from data/adventure/config.json. */
public record AdventureConfig(String levelCurve, String startRegion, int inventorySize, int startingGold,
                              int sellPercent, int[] healthByLevel, int goldPerLevelUp,
                              List<String> startingEquipment, Map<String, Integer> startingItems,
                              int critChancePercent, int critMultiplier, double varianceMin, double varianceMax,
                              int escapeChancePercent, int enhancedChancePercent, int enhancedMaxLevelsAbove,
                              double chestMonsterGoldMin, double chestMonsterGoldMax, int reviveCostPerLevel,
                              int searchCountdownStart, int countdownAfterOpenMin, int countdownAfterOpenMax,
                              int countdownPerWinMin, int countdownPerWinMax, int keyDropChancePercent,
                              String shopTag, String potionTag) {

    public static AdventureConfig from(ContentRegistry content) {
        JsonObject o = content.config("adventure");
        JsonArray hp = o.getAsJsonArray("healthByLevel");
        int[] health = new int[hp.size()];
        for (int i = 0; i < health.length; i++) {
            health[i] = hp.get(i).getAsInt();
        }
        int maxLevel = content.levelCurve(o.get("levelCurve").getAsString()).maxLevel();
        if (health.length != maxLevel + 1) {
            throw new ContentException("config 'adventure': healthByLevel needs " + (maxLevel + 1)
                    + " entries (levels 0.." + maxLevel + "), found " + health.length);
        }
        List<String> equipment = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("startingEquipment")) {
            equipment.add(e.getAsString());
        }
        Map<String, Integer> items = new LinkedHashMap<>();
        for (JsonElement e : o.getAsJsonArray("startingItems")) {
            JsonObject it = e.getAsJsonObject();
            items.put(it.get("id").getAsString(), it.has("count") ? it.get("count").getAsInt() : 1);
        }
        JsonObject c = o.getAsJsonObject("combat");
        JsonArray variance = c.getAsJsonArray("damageVariance");
        JsonArray chestGold = c.getAsJsonArray("monsterChestRewardMultiplier");
        JsonObject ch = o.getAsJsonObject("chests");
        JsonArray after = ch.getAsJsonArray("searchCountdownAfterOpen");
        JsonArray perWin = ch.getAsJsonArray("countdownPerWin");
        return new AdventureConfig(o.get("levelCurve").getAsString(), o.get("startRegion").getAsString(),
                o.get("inventorySize").getAsInt(), o.get("startingGold").getAsInt(), o.get("sellPercent").getAsInt(),
                health, o.get("goldPerLevelUp").getAsInt(), List.copyOf(equipment), Map.copyOf(items),
                c.get("critChancePercent").getAsInt(), c.get("critMultiplier").getAsInt(),
                variance.get(0).getAsDouble(), variance.get(1).getAsDouble(), c.get("escapeChancePercent").getAsInt(),
                c.get("enhancedChancePercent").getAsInt(), c.get("enhancedMaxLevelsAbove").getAsInt(),
                chestGold.get(0).getAsDouble(), chestGold.get(1).getAsDouble(), o.get("reviveCostPerLevel").getAsInt(),
                ch.get("searchCountdownStart").getAsInt(), after.get(0).getAsInt(), after.get(1).getAsInt(),
                perWin.get(0).getAsInt(), perWin.get(1).getAsInt(), ch.get("keyDropChancePercent").getAsInt(),
                o.has("shopTag") ? o.get("shopTag").getAsString() : "adv:shop",
                o.has("potionTag") ? o.get("potionTag").getAsString() : "adv:potion");
    }
}
