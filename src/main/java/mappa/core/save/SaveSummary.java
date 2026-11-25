package mappa.core.save;

/**
 * What the save menu shows for one slot.
 *
 * @param text    e.g. "Hero - Level 7 - Old Town" (or a reason the file can't be read)
 * @param savedAt ISO time the slot was saved, or "" if unknown
 * @param readable false if the file exists but can't be loaded
 */
public record SaveSummary(int slot, String text, String savedAt, boolean readable) {

    /** "Hero - Level 7 - Old Town (saved 2026-11-24 19:40)". */
    public String describe() {
        String when = savedAt.length() >= 16 ? savedAt.substring(0, 10) + " " + savedAt.substring(11, 16) : savedAt;
        return text + (when.isEmpty() ? "" : " (saved " + when + ")");
    }
}
