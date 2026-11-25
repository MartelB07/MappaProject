package mappa.dungeons;

import mappa.core.content.ContentRegistry;
import mappa.core.content.Difficulty;
import mappa.core.content.DungeonDefinition;
import mappa.core.io.Ansi;
import mappa.core.io.GameIO;
import mappa.core.items.ItemEffect;
import mappa.core.items.ItemInstance;
import mappa.core.mobs.Mob;
import mappa.core.mobs.MobDefinition;
import mappa.core.mobs.MobFactory;
import mappa.core.progression.LevelUpResult;
import mappa.core.random.Rng;
import mappa.core.ui.Tone;
import mappa.core.ui.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The rules of one dungeon run: rooms (waves then a boss), a turn limit, mob attack
 * telegraphs, spell cooldowns, death handling per {@link RunMode}, and rewards.
 * Contains no menus — {@link DungeonRunScreen} drives it — so it can be unit tested.
 */
public class DungeonRun {
    public enum Status { IN_PROGRESS, CLEARED, DIED, OUT_OF_TURNS, ABANDONED }

    /** A mob in the current room plus its attack charge (0..attackEvery). */
    public static final class RoomMob {
        private final Mob mob;
        private int charge;

        RoomMob(Mob mob) {
            this.mob = mob;
        }

        public Mob mob() {
            return mob;
        }

        public int charge() {
            return charge;
        }

        public int attackEvery() {
            return mob.definition().attackEvery();
        }

        /** True if this mob attacks at the end of the next turn. */
        public boolean attacksNextTurn() {
            return mob.isAlive() && charge + 1 >= attackEvery();
        }

        /** Colored bar such as "--" that fills up until the mob attacks. */
        public String telegraph() {
            return telegraph(mappa.core.ui.Glyphs.current());
        }

        /**
         * Attack gauge, e.g. "attack [##.]": one cell per turn until the mob strikes.
         * Turns yellow as it fills and red (with a warning) the turn before it attacks.
         */
        public String telegraph(mappa.core.ui.Glyphs glyphs) {
            int cells = Math.max(1, attackEvery() - 1);
            int filled = Math.min(charge, cells);
            Ansi.Color c = attacksNextTurn() ? Ansi.Color.RED : filled > 0 ? Ansi.Color.YELLOW : Ansi.Color.GRAY;
            String gauge = Ansi.color("attack ", Ansi.Color.GRAY) + "["
                    + Ansi.color(glyphs.barFull().repeat(filled), c)
                    + Ansi.color(glyphs.barEmpty().repeat(cells - filled), Ansi.Color.GRAY) + "]";
            return attacksNextTurn() ? gauge + Ansi.bold(" ATTACKING NEXT TURN", Ansi.Color.RED) : gauge;
        }
    }

    private final ContentRegistry content;
    private final DungeonDefinition dungeon;
    private final Difficulty difficulty;
    private final DungeonDefinition.Settings settings;
    private final RunMode mode;
    private final DungeonPlayer player;
    private final Rng rng;
    private final Ui ui;

    private final List<RoomMob> mobs = new ArrayList<>();
    private final Map<String, Integer> lastCast = new HashMap<>();
    private final List<ItemInstance> rewards = new ArrayList<>();
    private final List<ItemInstance> rewardsLost = new ArrayList<>();

    private int turnsLeft;
    private int turnsUsed;
    private int room;
    private int deaths;
    private boolean dodging;
    private Status status = Status.IN_PROGRESS;

    public DungeonRun(ContentRegistry content, DungeonDefinition dungeon, Difficulty difficulty, RunMode mode,
                      DungeonPlayer player, Rng rng, GameIO io) {
        this.content = content;
        this.dungeon = dungeon;
        this.difficulty = difficulty;
        this.settings = dungeon.difficulties().get(difficulty.id());
        if (settings == null) {
            throw new IllegalArgumentException(dungeon.id() + " has no " + difficulty.id() + " difficulty");
        }
        this.mode = mode;
        this.player = player;
        this.rng = rng;
        this.ui = new Ui(io, DungeonsGame.ACCENT);
        this.turnsLeft = dungeon.turnLimit();
        player.health().fill();
        spawnRoom();
    }

    // ------------------------------------------------------------------ state

    public Status status() {
        return status;
    }

    public boolean inProgress() {
        return status == Status.IN_PROGRESS;
    }

