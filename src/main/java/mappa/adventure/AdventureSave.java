package mappa.adventure;

import com.google.gson.JsonObject;
import mappa.core.content.ContentRegistry;
import mappa.core.content.RegionDefinition;
import mappa.core.save.ItemCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Mappa Adventure save contains and how it is restored.
 * Format 1: player (name, level, exp, health, gold), location, equipment, inventory, progress.
 */
final class AdventureSave {
    static final String MODE = "adventure";
    static final int FORMAT = 1;

    private AdventureSave() {
    }

    static String summary(AdventureGame game) {
        AdventurePlayer p = game.player();
        return p.name() + " - Level " + p.level() + " - " + game.location().name() + (game.hasWon() ? " - Boss beaten" : "");
    }

    static JsonObject write(AdventureGame game) {
        AdventurePlayer p = game.player();
        JsonObject player = new JsonObject();
        player.addProperty("name", p.name());
        player.addProperty("level", p.level());
        player.addProperty("exp", p.progression().exp());
        player.addProperty("health", p.health().current());
        player.addProperty("gold", p.wallet().gold());

        JsonObject progress = new JsonObject();
        progress.addProperty("chestCountdown", game.chests().countdown());
        progress.addProperty("hasWon", game.hasWon());

        JsonObject data = new JsonObject();
        data.add("player", player);
        data.addProperty("location", game.location().id());
        data.add("equipment", ItemCodec.encodeEquipment(p.equipment()));
        data.add("inventory", ItemCodec.encodeInventory(p.inventory()));
        data.add("progress", progress);
        return data;
    }

    /** Restores a save into {@code game}. Returns warnings about anything that couldn't be restored. */
    static List<String> read(JsonObject data, AdventureGame game, ContentRegistry content, AdventureConfig config) {
        List<String> warnings = new ArrayList<>();
        JsonObject pj = data.getAsJsonObject("player");
        AdventurePlayer p = AdventurePlayer.blank(pj.get("name").getAsString(), config, content);
        p.restoreProgress(intOr(pj, "level", 0), intOr(pj, "exp", 0));
        ItemCodec.decodeEquipment(data.getAsJsonObject("equipment"), p.equipment(), p.inventory(), p.level(),
                content, warnings);
        ItemCodec.decodeInventory(data.getAsJsonArray("inventory"), p.inventory(), content, warnings);
        p.wallet().add(Math.max(0, pj.has("gold") ? pj.get("gold").getAsLong() : 0));
        int hp = intOr(pj, "health", p.health().max());
        p.health().set(hp <= 0 ? p.health().max() : hp);

        RegionDefinition location = game.regions().start();
        String regionId = data.has("location") ? data.get("location").getAsString() : "";
        try {
            RegionDefinition saved = content.region(regionId);
            if (saved.levelReq() <= p.level()) {
                location = saved;
            } else {
                warnings.add("You can't be in " + saved.name() + " at your level any more; you're back at "
                        + location.name() + ".");
            }
        } catch (RuntimeException e) {
            warnings.add("Region '" + regionId + "' no longer exists; you're back at " + location.name() + ".");
        }

        JsonObject progress = data.has("progress") ? data.getAsJsonObject("progress") : new JsonObject();
        game.restore(p, location, progress.has("hasWon") && progress.get("hasWon").getAsBoolean());
        if (progress.has("chestCountdown")) {
            game.chests().setCountdown(progress.get("chestCountdown").getAsInt());
        }
        return warnings;
    }

    private static int intOr(JsonObject o, String field, int def) {
        return o.has(field) ? o.get(field).getAsInt() : def;
    }
}
