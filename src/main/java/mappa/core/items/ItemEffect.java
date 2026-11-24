package mappa.core.items;

/**
 * What a consumable or spell does when used.
 *
 * @param heal     health restored (0 for none)
 * @param damage   damage dealt (0 for none)
 * @param target   who a damaging effect hits
 * @param cooldown turns before it can be used again (0 = every turn)
 */
public record ItemEffect(int heal, int damage, Target target, int cooldown) {
    public enum Target { SINGLE, ALL, SELF }

    public static final ItemEffect NONE = new ItemEffect(0, 0, Target.SELF, 0);

    public ItemEffect {
        if (target == null) {
            target = damage > 0 ? Target.SINGLE : Target.SELF;
        }
    }

    public boolean heals() {
        return heal > 0;
    }

    public boolean damages() {
        return damage > 0;
    }
}