    public int turnsLeft() {
        return turnsLeft;
    }

    public int deaths() {
        return deaths;
    }

    /** Lives remaining, or -1 if unlimited. */
    public int livesLeft() {
        return mode.unlimitedLives() ? -1 : Math.max(0, mode.lives() - deaths);
    }

    public int room() {
        return room;
    }

    public int roomCount() {
        return dungeon.waves().size() + 1;
    }

    public boolean isBossRoom() {
        return room == dungeon.waves().size();
    }

    public List<RoomMob> mobs() {
        return List.copyOf(mobs);
    }

    public List<RoomMob> aliveMobs() {
        return mobs.stream().filter(m -> m.mob.isAlive()).toList();
    }

    public boolean roomCleared() {
        return aliveMobs().isEmpty();
    }

    public boolean canMoveOn() {
        return inProgress() && roomCleared() && !isBossRoom();
    }

    public DungeonDefinition dungeon() {
        return dungeon;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public RunMode mode() {
        return mode;
    }

    public DungeonPlayer player() {
        return player;
    }

    public List<ItemInstance> rewards() {
        return List.copyOf(rewards);
    }

    public List<ItemInstance> rewardsLost() {
        return List.copyOf(rewardsLost);
    }

    /** Turns until the spell can be cast again (0 = ready). */
    public int cooldownRemaining(ItemInstance spell) {
        Integer last = lastCast.get(spell.instanceId());
        if (last == null) {
            return 0;
        }
        int ready = last + spell.effect().cooldown() + 1;
        return Math.max(0, ready - turnsUsed);
    }

    // ------------------------------------------------------------------ actions

    /** Melee attack on the n-th living mob (0-based). Returns false if nothing happened. */
    public boolean attack(int aliveIndex) {
        if (!inProgress()) {
            return false;
        }
        int damage = player.attackDamage();
        if (damage <= 0) {
            ui.warn("You have no weapon equipped.");
            return false;
        }
        List<RoomMob> alive = aliveMobs();
        if (aliveIndex < 0 || aliveIndex >= alive.size()) {
            return false;
        }
        RoomMob target = alive.get(aliveIndex);
        int dealt = target.mob.takeDamage(damage);
        if (!target.mob.isBoss()) {
            target.charge = 0; // hitting a normal mob staggers it
        }
        ui.success(target.mob.name() + " took " + dealt + " damage.");
        if (!target.mob.isAlive()) {
            onKill(target.mob);
        }
        endTurn();
        return true;
    }

    /** Casts the spell in slot 0/1. Returns false if it could not be cast. */
    public boolean castSpell(int slot) {
        if (!inProgress()) {
            return false;
        }
        ItemInstance spell = player.spell(slot);
        if (spell == null) {
            ui.warn("No spell in that slot.");
            return false;
        }
        int wait = cooldownRemaining(spell);
        if (wait > 0) {
            ui.warn("You have to wait " + wait + " turn(s) to use " + spell.name() + " again.");
            return false;
        }
        if (spell.healAmount() > 0) {
            int healed = player.health().heal(spell.healAmount());
            ui.event(Tone.MAGIC, "You cast " + spell.name() + " and healed " + healed + " health.");
        }
        int damage = player.spellDamage(spell);
        if (damage > 0) {
            List<RoomMob> targets = aliveMobs();
            if (targets.isEmpty()) {
                ui.warn("There are no mobs to hit.");
                return false;
            }
            if (spell.effect().target() != ItemEffect.Target.ALL) {
                targets = targets.subList(0, 1);
            }
            ui.event(Tone.MAGIC, "You cast " + spell.name() + "!");
            for (RoomMob t : targets) {
                int dealt = t.mob.takeDamage(damage);
                ui.success("  " + t.mob.name() + " took " + dealt + " damage.");
                if (!t.mob.isAlive()) {
                    onKill(t.mob);
                }
            }
        }
        lastCast.put(spell.instanceId(), turnsUsed);
        endTurn();
        return true;
    }

    /** Boss rooms only: avoid the boss's attack if it lands this turn. */
    public boolean dodge() {
        if (!inProgress() || !isBossRoom()) {
            return false;
        }
        dodging = true;
        ui.info("You get ready to dodge.");
        endTurn();
        return true;
    }

    /** Uses a turn without acting (e.g. changing gear mid-run). */
    public void spendTurn() {
        if (inProgress()) {
            endTurn();
        }
    }

    public boolean moveOn() {
        if (!canMoveOn()) {
            return false;
        }
        room++;
        spawnRoom();
        if (isBossRoom()) {
            ui.banner(Tone.DANGER, "You've reached the boss!", "Room " + (room + 1) + " of " + roomCount()
                    + ". Watch its attack bar and dodge when it says it is attacking next turn.");
        } else {
            ui.info("Walking... Group of enemies found in room " + (room + 1) + " of " + roomCount() + ".");
        }
        return true;
    }

    public void abandon() {
        if (inProgress()) {
            status = Status.ABANDONED;
        }
    }

    // ------------------------------------------------------------------ internals

    private void spawnRoom() {
        mobs.clear();
        List<String> ids = isBossRoom() ? List.of(dungeon.boss()) : dungeon.waves().get(room);
        for (String id : ids) {
            MobDefinition def = content.mob(id);
            mobs.add(new RoomMob(MobFactory.spawn(def, settings.mobLevel(), difficulty.mobScale(), rng)));
        }
    }

    private void onKill(Mob mob) {
        ui.success(mob.name() + " was defeated!  +" + mob.exp() + " EXP" + (mob.gold() > 0 ? "  +" + mob.gold() + " gold" : ""));
        player.wallet().add(mob.gold());
        LevelUpResult lu = player.addExp(mob.exp());
        if (lu.leveledUp()) {
            ui.banner(Tone.REWARD, "LEVEL UP!  You are now level " + lu.toLevel(),
                    "+" + lu.levelsGained() * player.config().pointsPerLevel() + " attribute point(s). Health restored.");
        }
        if (lu.reachedMax()) {
            ui.reward("You've reached the max level.");
        }
        if (isBossRoom() && roomCleared()) {
            complete();
        } else if (roomCleared()) {
            ui.success("Room cleared! Move on when you're ready.");
        }
    }

    private void complete() {
        status = Status.CLEARED;
        player.markCleared(dungeon.id());
        RewardRoller roller = new RewardRoller(content);
        for (ItemInstance item : roller.roll(dungeon, difficulty, mode.rewardItems(), rng)) {
            if (player.inventory().add(item)) {
                rewards.add(item);
            } else {
                rewardsLost.add(item);
            }
        }
    }

    private void endTurn() {
        turnsUsed++;
        turnsLeft--;
        if (!inProgress()) {
            return;
        }
        for (RoomMob m : aliveMobs()) {
            m.charge++;
            if (m.charge >= m.attackEvery()) {
                m.charge = 0;
                if (dodging && m.mob.isBoss()) {
                    ui.success("You dodged " + m.mob.name() + "'s attack.");
                    continue;
                }
                int dmg = Math.max(1, m.mob.damage() - player.defense());
                int taken = player.health().damage(dmg);
                ui.danger("You took " + taken + " damage from " + m.mob.label() + ".");
                if (!player.health().isAlive()) {
                    break;
                }
            }
        }
        dodging = false;
        if (!player.health().isAlive()) {
            onDeath();
        }
        if (inProgress() && turnsLeft <= 0) {
            turnsLeft = 0;
            status = Status.OUT_OF_TURNS;
            ui.danger("You ran out of turns.");
        }
    }

    private void onDeath() {
        deaths++;
        if (mode.isOutOfLives(deaths)) {
            status = Status.DIED;
            ui.danger("You died." + (mode.lives() == 1 ? " " + mode.name() + " runs only give you one life." : ""));
            return;
        }
        turnsLeft -= mode.deathTurnPenalty();
        if (turnsLeft <= 0) {
            turnsLeft = 0;
            status = Status.OUT_OF_TURNS;
            ui.danger("You died and the " + mode.deathTurnPenalty()
                    + "-turn respawn penalty used up your remaining turns.");
            return;
        }
        int hp = Math.max(1, player.health().max() * mode.respawnHealthPercent() / 100);
        player.health().set(hp);
        dodging = false;
        spawnRoom(); // the room starts over
        ui.banner(Tone.DANGER, "You died!", "You respawned at the start of room " + (room + 1) + " with " + hp
                + " health.", Ansi.color("-" + mode.deathTurnPenalty() + " turns  (" + turnsLeft + " left)", Ansi.Color.YELLOW));
    }
}
