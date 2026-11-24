package mappa.core.inventory;

/** Whole-number gold that can never go negative. */
public class Wallet {
    private long gold;

    public Wallet() {
        this(0);
    }

    public Wallet(long startingGold) {
        if (startingGold < 0) {
            throw new IllegalArgumentException("Gold cannot be negative");
        }
        this.gold = startingGold;
    }

    public long gold() {
        return gold;
    }

    public void add(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Use trySpend to remove gold");
        }
        gold += amount;
    }

    public boolean canAfford(long amount) {
        return amount <= gold;
    }

    /** Spends the gold if there is enough; returns false and changes nothing otherwise. */
    public boolean trySpend(long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        if (amount > gold) {
            return false;
        }
        gold -= amount;
        return true;
    }
}
