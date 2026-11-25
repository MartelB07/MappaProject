package mappa.dungeons;

import mappa.core.content.ContentRegistry;
import mappa.core.inventory.Equipment;
import mappa.core.inventory.Inventory;
import mappa.core.inventory.Wallet;
import mappa.core.items.ClassTag;
import mappa.core.items.ItemInstance;
import mappa.core.items.ItemSlot;
import mappa.core.progression.LevelUpResult;
import mappa.core.progression.Progression;
import mappa.core.stats.Health;
import mappa.core.stats.StatModifier;
import mappa.core.stats.StatType;
import mappa.core.stats.Stats;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A Mappa Dungeons character: attributes, gear, two spell slots, and progress. */
public class DungeonPlayer {
    public enum Attribute { WAR, MAGE, HEALTH }

    public static final int SPELL_SLOTS = 2;
    private static final String ATTRIBUTE_SOURCE = "attributes";

    private final String name;
    private final DungeonsConfig config;
    private final Stats stats = new Stats();
    private final Health health;
    private final Progression progression;
    private final Equipment equipment;
    private final Inventory inventory;
    private final Wallet wallet;
    private final ItemInstance[] spells = new ItemInstance[SPELL_SLOTS];
    private final EnumMap<Attribute, Integer> points = new EnumMap<>(Attribute.class);
    private final Set<String> clearedDungeons = new HashSet<>();
    private int availablePoints;

    /** A new character with the configured starting items. */
    public DungeonPlayer(String name, DungeonsConfig config, ContentRegistry content) {
        this(name, config, content, true);
    }

    private DungeonPlayer(String name, DungeonsConfig config, ContentRegistry content, boolean starterKit) {
        this.name = name;
        this.config = config;
        stats.setBase(StatType.MAX_HEALTH, config.startingHealth());
        this.health = new Health(stats);
        this.progression = new Progression(content.levelCurve(config.levelCurve()));
        this.equipment = new Equipment(stats, health);
        this.inventory = new Inventory(config.inventorySize());
        this.wallet = new Wallet(starterKit ? config.startingGold() : 0);
        for (Attribute a : Attribute.values()) {
            points.put(a, 0);
        }
        if (starterKit) {
            for (String id : config.startingItems()) {
                inventory.add(ItemInstance.of(content.item(id)));
            }
        }
    }

    /** An empty character (no items, no gold) for loading a save into. */
    static DungeonPlayer blank(String name, DungeonsConfig config, ContentRegistry content) {
        return new DungeonPlayer(name, config, content, false);
    }

    /** Restores level and EXP from a save. */
    void restoreProgress(int level, int exp) {
        progression.restore(level, exp);
    }

    /**
     * Restores attribute points from a save. If the save claims more points than this level
     * can have earned (e.g. the level curve changed), the extra points are dropped.
     */
    void restorePoints(Map<Attribute, Integer> spent, int available) {
        int earned = level() * config.pointsPerLevel();
        int total = 0;
        for (Attribute a : Attribute.values()) {
            int v = Math.max(0, spent.getOrDefault(a, 0));
            v = Math.min(v, earned - total);
            points.put(a, v);
            total += v;
        }
        availablePoints = Math.max(0, Math.min(available, earned - total));
        applyAttributes();
        health.fill();
    }

    /** Puts a spell straight into a slot when loading (it doesn't come from the inventory). */
    boolean restoreSpell(int slot, ItemInstance spell) {
        if (slot < 0 || slot >= SPELL_SLOTS || spell.slot() != ItemSlot.SPELL || spell.levelReq() > level()) {
            return false;
        }
        spells[slot] = spell;
        return true;
    }

    // ------------------------------------------------------------------ basics

    public String name() {
        return name;
    }

    public Stats stats() {
        return stats;
    }

    public Health health() {
        return health;
    }

    public Progression progression() {
        return progression;
    }

    public int level() {
        return progression.level();
    }

    public Equipment equipment() {
        return equipment;
    }

    public Inventory inventory() {
        return inventory;
    }

    public Wallet wallet() {
        return wallet;
    }

    public DungeonsConfig config() {
        return config;
    }

    // ------------------------------------------------------------------ progression

    /** Adds EXP; each level gained grants attribute points and a full heal. */
    public LevelUpResult addExp(int amount) {
        LevelUpResult r = progression.addExp(amount);
        if (r.leveledUp()) {
            availablePoints += r.levelsGained() * config.pointsPerLevel();
            health.fill();
        }
        return r;
    }

    public boolean hasCleared(String dungeonId) {
        return clearedDungeons.contains(dungeonId);
    }

    public void markCleared(String dungeonId) {
        clearedDungeons.add(dungeonId);
    }

    public Set<String> clearedDungeons() {
        return Set.copyOf(clearedDungeons);
    }

    // ------------------------------------------------------------------ attributes

    public int availablePoints() {
        return availablePoints;
    }

    public int points(Attribute a) {
        return points.get(a);
    }

