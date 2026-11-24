package mappa.core.stats;

/**
 * Current HP tracked against a Stats object's MAX_HEALTH. Current HP is clamped
 * whenever max HP changes, so removing gear can never kill the player.
 */
public class Health {
    private final Stats stats;
    private int current;

    public Health(Stats stats) {
        this.stats = stats;
        this.current = max();
    }

    public int max() {
        return Math.max(1, stats.get(StatType.MAX_HEALTH));
    }

    public int current() {
        return current;
    }

    public boolean isAlive() {
        return current > 0;
    }

    /** Applies damage; returns the damage actually taken. */
    public int damage(int amount) {
        int taken = Math.max(0, Math.min(amount, current));
        current -= taken;
        return taken;
    }

    /** Heals up to max; returns the amount actually healed. */
    public int heal(int amount) {
        int healed = Math.max(0, Math.min(amount, max() - current));
        current += healed;
        return healed;
    }

    public void fill() {
        current = max();
    }

    /**
     * Call after max health changed. Gaining max HP also grants the difference;
     * losing max HP clamps current HP to [1, max] if the player was alive.
     */
    public void onMaxChanged(int oldMax) {
        boolean wasAlive = current > 0;
        int newMax = max();
        if (newMax > oldMax && wasAlive) {
            current += newMax - oldMax;
        }
        if (current > newMax) {
            current = newMax;
        }
        if (wasAlive && current < 1) {
            current = 1;
        }
    }

    public void set(int value) {
        current = Math.max(0, Math.min(value, max()));
    }
}
