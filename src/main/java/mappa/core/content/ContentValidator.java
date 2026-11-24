package mappa.core.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mappa.core.items.ItemDefinition;
import mappa.core.items.ItemSlot;
import mappa.core.items.Rarity;
import mappa.core.mobs.MobDefinition;
import mappa.core.stats.StatType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cross-file checks run after loading. Returns a list of human-readable problems
 * (empty = content is valid). New content is checked automatically.
 */
public final class ContentValidator {
    private ContentValidator() {
    }

    public static List<String> validate(ContentRegistry r) {
        List<String> p = new ArrayList<>();
        rarities(r, p);
        difficulties(r, p);
        items(r, p);
        mobs(r, p);
        dungeons(r, p);
        regions(r, p);
        chests(r, p);
        biomes(r, p);
        configs(r, p);
        return p;
    }

    private static void rarities(ContentRegistry r, List<String> p) {
        List<Rarity> list = new ArrayList<>(r.rarities.values());
        list.sort(Comparator.comparingInt(Rarity::rank));
        for (int i = 0; i < list.size(); i++) {
            Rarity x = list.get(i);
            if (x.weight() <= 0) {
                p.add("rarity '" + x.id() + "': weight must be > 0");
            }
            if (x.minMultiplier() <= 0 || x.maxMultiplier() < x.minMultiplier()) {
                p.add("rarity '" + x.id() + "': multiplier must be [min, max] with 0 < min <= max");
            }
            if (i > 0 && list.get(i - 1).maxMultiplier() > x.minMultiplier()) {
                p.add("rarity '" + x.id() + "': multiplier range overlaps '" + list.get(i - 1).id() + "'");
            }
            if (i > 0 && list.get(i - 1).rank() == x.rank()) {
                p.add("rarity '" + x.id() + "': duplicate rank " + x.rank());
            }
        }
    }

    private static void difficulties(ContentRegistry r, List<String> p) {
        for (Difficulty d : r.difficulties.values()) {
            if (d.mobScale() <= 0) {
                p.add("difficulty '" + d.id() + "': mobScale must be > 0");
            }
            if (d.allowedRarities().isEmpty()) {
                p.add("difficulty '" + d.id() + "': allowedRarities is empty");
            }
            for (String rar : d.allowedRarities()) {
                if (!r.rarities.containsKey(rar)) {
                    p.add("difficulty '" + d.id() + "': unknown rarity '" + rar + "'");
                }
            }
        }
    }

    private static void items(ContentRegistry r, List<String> p) {
        for (ItemDefinition i : r.items.values()) {
            String w = "item '" + i.id() + "'";
            if (i.levelReq() < 0) {
                p.add(w + ": levelReq must be >= 0");
            }
            if (i.price() < 0) {
                p.add(w + ": price must be >= 0");
            }
            if (i.fixedRarity() != null && !r.rarities.containsKey(i.fixedRarity())) {
                p.add(w + ": unknown fixedRarity '" + i.fixedRarity() + "'");
            }
            if (i.slot() == ItemSlot.CONSUMABLE && !i.effect().heals() && !i.effect().damages()) {
                p.add(w + ": consumable needs an effect with heal > 0 or damage > 0");
            }
            if (i.slot() == ItemSlot.SPELL && !i.effect().heals() && !i.effect().damages()) {
                p.add(w + ": spell needs an effect with heal > 0 or damage > 0");
            }
            if (i.effect().cooldown() < 0 || i.effect().heal() < 0 || i.effect().damage() < 0) {
                p.add(w + ": effect values must be >= 0");
            }
        }
    }

    private static void mobs(ContentRegistry r, List<String> p) {
        for (MobDefinition m : r.mobs.values()) {
            String w = "mob '" + m.id() + "'";
            if (m.stat(StatType.MAX_HEALTH).base() <= 0) {
                p.add(w + ": stats.MAX_HEALTH must be > 0");
            }
            if (m.stat(StatType.DAMAGE).base() < 0) {
                p.add(w + ": stats.DAMAGE must be >= 0");
            }
            if (m.exp() < 0) {
                p.add(w + ": exp must be >= 0");
            }
            if (m.goldMin() < 0 || m.goldMax() < m.goldMin()) {
                p.add(w + ": gold must be [min, max] with 0 <= min <= max");
            }
            if (m.baseLevel() < 0) {
                p.add(w + ": baseLevel must be >= 0");
            }
        }
    }

