package mappa.core.random;

import java.util.Random;

public class SeededRng implements Rng {
    private final Random random;

    public SeededRng() {
        this.random = new Random();
    }

    public SeededRng(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    @Override
    public double nextDouble() {
        return random.nextDouble();
    }
}
