package mappa.core.random;

import java.util.List;
import java.util.function.ToIntFunction;

/** Source of randomness. Game code never calls Math.random() directly. */
public interface Rng {

    /** Uniform int in [0, bound). */
    int nextInt(int bound);

    /** Uniform double in [0, 1). */
    double nextDouble();

    /** Uniform int in [min, max] (inclusive). */
    default int between(int min, int max) {
        if (max < min) {
            throw new IllegalArgumentException("max < min: " + min + ".." + max);
        }
        return min + nextInt(max - min + 1);
    }

    /** Uniform double in [min, max). */
    default double between(double min, double max) {
        return min + nextDouble() * (max - min);
    }

    /** True with probability p (0..1). */
    default boolean chance(double p) {
        return nextDouble() < p;
    }

    default <T> T pick(List<T> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Cannot pick from an empty list");
        }
        return items.get(nextInt(items.size()));
    }

    /** Picks an item with probability proportional to its weight (weights must be >= 0, sum > 0). */
    default <T> T weighted(List<T> items, ToIntFunction<T> weight) {
        int total = 0;
        for (T t : items) {
            total += Math.max(0, weight.applyAsInt(t));
        }
        if (total <= 0) {
            throw new IllegalArgumentException("Total weight must be positive");
        }
        int roll = nextInt(total);
        for (T t : items) {
            roll -= Math.max(0, weight.applyAsInt(t));
            if (roll < 0) {
                return t;
            }
        }
        throw new IllegalStateException("unreachable");
    }
}
