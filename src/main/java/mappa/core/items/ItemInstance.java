package mappa.core.items;

import mappa.core.stats.StatModifier;
import mappa.core.stats.StatType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One concrete item a player owns: a definition plus an optional rolled rarity and
 * stat multiplier. Every instance has its own id, so two copies never share state.
 */
public final class ItemInstance {
    private final String instanceId;
    private final ItemDefinition definition;
    private final Rarity rarity;
    private final double multiplier;

    private ItemInstance(String instanceId, ItemDefinition definition, Rarity rarity, double multiplier) {
        this.instanceId = Objects.requireNonNull(instanceId);
        this.definition = Objects.requireNonNull(definition);
        this.rarity = rarity;
        if (multiplier <= 0) {
            throw new IllegalArgumentException("multiplier must be > 0");
        }
        this.multiplier = multiplier;
    }

    /** A plain instance with base stats and no rarity. */
    public static ItemInstance of(ItemDefinition definition) {
        return new ItemInstance(UUID.randomUUID().toString(), definition, null, 1.0);
    }

    public static ItemInstance rolled(ItemDefinition definition, Rarity rarity, double multiplier) {
        return new ItemInstance(UUID.randomUUID().toString(), definition, rarity, multiplier);
    }

    /** Recreates an instance with a known id (save/load). */
    public static ItemInstance restore(String instanceId, ItemDefinition definition, Rarity rarity, double multiplier) {
        return new ItemInstance(instanceId, definition, rarity, multiplier);
    }

    public String instanceId() {
        return instanceId;
    }

    public ItemDefinition definition() {
        return definition;
    }

    public String definitionId() {
        return definition.id();
    }

    public String name() {
        return definition.name();
    }

    public ItemSlot slot() {
        return definition.slot();
    }

    public ClassTag classTag() {
        return definition.classTag();
    }

    public int levelReq() {
        return definition.levelReq();
    }

    /** Null when the item has no rarity. */
    public Rarity rarity() {
        return rarity;
    }

    public double multiplier() {
        return multiplier;
    }

    /** Stackable items with no rarity roll can share a stack. */
    public boolean isStackable() {
        return definition.slot().isStackable() && rarity == null;
    }

    public int stat(StatType type) {
        return scale(definition.stat(type));
    }

    public Map<StatType, Integer> stats() {
        EnumMap<StatType, Integer> out = new EnumMap<>(StatType.class);
        definition.stats().forEach((k, v) -> out.put(k, scale(v)));
        return out;
    }

    public int healAmount() {
        return scale(definition.effect().heal());
    }

    public int effectDamage() {
        return scale(definition.effect().damage());
    }

    public ItemEffect effect() {
        return definition.effect();
    }

    /** Value when bought from a shop. */
    public int price() {
        return definition.price();
    }

    /** Whole gold received when sold: {@code percent} of price, scaled by rarity. */
    public int sellValue(int percent) {
        return (int) Math.floor(definition.price() * multiplier * percent / 100.0);
    }

    /** Stat modifiers this item gives when equipped (source id = instance id). */
    public List<StatModifier> modifiers() {
        List<StatModifier> mods = new ArrayList<>();
        definition.stats().forEach((type, value) -> {
            int scaled = scale(value);
            if (scaled != 0) {
                mods.add(StatModifier.flat(type, scaled, instanceId));
            }
        });
        return mods;
    }

    /** "[Level 5] Sword", colored by rarity if it has one. */
    public String displayName() {
        String text = "[Level " + levelReq() + "] " + (rarity != null ? rarity.displayName() + " " : "") + name();
        return rarity != null ? rarity.colorize(text) : text;
    }

    /** Short stat summary such as "H +20  D +4". */
    public String statSummary() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<StatType, Integer> e : stats().entrySet()) {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(shortName(e.getKey())).append(" +").append(e.getValue());
        }
        if (healAmount() > 0) {
            sb.append(sb.length() > 0 ? "  " : "").append("Heals ").append(healAmount());
        }
        if (effectDamage() > 0) {
            sb.append(sb.length() > 0 ? "  " : "").append("Dmg ").append(effectDamage());
            if (effect().target() == ItemEffect.Target.ALL) {
                sb.append(" (all)");
            }
        }
        return sb.toString();
    }

    private static String shortName(StatType t) {
        return switch (t) {
            case MAX_HEALTH -> "HP";
            case DAMAGE -> "DMG";
            case WAR_DAMAGE -> "War";
            case MAGE_DAMAGE -> "Mage";
            case DEFENSE -> "Def";
            case CRIT_CHANCE -> "Crit%";
            case DODGE_CHANCE -> "Dodge%";
            default -> t.name();
        };
    }

    private int scale(int value) {
        return (int) Math.round(value * multiplier);
    }

    @Override
    public String toString() {
        return name() + "#" + instanceId.substring(0, 8);
    }
}
