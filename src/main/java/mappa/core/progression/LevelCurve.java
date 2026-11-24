package mappa.core.progression;

import java.util.Arrays;

/**
 * EXP needed per level. {@code expToReach(n)} is the EXP needed to go from level n-1
 * to level n (EXP resets to the remainder on each level up). Level 0 is the start.
 */
public final class LevelCurve {
    private final int[] thresholds;

    /** thresholds[0] is ignored (level 0 is free); thresholds.length - 1 is the max level. */
    public LevelCurve(int[] thresholds) {
        if (thresholds.length < 2) {
            throw new IllegalArgumentException("A level curve needs at least 2 entries");
        }
        for (int i = 1; i < thresholds.length; i++) {
            if (thresholds[i] <= 0) {
                throw new IllegalArgumentException("Threshold for level " + i + " must be > 0");
            }
        }
        this.thresholds = thresholds.clone();
    }

    /** Builds thresholds as round(base * growth^(level-1)) for levels 1..maxLevel. */
    public static LevelCurve formula(int maxLevel, double base, double growth) {
        int[] t = new int[maxLevel + 1];
        for (int level = 1; level <= maxLevel; level++) {
            t[level] = Math.max(1, (int) Math.round(base * Math.pow(growth, level - 1)));
            if (level > 1 && t[level] <= t[level - 1]) {
                t[level] = t[level - 1] + 1;
            }
        }
        return new LevelCurve(t);
    }

    public int maxLevel() {
        return thresholds.length - 1;
    }

    public int expToReach(int level) {
        if (level < 1 || level > maxLevel()) {
            throw new IllegalArgumentException("No threshold for level " + level);
        }
        return thresholds[level];
    }

    public int[] thresholds() {
        return thresholds.clone();
    }

    @Override
    public String toString() {
        return "LevelCurve" + Arrays.toString(thresholds);
    }
}
