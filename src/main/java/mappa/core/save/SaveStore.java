package mappa.core.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Save slots for one game mode: {@code <root>/<mode>/slot<N>.json}.
 *
 * <p>Every file is an envelope {@code {format, mode, savedAt, summary, data}}; only {@code data}
 * is mode-specific. Writes go to a temporary file first and are then moved into place,
 * so a crash mid-save never leaves a half-written slot.</p>
 */
public class SaveStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path dir;
    private final String mode;
    private final int slots;
    private final int format;
    private final Clock clock;
    private final Map<Integer, UnaryOperator<JsonObject>> migrations = new HashMap<>();

    /**
     * @param root   the saves folder shared by all modes
     * @param mode   mode id, used as the sub-folder name (e.g. "adventure")
     * @param format current save format version for this mode (start at 1)
     */
    public SaveStore(Path root, String mode, int slots, int format, Clock clock) {
        if (slots < 1) {
            throw new IllegalArgumentException("slots must be >= 1");
        }
        this.dir = root.resolve(mode);
        this.mode = mode;
        this.slots = slots;
        this.format = format;
        this.clock = clock;
    }

    public SaveStore(Path root, String mode, int slots, int format) {
        this(root, mode, slots, format, Clock.systemDefaultZone());
    }

    /**
     * Registers an upgrade from format {@code from} to {@code from + 1}. Applied in order when an
     * older file is loaded, e.g. {@code withMigration(1, data -> { data.addProperty("newField", 0); return data; })}.
     */
    public SaveStore withMigration(int from, UnaryOperator<JsonObject> upgrade) {
        migrations.put(from, upgrade);
        return this;
    }

    public String mode() {
        return mode;
    }

    public int slotCount() {
        return slots;
    }

    public Path file(int slot) {
        checkSlot(slot);
        return dir.resolve("slot" + slot + ".json");
    }

    public boolean exists(int slot) {
        return Files.isRegularFile(file(slot));
    }

    public boolean anyExist() {
        for (int s = 1; s <= slots; s++) {
            if (exists(s)) {
                return true;
            }
        }
        return false;
    }

    /** Summary for the menu; empty if the slot has no file. Unreadable files still get a summary. */
    public Optional<SaveSummary> summary(int slot) {
        if (!exists(slot)) {
            return Optional.empty();
        }
        try {
            JsonObject env = readEnvelope(slot);
            String text = env.has("summary") ? env.get("summary").getAsString() : "(no description)";
            String at = env.has("savedAt") ? env.get("savedAt").getAsString() : "";
            int fileFormat = env.has("format") ? env.get("format").getAsInt() : 0;
            if (fileFormat > format) {
                return Optional.of(new SaveSummary(slot, text + " [made by a newer version]", at, false));
            }
            return Optional.of(new SaveSummary(slot, text, at, true));
        } catch (SaveException e) {
            return Optional.of(new SaveSummary(slot, "(unreadable save)", "", false));
        }
    }

    /** Loads a slot's {@code data} object, upgrading older formats. */
    public JsonObject load(int slot) {
        if (!exists(slot)) {
            throw new SaveException("Slot " + slot + " is empty.");
        }
        JsonObject env = readEnvelope(slot);
        String fileMode = env.has("mode") ? env.get("mode").getAsString() : "";
        if (!mode.equals(fileMode)) {
            throw new SaveException("Slot " + slot + " holds a '" + fileMode + "' save, not '" + mode + "'.");
        }
        int fileFormat = env.has("format") ? env.get("format").getAsInt() : 0;
        if (fileFormat > format) {
            throw new SaveException("Slot " + slot + " was made by a newer version of the game (format "
                    + fileFormat + ", this version reads up to " + format + ").");
        }
        if (!env.has("data") || !env.get("data").isJsonObject()) {
            throw new SaveException("Slot " + slot + " has no game data.");
        }
        JsonObject data = env.getAsJsonObject("data");
        for (int v = fileFormat; v < format; v++) {
            UnaryOperator<JsonObject> step = migrations.get(v);
            if (step == null) {
                throw new SaveException("Slot " + slot + " uses old format " + v + " and no upgrade is available.");
            }
            data = step.apply(data);
        }
        return data;
    }

    /** Writes {@code data} to a slot (replacing it) with the given menu summary. */
    public void save(int slot, String summary, JsonObject data) {
        JsonObject env = new JsonObject();
        env.addProperty("format", format);
        env.addProperty("mode", mode);
        env.addProperty("savedAt", LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        env.addProperty("summary", summary);
        env.add("data", data);
        Path target = file(slot);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(dir);
            Files.writeString(tmp, GSON.toJson(env) + System.lineSeparator(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new SaveException("Could not save to slot " + slot + ": " + e.getMessage(), e);
        }
    }

    /** Deletes a slot. Returns true if a file was removed. */
    public boolean delete(int slot) {
        try {
            return Files.deleteIfExists(file(slot));
        } catch (IOException e) {
            throw new SaveException("Could not delete slot " + slot + ": " + e.getMessage(), e);
        }
    }

    private JsonObject readEnvelope(int slot) {
        try {
            JsonElement el = JsonParser.parseString(Files.readString(file(slot), StandardCharsets.UTF_8));
            if (!el.isJsonObject()) {
                throw new SaveException("Slot " + slot + " is not a save file.");
            }
            return el.getAsJsonObject();
        } catch (IOException e) {
            throw new SaveException("Could not read slot " + slot + ": " + e.getMessage(), e);
        } catch (JsonParseException | IllegalStateException e) {
            throw new SaveException("Slot " + slot + " is damaged and can't be read.", e);
        }
    }

    private void checkSlot(int slot) {
        if (slot < 1 || slot > slots) {
            throw new IllegalArgumentException("Slot must be 1.." + slots + ", was " + slot);
        }
    }
}