    private static void dungeons(ContentRegistry r, List<String> p) {
        for (DungeonDefinition d : r.dungeons.values()) {
            String w = "dungeon '" + d.id() + "'";
            if (d.unlockRequires() != null && !r.dungeons.containsKey(d.unlockRequires())) {
                p.add(w + ": unlockRequires unknown dungeon '" + d.unlockRequires() + "'");
            }
            if (d.difficulties().isEmpty()) {
                p.add(w + ": no difficulties defined");
            }
            if (d.turnLimit() <= 0) {
                p.add(w + ": turnLimit must be > 0");
            }
            if (d.waves().isEmpty()) {
                p.add(w + ": needs at least one wave");
            }
            for (int i = 0; i < d.waves().size(); i++) {
                if (d.waves().get(i).isEmpty()) {
                    p.add(w + ": wave " + (i + 1) + " is empty");
                }
                for (String mob : d.waves().get(i)) {
                    if (!r.mobs.containsKey(mob)) {
                        p.add(w + ": wave " + (i + 1) + " has unknown mob '" + mob + "'");
                    }
                }
            }
            MobDefinition boss = r.mobs.get(d.boss());
            if (boss == null) {
                p.add(w + ": unknown boss mob '" + d.boss() + "'");
            } else if (!boss.boss()) {
                p.add(w + ": boss mob '" + d.boss() + "' is not marked \"boss\": true");
            }
            for (String item : d.rewardItems()) {
                if (!r.items.containsKey(item)) {
                    p.add(w + ": rewardPool has unknown item '" + item + "'");
                }
            }
            List<ItemDefinition> pool = rewardPool(r, d);
            for (Map.Entry<String, DungeonDefinition.Settings> e : d.difficulties().entrySet()) {
                String diff = e.getKey();
                DungeonDefinition.Settings s = e.getValue();
                if (!r.difficulties.containsKey(diff)) {
                    p.add(w + ": unknown difficulty '" + diff + "'");
                }
                if (s.levelReq() < 0 || s.mobLevel() < 0 || s.rewardLevelCap() < 0) {
                    p.add(w + " " + diff + ": levels must be >= 0");
                }
                boolean anyDrop = pool.stream().anyMatch(i -> i.levelReq() <= s.rewardLevelCap());
                if (!anyDrop) {
                    p.add(w + " " + diff + ": no reward item has levelReq <= rewardLevelCap " + s.rewardLevelCap());
                }
            }
        }
        // unlock chain must not loop
        for (DungeonDefinition d : r.dungeons.values()) {
            Set<String> seen = new HashSet<>();
            String cur = d.id();
            while (cur != null && r.dungeons.containsKey(cur)) {
                if (!seen.add(cur)) {
                    p.add("dungeon '" + d.id() + "': unlockRequires chain forms a loop");
                    break;
                }
                cur = r.dungeons.get(cur).unlockRequires();
            }
        }
    }

    /** Items that can drop from a dungeon: tagged items plus explicitly listed ones. */
    public static List<ItemDefinition> rewardPool(ContentRegistry r, DungeonDefinition d) {
        List<ItemDefinition> pool = new ArrayList<>();
        for (ItemDefinition i : r.items.values()) {
            boolean tagged = false;
            for (String t : d.rewardTags()) {
                if (i.hasTag(t)) {
                    tagged = true;
                    break;
                }
            }
            if (tagged || d.rewardItems().contains(i.id())) {
                pool.add(i);
            }
        }
        return pool;
    }

