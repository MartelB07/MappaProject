package mappa.core.progression;

/** A player's level and EXP on a {@link LevelCurve}. */
public class Progression {
    private final LevelCurve curve;
    private int level;
    private int exp;
    private boolean announcedMax;

    public Progression(LevelCurve curve) {
        this(curve, 0);
    }

    public Progression(LevelCurve curve, int startLevel) {
        this.curve = curve;
        this.level = Math.max(0, Math.min(startLevel, curve.maxLevel()));
    }

    public int level() {
        return level;
    }

    public int exp() {
        return exp;
    }

    public boolean isMaxLevel() {
        return level >= curve.maxLevel();
    }

    /** EXP needed for the next level, or 0 at max level. */
    public int expForNext() {
        return isMaxLevel() ? 0 : curve.expToReach(level + 1);
    }

    public LevelCurve curve() {
        return curve;
    }

    /**
     * Adds EXP and applies every level-up it pays for (possibly several).
     * EXP stops accumulating at the max level.
     */
    public LevelUpResult addExp(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("EXP cannot be negative");
        }
        int from = level;
        if (!isMaxLevel()) {
            exp += amount;
            while (!isMaxLevel() && exp >= curve.expToReach(level + 1)) {
                exp -= curve.expToReach(level + 1);
                level++;
            }
            if (isMaxLevel()) {
                exp = 0;
            }
        }
        boolean justMaxed = isMaxLevel() && !announcedMax && level > from;
        if (justMaxed) {
            announcedMax = true;
        }
        return new LevelUpResult(from, level, justMaxed);
    }

    /** Restores saved state (used by save/load and tests). */
    public void restore(int level, int exp) {
        this.level = Math.max(0, Math.min(level, curve.maxLevel()));
        this.exp = isMaxLevel() ? 0 : Math.max(0, exp);
        this.announcedMax = isMaxLevel();
    }
}
