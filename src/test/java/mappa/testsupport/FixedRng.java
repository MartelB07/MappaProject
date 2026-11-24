package mappa.testsupport;

import mappa.core.random.Rng;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Deterministic Rng. Queued ints/doubles are returned in order; when a queue is empty
 * the defaults are used (nextInt → 0, nextDouble → {@link #defaultDouble}).
 * The default double of 0.999 makes every {@code chance(p)} with p < 0.999 false.
 */
public class FixedRng implements Rng {
    private final Deque<Integer> ints = new ArrayDeque<>();
    private final Deque<Double> doubles = new ArrayDeque<>();
    private double defaultDouble = 0.999;

    public static FixedRng neverLucky() {
        return new FixedRng();
    }

    /** Every chance() succeeds, every between() returns its minimum. */
    public static FixedRng alwaysLucky() {
        FixedRng r = new FixedRng();
        r.defaultDouble = 0.0;
        return r;
    }

    public FixedRng ints(int... values) {
        for (int v : values) {
            ints.add(v);
        }
        return this;
    }

    public FixedRng doubles(double... values) {
        for (double v : values) {
            doubles.add(v);
        }
        return this;
    }

    @Override
    public int nextInt(int bound) {
        int v = ints.isEmpty() ? 0 : ints.poll();
        return Math.max(0, Math.min(v, bound - 1));
    }

    @Override
    public double nextDouble() {
        return doubles.isEmpty() ? defaultDouble : doubles.poll();
    }
}