    private static void regions(ContentRegistry r, List<String> p) {
        for (RegionDefinition reg : r.regions.values()) {
            String w = "region '" + reg.id() + "'";
            if (reg.hasChests() && (reg.chestLevelMin() < 1 || reg.chestLevelMax() < reg.chestLevelMin())) {
                p.add(w + ": chestLevel must be [min, max] with 1 <= min <= max");
            }
            for (String n : reg.neighbors()) {
                if (!r.regions.containsKey(n)) {
                    p.add(w + ": unknown neighbor '" + n + "'");
                }
            }
            spawns(r, p, w, reg.mobs());
            if (reg.mobs().isEmpty()) {
                p.add(w + ": has no mobs");
            } else if (reg.mobs().stream().noneMatch(s -> s.minLevel() <= reg.levelReq())) {
                p.add(w + ": no mob can spawn for a player at the region's levelReq " + reg.levelReq());
            }
        }
        if (r.configs.containsKey("adventure") && !r.regions.isEmpty()) {
            JsonElement start = r.configs.get("adventure").get("startRegion");
            if (start == null || !r.regions.containsKey(start.getAsString())) {
                p.add("config 'adventure': startRegion missing or unknown");
            } else {
                Set<String> reached = new HashSet<>();
                Deque<String> todo = new ArrayDeque<>();
                todo.add(start.getAsString());
                while (!todo.isEmpty()) {
                    String cur = todo.poll();
                    if (reached.add(cur) && r.regions.containsKey(cur)) {
                        todo.addAll(r.regions.get(cur).neighbors());
                    }
                }
                for (String id : r.regions.keySet()) {
                    if (!reached.contains(id)) {
                        p.add("region '" + id + "': not reachable from start region '" + start.getAsString() + "'");
                    }
                }
            }
        }
    }

    private static void spawns(ContentRegistry r, List<String> p, String w, List<SpawnEntry> spawns) {
        for (SpawnEntry s : spawns) {
            if (!r.mobs.containsKey(s.mobId())) {
                p.add(w + ": unknown mob '" + s.mobId() + "'");
            }
            if (s.weight() <= 0) {
                p.add(w + ": mob '" + s.mobId() + "' weight must be > 0");
            }
            if (s.minLevel() < 0 || s.maxLevel() < s.minLevel()) {
                p.add(w + ": mob '" + s.mobId() + "' levelRange must be [min, max] with 0 <= min <= max");
            }
        }
    }

    private static void chests(ContentRegistry r, List<String> p) {
        for (ChestDefinition c : r.chests.values()) {
            String w = "chest '" + c.id() + "'";
            if (c.weight() <= 0) {
                p.add(w + ": weight must be > 0");
            }
            if (c.amountMin() < 0 || c.amountMax() < c.amountMin()) {
                p.add(w + ": amount must be [min, max] with 0 <= min <= max");
            }
            if (c.kind() == ChestDefinition.Kind.ITEMS || c.kind() == ChestDefinition.Kind.CONSUMABLES) {
                boolean any = r.items.values().stream().anyMatch(i -> c.itemTags().stream().anyMatch(i::hasTag));
                if (!any) {
                    p.add(w + ": no item has any of the tags " + c.itemTags());
                }
            }
        }
    }

    private static void biomes(ContentRegistry r, List<String> p) {
        for (BiomeDefinition b : r.biomes.values()) {
            String w = "biome '" + b.id() + "'";
            if (b.dangerMin() < 0 || b.dangerMax() < b.dangerMin()) {
                p.add(w + ": danger must be [min, max]");
            }
            for (BiomeDefinition.ResourceYield y : b.resources()) {
                if (!r.items.containsKey(y.itemId())) {
                    p.add(w + ": unknown resource item '" + y.itemId() + "'");
                }
                if (y.min() < 0 || y.max() < y.min()) {
                    p.add(w + ": resource '" + y.itemId() + "' yield must be [min, max]");
                }
            }
            spawns(r, p, w, b.mobs());
            for (BiomeDefinition.Hazard h : b.hazards()) {
                if (h.chance() < 0 || h.chance() > 1) {
                    p.add(w + ": hazard '" + h.id() + "' chance must be between 0 and 1");
                }
            }
        }
    }

    private static void configs(ContentRegistry r, List<String> p) {
        for (Map.Entry<String, JsonObject> e : r.configs.entrySet()) {
            for (String field : List.of("startingItems", "startingEquipment")) {
                JsonElement start = e.getValue().get(field);
                if (start == null) {
                    continue;
                }
                for (JsonElement el : start.getAsJsonArray()) {
                    String id = el.isJsonObject() ? el.getAsJsonObject().get("id").getAsString() : el.getAsString();
                    if (!r.items.containsKey(id)) {
                        p.add("config '" + e.getKey() + "': " + field + " has unknown item '" + id + "'");
                    }
                }
            }
            JsonElement curve = e.getValue().get("levelCurve");
            if (curve != null && !r.levelCurves.containsKey(curve.getAsString())) {
                p.add("config '" + e.getKey() + "': unknown levelCurve '" + curve.getAsString() + "'");
            }
        }
    }
}
