package mappa.dungeons;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mappa.core.content.ContentRegistry;
import mappa.core.items.ItemInstance;
import mappa.core.save.ItemCodec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * What a Mappa Dungeons save contains and how it is restored. Saves are only made in the
 * lobby, so there is never a half-finished run in a save file.
 * Format 1: player (name, level, exp, gold, points), equipment, spells, inventory, cleared dungeons.
 */
final class DungeonsSave {
    static final String MODE = "dungeons";
    static final int FORMAT = 1;

    private DungeonsSave() {
    }

    static String summary(DungeonPlayer p, ContentRegistry content) {
        long cleared = content.dungeons().stream().filter(d -> p.hasCleared(d.id())).count();
        return p.name() + " - Level " + p.level() + " - " + cleared + "/" + content.dungeons().size()
                + " dungeons cleared";
    }

    static JsonObject write(DungeonPlayer p) {
        JsonObject player = new JsonObject();
        player.addProperty("name", p.name());
        player.addProperty("level", p.level());
        player.addProperty("exp", p.progression().exp());
        player.addProperty("gold", p.wallet().gold());

        JsonObject pts = new JsonObject();
        for (DungeonPlayer.Attribute a : DungeonPlayer.Attribute.values()) {
            pts.addProperty(a.name(), p.points(a));
        }
        pts.addProperty("available", p.availablePoints());
        player.add("points", pts);

        JsonArray spells = new JsonArray();
        for (int s = 0; s < DungeonPlayer.SPELL_SLOTS; s++) {
            ItemInstance spell = p.spell(s);
            spells.add(spell == null ? null : ItemCodec.encode(spell));
        }
        JsonArray cleared = new JsonArray();
        new TreeSet<>(p.clearedDungeons()).forEach(cleared::add);

        JsonObject data = new JsonObject();
        data.add("player", player);
        data.add("equipment", ItemCodec.encodeEquipment(p.equipment()));
        data.add("spells", spells);
        data.add("inventory", ItemCodec.encodeInventory(p.inventory()));
        data.add("cleared", cleared);
        return data;
    }

    /** Builds the saved player. Anything that couldn't be restored is added to {@code warnings}. */
    static DungeonPlayer read(JsonObject data, ContentRegistry content, DungeonsConfig config, List<String> warnings) {
        JsonObject pj = data.getAsJsonObject("player");
        DungeonPlayer p = DungeonPlayer.blank(pj.get("name").getAsString(), config, content);
        p.restoreProgress(intOr(pj, "level", 0), intOr(pj, "exp", 0));

        Map<DungeonPlayer.Attribute, Integer> spent = new EnumMap<>(DungeonPlayer.Attribute.class);
        JsonObject pts = pj.has("points") ? pj.getAsJsonObject("points") : new JsonObject();
        for (DungeonPlayer.Attribute a : DungeonPlayer.Attribute.values()) {
            spent.put(a, intOr(pts, a.name(), 0));
        }
        p.restorePoints(spent, intOr(pts, "available", 0));

        ItemCodec.decodeEquipment(data.getAsJsonObject("equipment"), p.equipment(), p.inventory(), p.level(),
                content, warnings);
        ItemCodec.decodeInventory(data.getAsJsonArray("inventory"), p.inventory(), content, warnings);

        JsonArray spells = data.has("spells") ? data.getAsJsonArray("spells") : new JsonArray();
        for (int s = 0; s < Math.min(spells.size(), DungeonPlayer.SPELL_SLOTS); s++) {
            JsonElement el = spells.get(s);
            if (el == null || el.isJsonNull()) {
                continue;
            }
            ItemInstance spell = ItemCodec.decode(el.getAsJsonObject(), content, warnings);
            if (spell != null && !p.restoreSpell(s, spell)) {
                String where = p.inventory().add(spell) ? "moved to your inventory" : "lost (inventory full)";
                warnings.add(spell.name() + " can't be used in spell slot " + (s + 1) + " any more and was " + where + ".");
            }
        }

        if (data.has("cleared")) {
            for (JsonElement el : data.getAsJsonArray("cleared")) {
                String id = el.getAsString();
                if (content.dungeons().stream().anyMatch(d -> d.id().equals(id))) {
                    p.markCleared(id);
                } else {
                    warnings.add("Cleared dungeon '" + id + "' no longer exists.");
                }
            }
        }
        p.wallet().add(Math.max(0, pj.has("gold") ? pj.get("gold").getAsLong() : 0));
        p.health().fill();
        return p;
    }

    private static int intOr(JsonObject o, String field, int def) {
        return o.has(field) ? o.get(field).getAsInt() : def;
    }
}