    public int spentPoints() {
        return points.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int resetCost() {
        return spentPoints() * config.resetCostPerPoint();
    }

    /** Spends points; rejects amounts below 1 or above what is available. */
    public boolean spendPoints(Attribute a, int amount) {
        if (amount < 1 || amount > availablePoints) {
            return false;
        }
        availablePoints -= amount;
        points.merge(a, amount, Integer::sum);
        applyAttributes();
        return true;
    }

    /**
     * Refunds every spent point for gold. Removes the stats those points gave and keeps
     * any unspent points. Returns false (and changes nothing) if the player can't pay.
     */
    public boolean resetPoints() {
        int spent = spentPoints();
        if (spent == 0 || !wallet.trySpend(resetCost())) {
            return false;
        }
        for (Attribute a : Attribute.values()) {
            points.put(a, 0);
        }
        availablePoints += spent;
        applyAttributes();
        return true;
    }

    private void applyAttributes() {
        int oldMax = health.max();
        stats.removeSource(ATTRIBUTE_SOURCE);
        int dpp = config.damagePerPoint();
        stats.addAll(ATTRIBUTE_SOURCE, List.of(
                StatModifier.flat(StatType.WAR_DAMAGE, points(Attribute.WAR) * dpp, ATTRIBUTE_SOURCE),
                StatModifier.flat(StatType.MAGE_DAMAGE, points(Attribute.MAGE) * dpp, ATTRIBUTE_SOURCE),
                StatModifier.flat(StatType.MAX_HEALTH, points(Attribute.HEALTH) * config.healthPerPoint(),
                        ATTRIBUTE_SOURCE)));
        health.onMaxChanged(oldMax);
    }

    // ------------------------------------------------------------------ damage

    public ItemInstance weapon() {
        return equipment.get(ItemSlot.WEAPON);
    }

    /** Melee damage: the equipped weapon's class damage stat. 0 with no weapon. */
    public int attackDamage() {
        ItemInstance w = weapon();
        if (w == null) {
            return 0;
        }
        StatType stat = w.classTag().damageStat();
        return stat == null ? 0 : stats.get(stat);
    }

    /** Spell damage: the spell's base damage plus attribute points in the spell's class. */
    public int spellDamage(ItemInstance spell) {
        int bonus = switch (spell.classTag()) {
            case WAR -> points(Attribute.WAR) * config.damagePerPoint();
            case MAGE -> points(Attribute.MAGE) * config.damagePerPoint();
            default -> 0;
        };
        return spell.effectDamage() > 0 ? spell.effectDamage() + bonus : 0;
    }

    public int defense() {
        return stats.get(StatType.DEFENSE);
    }

    // ------------------------------------------------------------------ gear & spells

    public enum EquipOutcome { OK, LEVEL_TOO_LOW, NOT_EQUIPPABLE, NOT_IN_INVENTORY, INVENTORY_FULL, BAD_SLOT }

    /** Equips gear from the inventory; the old piece goes back into the inventory. */
    public EquipOutcome equipGear(ItemInstance item) {
        if (!inventory.containsInstance(item)) {
            return EquipOutcome.NOT_IN_INVENTORY;
        }
        if (!item.slot().isGear()) {
            return EquipOutcome.NOT_EQUIPPABLE;
        }
        if (item.levelReq() > level()) {
            return EquipOutcome.LEVEL_TOO_LOW;
        }
        inventory.remove(item);
        Equipment.Result r = equipment.equip(item, level());
        if (r.previous() != null) {
            inventory.add(r.previous());
        }
        return EquipOutcome.OK;
    }

    public EquipOutcome unequipGear(ItemSlot slot) {
        if (equipment.get(slot) == null) {
            return EquipOutcome.BAD_SLOT;
        }
        if (inventory.isFull()) {
            return EquipOutcome.INVENTORY_FULL;
        }
        inventory.add(equipment.unequip(slot));
        return EquipOutcome.OK;
    }

    public ItemInstance spell(int slot) {
        return spells[slot];
    }

    /** Puts a spell from the inventory into slot 0 or 1; the old spell returns to the inventory. */
    public EquipOutcome equipSpell(ItemInstance spell, int slot) {
        if (slot < 0 || slot >= SPELL_SLOTS) {
            return EquipOutcome.BAD_SLOT;
        }
        if (!inventory.containsInstance(spell)) {
            return EquipOutcome.NOT_IN_INVENTORY;
        }
        if (spell.slot() != ItemSlot.SPELL) {
            return EquipOutcome.NOT_EQUIPPABLE;
        }
        if (spell.levelReq() > level()) {
            return EquipOutcome.LEVEL_TOO_LOW;
        }
        inventory.remove(spell);
        ItemInstance old = spells[slot];
        spells[slot] = spell;
        if (old != null) {
            inventory.add(old);
        }
        return EquipOutcome.OK;
    }

    public EquipOutcome unequipSpell(int slot) {
        if (slot < 0 || slot >= SPELL_SLOTS || spells[slot] == null) {
            return EquipOutcome.BAD_SLOT;
        }
        if (inventory.isFull()) {
            return EquipOutcome.INVENTORY_FULL;
        }
        inventory.add(spells[slot]);
        spells[slot] = null;
        return EquipOutcome.OK;
    }

    /** Equipped gear by slot (for display). */
    public Map<ItemSlot, ItemInstance> gear() {
        return equipment.all();
    }

    public static ClassTag classOf(Attribute a) {
        return switch (a) {
            case WAR -> ClassTag.WAR;
            case MAGE -> ClassTag.MAGE;
            case HEALTH -> ClassTag.GUARD;
        };
    }
}
