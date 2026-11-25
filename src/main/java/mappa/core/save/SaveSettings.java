package mappa.core.save;

import com.google.gson.JsonObject;
import mappa.core.content.ContentException;

/**
 * The {@code "saves"} block of a mode's config, e.g. {@code "saves": { "slots": 3, "deleteOnDeath": false }}.
 *
 * @param slots         number of save slots for the mode (1..9)
 * @param deleteOnDeath whether the slot is deleted when the character dies for good
 */
public record SaveSettings(int slots, boolean deleteOnDeath) {
    public static final SaveSettings DEFAULT = new SaveSettings(3, false);
    public static final int MAX_SLOTS = 9;

    public SaveSettings {
        if (slots < 1 || slots > MAX_SLOTS) {
            throw new ContentException("saves.slots must be 1.." + MAX_SLOTS + ", was " + slots);
        }
    }

    /** Reads the "saves" block from a mode config; missing fields use the defaults. */
    public static SaveSettings from(JsonObject modeConfig) {
        if (modeConfig == null || !modeConfig.has("saves")) {
            return DEFAULT;
        }
        JsonObject s = modeConfig.getAsJsonObject("saves");
        int slots = s.has("slots") ? s.get("slots").getAsInt() : DEFAULT.slots();
        boolean delete = s.has("deleteOnDeath") ? s.get("deleteOnDeath").getAsBoolean() : DEFAULT.deleteOnDeath();
        return new SaveSettings(slots, delete);
    }
}
