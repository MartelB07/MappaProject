package mappa.adventure;

import mappa.core.content.ChestDefinition;
import mappa.core.content.ContentRegistry;
import mappa.core.content.RegionDefinition;
import mappa.core.inventory.ItemStack;
import mappa.core.io.GameIO;
import mappa.core.items.ItemDefinition;
import mappa.core.items.ItemInstance;
import mappa.core.items.ItemSlot;
import mappa.core.random.Rng;

import java.util.ArrayList;
import java.util.List;

/** Finding and opening chests, and key drops. */
public class ChestService {
    public enum Result { NOTHING_FOUND, NO_KEY, DECLINED, OPENED, MONSTER }

    /** A chest that was found: its type and level. */
    public record FoundChest(ChestDefinition chest, int level) {
    }

    private final ContentRegistry content;
    private final AdventureConfig config;
    private final Rng rng;
    private final GameIO io;
    private int countdown;

    public ChestService(ContentRegistry content, AdventureConfig config, Rng rng, GameIO io) {
        this.content = content;
        this.config = config;
        this.rng = rng;
        this.io = io;
        this.countdown = config.searchCountdownStart();
    }

    public int countdown() {
        return countdown;
    }

    public void setCountdown(int value) {
        countdown = value;
    }

    /** Called after every won fight: chests become findable sooner, and keys may drop. */
    public void onFightWon(AdventurePlayer player, int mobLevel) {
        countdown -= rng.between(config.countdownPerWinMin(), config.countdownPerWinMax());
        if (player.level() > 0 && rng.chance(config.keyDropChancePercent() / 100.0)) {
            int keyLevel = Math.min(10, rng.between(1, Math.max(1, mobLevel)));
            String id = "adv_key_" + keyLevel;
            if (content.hasItem(id)) {
                ItemInstance key = ItemInstance.of(content.item(id));
                if (player.inventory().add(key)) {
                    io.println("The monster dropped a [Level " + keyLevel + "] Key. Use it to open a chest of that level or lower.");
                } else {
                    io.println("The monster dropped a key, but your inventory is full.");
                }
            }
        }
    }

    /** Rolls a chest for the region, or null if none is found yet. */
    public FoundChest search(RegionDefinition region) {
        if (!region.hasChests() || countdown > 0) {
            return null;
        }
        List<ChestDefinition> chests = new ArrayList<>(content.chests());
        ChestDefinition type = rng.weighted(chests, ChestDefinition::weight);
        int level = rng.between(region.chestLevelMin(), region.chestLevelMax());
        return new FoundChest(type, level);
    }

    /** The lowest-level key in the inventory that opens a chest of this level, or null. */
    public ItemStack bestKey(AdventurePlayer player, int chestLevel) {
        ItemStack best = null;
        for (ItemStack s : player.inventory().ofSlot(ItemSlot.KEY)) {
            if (s.item().levelReq() >= chestLevel && (best == null || s.item().levelReq() < best.item().levelReq())) {
                best = s;
            }
        }
        return best;
    }

    /** Uses the key and gives the reward (MONSTER chests return MONSTER for the caller to fight). */
    public Result open(AdventurePlayer player, FoundChest found, ItemStack key) {
        player.inventory().remove(key.item());
        countdown = rng.between(config.countdownAfterOpenMin(), config.countdownAfterOpenMax());
        ChestDefinition chest = found.chest();
        io.println("You opened a [Level " + found.level() + "] " + chest.name() + ".");
        switch (chest.kind()) {
            case GOLD -> {
                int gold = rng.between(chest.amountMin() * found.level(), chest.amountMax() * found.level());
                player.wallet().add(gold);
                io.println("You received " + gold + " gold.");
            }
            case MONSTER -> {
                return Result.MONSTER;
            }
            case CONSUMABLES -> {
                ItemDefinition def = pickItem(chest, found.level());
                int amount = rng.between(chest.amountMin(), chest.amountMax());
                int added = player.inventory().add(ItemInstance.of(def), amount);
                io.println("You received " + def.name() + " x" + added + ".");
                if (added < amount) {
                    io.println("Your inventory is full; " + (amount - added) + " couldn't be collected.");
                }
            }
            case ITEMS -> {
                ItemDefinition def = pickItem(chest, found.level());
                ItemInstance item = ItemInstance.of(def);
                if (player.inventory().add(item)) {
                    io.println("You received " + item.displayName() + ". Open your inventory to equip it.");
                } else {
                    io.println("Your inventory is full. Could not collect " + def.name() + ".");
                }
            }
        }
        return Result.OPENED;
    }

    private ItemDefinition pickItem(ChestDefinition chest, int chestLevel) {
        List<ItemDefinition> pool = new ArrayList<>();
        for (ItemDefinition d : content.items()) {
            if (d.levelReq() <= chestLevel && chest.itemTags().stream().anyMatch(d::hasTag)) {
                pool.add(d);
            }
        }
        if (pool.isEmpty()) { // fall back to the lowest-level tagged item
            for (ItemDefinition d : content.items()) {
                if (chest.itemTags().stream().anyMatch(d::hasTag)
                        && (pool.isEmpty() || d.levelReq() < pool.get(0).levelReq())) {
                    pool.clear();
                    pool.add(d);
                }
            }
        }
        return rng.pick(pool);
    }
}
