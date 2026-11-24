package mappa.adventure;

import mappa.core.content.ContentRegistry;
import mappa.core.io.Ansi;
import mappa.core.io.GameIO;
import mappa.core.io.Menu;
import mappa.core.items.ItemDefinition;
import mappa.core.items.ItemSlot;
import mappa.core.mobs.Mob;
import mappa.core.random.Rng;

import java.util.ArrayList;
import java.util.List;

/**
 * One fight. Every player action (attack, heal, escape attempt) takes a turn and the
 * monster answers. Returns an outcome; death/revive is decided by the game loop.
 */
public class AdventureCombat {
    public enum Outcome { WON, FLED, DIED }

    private final AdventurePlayer player;
    private final Mob mob;
    private final AdventureConfig config;
    private final ContentRegistry content;
    private final Rng rng;
    private final GameIO io;

    public AdventureCombat(AdventurePlayer player, Mob mob, AdventureConfig config, ContentRegistry content,
                           Rng rng, GameIO io) {
        this.player = player;
        this.mob = mob;
        this.config = config;
        this.content = content;
        this.rng = rng;
        this.io = io;
    }

    public Mob mob() {
        return mob;
    }

    /** Interactive loop. */
    public Outcome fight(Menu menu) {
        io.println();
        io.println("You've encountered a " + mob.label() + (mob.isBoss() ? Ansi.color(" (BOSS)", Ansi.Color.RED) : ""));
        while (true) {
            io.println();
            io.println(" " + mob.label() + "  Health: " + Ansi.health(mob.health(), mob.maxHealth()));
            io.println(" [Level " + player.level() + "] " + player.name() + "  Health: "
                    + Ansi.health(player.health().current(), player.health().max()));
            int c = menu.choose("", null, "Attack", "Heal", "Escape");
            Outcome o = switch (c) {
                case 1 -> attackTurn();
                case 2 -> healMenu(menu);
                case 3 -> escapeTurn();
                default -> null;
            };
            if (o != null) {
                return o;
            }
        }
    }

    // ---- turn actions (public for tests) ----

    /** Player attacks, then the monster answers if it survived. Null = fight continues. */
    public Outcome attackTurn() {
        int dmg = roll(player.damage());
        boolean crit = rng.chance(config.critChancePercent() / 100.0);
        if (crit) {
            dmg *= config.critMultiplier();
        }
        if (rng.chance(mob.dodgeChance() / 100.0)) {
            io.println(mob.name() + " dodged the attack.");
        } else {
            int dealt = mob.takeDamage(dmg);
            io.println((crit ? "CRITICAL: " : "") + mob.name() + " took " + dealt + " damage.");
        }
        if (!mob.isAlive()) {
            return Outcome.WON;
        }
        return monsterTurn();
    }

    /**
     * Drinks up to {@code requested} potions of one kind, never more than needed to reach
     * full health. Uses a turn (the monster answers) only if a potion was drunk.
     */
    public Outcome healTurn(ItemDefinition potion, int requested) {
        int used = drinkPotions(player, potion, requested, io);
        if (used == 0) {
            return null;
        }
        return monsterTurn();
    }

    /** Shared potion logic. Returns how many potions were used. */
    public static int drinkPotions(AdventurePlayer player, ItemDefinition potion, int requested, GameIO io) {
        int per = potion.effect().heal();
        int missing = player.health().max() - player.health().current();
        int owned = player.inventory().count(potion.id());
        if (owned == 0) {
            io.println("You don't have any " + potion.name() + ".");
            return 0;
        }
        if (missing <= 0) {
            io.println("You're already at full health.");
            return 0;
        }
        int needed = (missing + per - 1) / per;
        int use = Math.min(Math.min(requested, needed), owned);
        if (use < requested) {
            io.println("Only using " + use + " (that's all you " + (use == owned ? "have" : "need") + ").");
        }
        player.inventory().removeByDefinition(potion.id(), use);
        int healed = player.health().heal(use * per);
        io.println("You used " + potion.name() + " x" + use + " and received +" + healed + " health.");
        return use;
    }

    public Outcome escapeTurn() {
        if (rng.chance(config.escapeChancePercent() / 100.0)) {
            io.println("You escaped!");
            return Outcome.FLED;
        }
        io.println("You failed to escape.");
        return monsterTurn();
    }

    private Outcome monsterTurn() {
        int dmg = roll(mob.damage());
        boolean crit = rng.chance(config.critChancePercent() / 100.0);
        if (crit) {
            dmg *= config.critMultiplier();
        }
        dmg = Math.max(1, dmg - player.defense());
        int taken = player.health().damage(dmg);
        io.println((crit ? "CRITICAL: " : "") + player.name() + " took " + taken + " damage.");
        return player.health().isAlive() ? null : Outcome.DIED;
    }

    private Outcome healMenu(Menu menu) {
        List<ItemDefinition> potions = new ArrayList<>();
        for (ItemDefinition d : content.itemsWithTag(config.potionTag())) {
            if (d.slot() == ItemSlot.CONSUMABLE) {
                potions.add(d);
            }
        }
        List<String> labels = new ArrayList<>();
        for (ItemDefinition d : potions) {
            labels.add("[Level " + d.levelReq() + "] " + d.name() + " (+" + d.effect().heal() + " health)  You have: "
                    + player.inventory().count(d.id()));
        }
        int pick = menu.chooseFrom("Which potion do you want to use?", labels, "cancel");
        if (pick == Menu.CANCEL) {
            return null;
        }
        ItemDefinition potion = potions.get(pick - 1);
        if (player.level() < potion.levelReq()) {
            io.println("You need to be level " + potion.levelReq() + " to use this potion.");
            return null;
        }
        int amount = menu.readInt("How many do you want to use? (0 to cancel): ", 1, 99, true);
        return amount == Menu.CANCEL ? null : healTurn(potion, amount);
    }

    private int roll(int base) {
        int min = (int) (base * config.varianceMin());
        int max = Math.max(min, (int) (base * config.varianceMax()));
        return Math.max(1, rng.between(min, max));
    }
}
