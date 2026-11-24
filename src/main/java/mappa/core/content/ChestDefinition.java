package mappa.core.content;

import java.util.Set;

/**
 * A chest type from data/adventure/chests.json.
 *
 * @param kind      what the chest gives
 * @param weight    relative chance of finding this chest type
 * @param itemTags  ITEMS/CONSUMABLES: items with any of these tags can be given
 * @param amountMin CONSUMABLES: min count; GOLD: gold per chest level (min)
 * @param amountMax CONSUMABLES: max count; GOLD: gold per chest level (max)
 */
public record ChestDefinition(String id, String name, Kind kind, int weight, Set<String> itemTags,
                              int amountMin, int amountMax) {
    public enum Kind { ITEMS, CONSUMABLES, GOLD, MONSTER }

    public ChestDefinition {
        itemTags = Set.copyOf(itemTags);
    }
}
