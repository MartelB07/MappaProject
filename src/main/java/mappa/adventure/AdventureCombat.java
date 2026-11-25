package mappa.adventure;

import mappa.core.content.ContentRegistry;
import mappa.core.io.Ansi;
import mappa.core.io.GameIO;
import mappa.core.io.Menu;
import mappa.core.items.ItemDefinition;
import mappa.core.items.ItemSlot;
import mappa.core.mobs.Mob;
import mappa.core.random.Rng;
import mappa.core.ui.Tone;
import mappa.core.ui.Ui;

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
    private final Ui ui;

    public AdventureCombat(AdventurePlayer player, Mob mob, AdventureConfig config, ContentRegistry content,
                           Rng rng, GameIO io) {
        this.player = player;
        this.mob = mob;
        this.config = config;
        this.content = content;
        this.rng = rng;
        this.ui = new Ui(io, AdventureGame.ACCENT);
    }

    public Mob mob() {
        return mob;
    }

    /** Interactive loop. */
    public Outcome fight(Menu menu) {
        ui.banner(mob.isBoss() ? Tone.DANGER : Tone.WARNING,
                (mob.isBoss() ? "BOSS FIGHT!  " : "") + "A wild " + mob.name() + " appears!",
                "Level " + mob.level() + "   Health " + mob.maxHealth() + "   Damage ~" + mob.damage());
        while (true) {
            ui.header(ui.glyphs().swords() + " Battle",
                    padName(mob.label()) + "  " + ui.bar(mob.health(), mob.maxHealth(), 14),
                    padName("[Level " + player.level() + "] " + player.name()) + "  "
                            + ui.bar(player.health().current(), player.health().max(), 14));
            int c = menu.choose("Your move:", null, "Attack", "Heal", "Escape");
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

    /** Pads both fighters' names to the same width so their health bars line up. */
    private String padName(String s) {
        int width = Math.max(mob.label().length(), ("[Level " + player.level() + "] " + player.name()).length());
        return s + " ".repeat(Math.max(0, width - s.length()));
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
            ui.muted(mob.name() + " dodged the attack.");
        } else {
            int dealt = mob.takeDamage(dmg);
            if (crit) {
                ui.reward("CRITICAL HIT! " + mob.name() + " took " + dealt + " damage.");
            } else {
                ui.success(mob.name() + " took " + dealt + " damage.");
            }
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
        int used = drinkPotions(player, potion, requested, ui.io());
        if (used == 0) {
            return null;
        }
        return monsterTurn();
    }

    /** Shared potion logic. Returns how many potions were used. */
    public static int drinkPotions(AdventurePlayer player, ItemDefinition potion, int requested, GameIO io) {
        Ui ui = new Ui(io, AdventureGame.ACCENT);
        int per = potion.effect().heal();
        int missing = player.health().max() - player.health().current();
        int owned = player.inventory().count(potion.id());
        if (owned == 0) {
            ui.warn("You don't have any " + potion.name() + ".");
            return 0;
        }
        if (missing <= 0) {
            ui.warn("You're already at full health.");
            return 0;
        }
        int needed = (missing + per - 1) / per;
        int use = Math.min(Math.min(requested, needed), owned);
        if (use < requested) {
            ui.muted("Only using " + use + " (that's all you " + (use == owned ? "have" : "need") + ").");
        }
        player.inventory().removeByDefinition(potion.id(), use);
        int healed = player.health().heal(use * per);
        ui.success("You used " + potion.name() + " x" + use + " and received +" + healed + " health.");
        int left = player.inventory().count(potion.id());
        if (left <= 3) {
            ui.warn(left == 0 ? "That was your last " + potion.name() + "." : "Only " + left + " " + potion.name()
                    + " left.");
        }
        return use;
    }

    public Outcome escapeTurn() {
        if (rng.chance(config.escapeChancePercent() / 100.0)) {
            ui.success("You escaped!");
            return Outcome.FLED;
        }
        ui.warn("You failed to escape.");
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
        ui.danger((crit ? "CRITICAL! " : "") + mob.name() + " hits you: " + player.name() + " took " + taken
                + " damage.");
        if (player.health().isAlive() && player.health().current() <= player.health().max() * 0.25) {
            ui.warn("Your health is low! Consider healing or escaping.");
        }
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
            int have = player.inventory().count(d.id());
            labels.add("[Level " + d.levelReq() + "] " + d.name() + " (+" + d.effect().heal() + " health)  "
                    + Ansi.color("You have: " + have, have == 0 ? Ansi.Color.GRAY : Ansi.Color.GREEN));
        }
        int pick = menu.chooseFrom("Which potion do you want to use?", labels, "cancel");
        if (pick == Menu.CANCEL) {
            return null;
        }
        ItemDefinition potion = potions.get(pick - 1);
        if (player.level() < potion.levelReq()) {
            ui.warn("You need to be level " + potion.levelReq() + " to use this potion.");
            return null;
        }
        int amount = menu.readInt("How many do you want to use? (0 to cancel)", 1, 99, true);
        return amount == Menu.CANCEL ? null : healTurn(potion, amount);
    }

    private int roll(int base) {
        int min = (int) (base * config.varianceMin());
        int max = Math.max(min, (int) (base * config.varianceMax()));
        return Math.max(1, rng.between(min, max));
    }
}
