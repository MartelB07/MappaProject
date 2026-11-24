package mappa.core.items;

import mappa.core.random.Rng;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Rolls a rarity (weighted) and a stat multiplier within that rarity's range. */
public final class RarityRoller {
    private final List<Rarity> rarities;

    public RarityRoller(Collection<Rarity> rarities) {
        this.rarities = new ArrayList<>(rarities);
        this.rarities.sort(Comparator.comparingInt(Rarity::rank));
    }

    /** Rolls a rarity among those whose id is in {@code allowed}. */
    public Rarity rollRarity(Rng rng, Set<String> allowed) {
        List<Rarity> pool = new ArrayList<>();
        for (Rarity r : rarities) {
            if (allowed.contains(r.id())) {
                pool.add(r);
            }
        }
        if (pool.isEmpty()) {
            throw new IllegalArgumentException("No rarities allowed from " + allowed);
        }
        return rng.weighted(pool, Rarity::weight);
    }

    public double rollMultiplier(Rng rng, Rarity rarity) {
        if (rarity.maxMultiplier() <= rarity.minMultiplier()) {
            return rarity.minMultiplier();
        }
        return rng.between(rarity.minMultiplier(), rarity.maxMultiplier());
    }

    /** Creates a rolled instance; items with a fixed rarity keep it. */
    public ItemInstance roll(Rng rng, ItemDefinition def, Set<String> allowed, Map<String, Rarity> byId) {
        Rarity rarity = def.fixedRarity() != null ? byId.get(def.fixedRarity()) : rollRarity(rng, allowed);
        if (rarity == null) {
            throw new IllegalStateException("Unknown fixed rarity " + def.fixedRarity() + " on " + def.id());
        }
        return ItemInstance.rolled(def, rarity, rollMultiplier(rng, rarity));
    }

    public List<Rarity> rarities() {
        return List.copyOf(rarities);
    }
}
