package mappa.core.items;

import mappa.core.io.Ansi;

/**
 * A rarity tier loaded from data/common/rarities.json.
 *
 * @param rank          ordering, 0 = most common
 * @param weight        relative drop weight
 * @param minMultiplier inclusive stat multiplier lower bound
 * @param maxMultiplier exclusive stat multiplier upper bound
 */
public record Rarity(String id, String displayName, Ansi.Color color, int rank, int weight,
                     double minMultiplier, double maxMultiplier) {

    public String colorize(String text) {
        return Ansi.color(text, color);
    }
}
