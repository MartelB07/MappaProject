package mappa.core.progression;

/** Outcome of {@link Progression#addExp(int)}. */
public record LevelUpResult(int fromLevel, int toLevel, boolean reachedMax) {
    public int levelsGained() {
        return toLevel - fromLevel;
    }

    public boolean leveledUp() {
        return toLevel > fromLevel;
    }
}
