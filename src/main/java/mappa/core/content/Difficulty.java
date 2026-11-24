package mappa.core.content;

import mappa.core.io.Ansi;

import java.util.Set;

/**
 * A difficulty tier from data/common/difficulties.json. Difficulty controls how strong
 * mobs are and which rarities can drop; how many items drop is set by the dungeon
 * run mode (Regular / Hardcore).
 *
 * @param mobScale        multiplier for mob health, damage and EXP
 * @param allowedRarities rarity ids that can drop at this difficulty
 */
public record Difficulty(String id, String displayName, Ansi.Color color, int rank, double mobScale,
                         Set<String> allowedRarities) {
    public Difficulty {
        allowedRarities = Set.copyOf(allowedRarities);
    }

    public String colored() {
        return Ansi.color(displayName, color);
    }
}
