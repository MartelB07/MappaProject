package mappa.core.items;

import mappa.core.stats.StatType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable item template loaded from JSON. Never put a definition in an inventory
 * directly — create an {@link ItemInstance}.
 *
 * @param fixedRarity rarity id that drops of this item always have, or null to roll
 */
public record ItemDefinition(String id, String name, ItemSlot slot, ClassTag classTag, int levelReq, int price,
                             Map<StatType, Integer> stats, ItemEffect effect, Set<String> tags,
                             String fixedRarity) {

    public ItemDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slot, "slot");
        classTag = classTag == null ? ClassTag.ANY : classTag;
        stats = stats == null || stats.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(stats));
        effect = effect == null ? ItemEffect.NONE : effect;
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    public int stat(StatType type) {
        return stats.getOrDefault(type, 0);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
