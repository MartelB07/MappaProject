package mappa.dungeons;

import mappa.core.content.ContentRegistry;
import mappa.core.content.Difficulty;
import mappa.core.content.DungeonDefinition;
import mappa.core.items.ItemInstance;
import mappa.testsupport.FixedRng;
import mappa.testsupport.ScriptedIO;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test dungeon A: room 1 = one Grunt (EASY: 10 HP, 4 dmg, attacks every 2 turns),
 * room 2 = Big Boss (20 HP, 6 dmg, attacks every 3 turns). Turn limit 30.
 * Player: 10 HP, Test Sword (5 dmg), Bolt (3 dmg to all) in slot 1, Heal (10) in slot 2.
 */
@Timeout(5)
class DungeonRunTest {
    private final ContentRegistry content = TestContent.small();
    private final DungeonsConfig config = DungeonsConfig.from(content);
    private final ScriptedIO io = new ScriptedIO();

    private DungeonPlayer player() {
        DungeonPlayer p = new DungeonPlayer("Tess", config, content);
        p.equipGear(p.inventory().find(i -> i.definitionId().equals("t_sword")).get(0).item());
        p.equipSpell(p.inventory().find(i -> i.definitionId().equals("t_bolt")).get(0).item(), 0);
        p.equipSpell(p.inventory().find(i -> i.definitionId().equals("t_heal")).get(0).item(), 1);
        return p;
    }

    private DungeonRun run(String dungeonId, String difficulty, String mode) {
        return run(dungeonId, difficulty, mode, player());
    }

    private DungeonRun run(String dungeonId, String difficulty, String mode, DungeonPlayer p) {
        return new DungeonRun(content, content.dungeon(dungeonId), content.difficulty(difficulty),
                config.runMode(mode), p, new FixedRng(), io);
    }

    private static void clearFirstRoomAndMoveOn(DungeonRun r) {
        r.attack(0);
        r.attack(0);
        assertTrue(r.roomCleared());
        assertTrue(r.moveOn());
        assertTrue(r.isBossRoom());
    }

    private static void idleUntilDeath(DungeonRun r) {
        int deaths = r.deaths();
        while (r.inProgress() && r.deaths() == deaths) {
            r.spendTurn();
        }
    }

    // ------------------------------------------------------------------ run modes

    @Nested
    @DisplayName("Regular mode")
    class Regular {
        @Test
        void death_respawnsAtStartOfCurrentRoom_andLosesTurns() {
            DungeonRun r = run("dungeon_a", "EASY", "REGULAR");
            clearFirstRoomAndMoveOn(r);           // 2 turns used
            r.attack(0);                          // boss 20 -> 15
            idleUntilDeath(r);                    // boss hits for 6 on turns 3 and 6 of the room
            assertEquals(DungeonRun.Status.IN_PROGRESS, r.status());
            assertEquals(1, r.deaths());
            assertEquals(1, r.room(), "respawned in the boss room, not the first room");
            DungeonRun.RoomMob boss = r.mobs().get(0);
            assertEquals(boss.mob().maxHealth(), boss.mob().health(), "the room starts over");
            assertEquals(r.player().health().max(), r.player().health().current());
            assertEquals(30 - 8 - 5, r.turnsLeft(), "8 turns used + 5 turn death penalty");
            assertTrue(io.outputContains("respawned at the start of room 2"));
        }

        @Test
        void unlimitedLives() {
            DungeonRun r = run("dungeon_a", "EASY", "REGULAR");
            assertEquals(-1, r.livesLeft());
        }

        @Test
        void penaltyUsesUpRemainingTurns_endsRunOutOfTurns() {
            DungeonRun r = run("dungeon_a", "EASY", "REGULAR");
            // Idle in room 1: the grunt deals 4 every 2 turns, so you die every 6 turns.
            for (int i = 0; i < 3 && r.inProgress(); i++) {
                idleUntilDeath(r);
            }
            assertEquals(DungeonRun.Status.OUT_OF_TURNS, r.status());
            assertEquals(0, r.turnsLeft());
            assertTrue(io.outputContains("respawn penalty used up your remaining turns"));
        }

        @Test
        void clear_givesTwoRewardItems() {
            DungeonRun r = run("dungeon_a", "EASY", "REGULAR");
            clearFirstRoomAndMoveOn(r);
            while (r.inProgress()) {
                r.attack(0);
            }
            assertEquals(DungeonRun.Status.CLEARED, r.status());
            assertEquals(2, r.rewards().size());
            assertTrue(r.player().hasCleared("dungeon_a"));
        }
    }

    @Nested
    @DisplayName("Hardcore mode")
    class Hardcore {
        @Test
        void death_endsTheRun_noRewards() {
            DungeonRun r = run("dungeon_a", "EASY", "HARDCORE");
            assertEquals(1, r.livesLeft());
            idleUntilDeath(r);
            assertEquals(DungeonRun.Status.DIED, r.status());
            assertEquals(0, r.livesLeft());
            assertTrue(r.rewards().isEmpty());
            assertFalse(r.player().hasCleared("dungeon_a"));
            assertFalse(r.attack(0), "no actions after the run ends");
        }

        @Test
        void clear_givesThreeRewardItems() {
            DungeonRun r = run("dungeon_a", "EASY", "HARDCORE");
            clearFirstRoomAndMoveOn(r);
            while (r.inProgress()) {
                r.attack(0);
            }
            assertEquals(DungeonRun.Status.CLEARED, r.status());
            assertEquals(3, r.rewards().size());
        }
    }

    @Test
    void rewards_inventoryFull_reportedAsLost() {
        DungeonPlayer p = player();
        while (p.inventory().freeSlots() > 1) {
            p.inventory().add(ItemInstance.of(content.item("t_helm")));
        }
        DungeonRun r = run("dungeon_a", "EASY", "HARDCORE", p);
        clearFirstRoomAndMoveOn(r);
        while (r.inProgress()) {
            r.attack(0);
        }
        assertEquals(1, r.rewards().size());
        assertEquals(2, r.rewardsLost().size());
    }

    // ------------------------------------------------------------------ regressions

    @Test
    @Tag("regression")
    @DisplayName("D-01: Nightmare on the last real dungeon doesn't crash")
    void lastDungeonNightmare_doesNotThrow() {
        ContentRegistry real = TestContent.real();
        DungeonsConfig realConfig = DungeonsConfig.from(real);
        DungeonDefinition last = real.dungeons().get(real.dungeons().size() - 1);
        Difficulty nightmare = real.difficulty("NIGHTMARE");
        assertDoesNotThrow(() -> new DungeonRun(real, last, nightmare, realConfig.runMode("REGULAR"),
                new DungeonPlayer("x", realConfig, real), new FixedRng(), new ScriptedIO()));
        assertFalse(new RewardRoller(real).eligible(last, "NIGHTMARE").isEmpty());
    }

    @Test
    @Tag("regression")
    @DisplayName("D-12: the reward cap comes from the chosen difficulty")
    void rewardCap_matchesSelectedDifficulty() {
        RewardRoller roller = new RewardRoller(content);
        DungeonDefinition a = content.dungeon("dungeon_a");
        assertTrue(roller.eligible(a, "EASY").stream().allMatch(i -> i.levelReq() <= 0));
        assertTrue(roller.eligible(a, "NIGHTMARE").stream().anyMatch(i -> i.id().equals("t_big_sword")));
    }

    @Test
    @Tag("regression")
    @DisplayName("D-11: running out of turns is not reported as death")
    void turnsReachZero_resultOutOfTurns_notDied() {
        DungeonRun r = run("dungeon_a", "EASY", "HARDCORE");
        r.attack(0);
        r.attack(0);
        while (r.inProgress()) {
            r.spendTurn();
        }
        assertEquals(DungeonRun.Status.OUT_OF_TURNS, r.status());
        assertFalse(io.outputContains("You died"));
    }

    @Test
    @Tag("regression")
    @DisplayName("D-05: healing spells heal")
    void healingSpell_restoresHealth() {
        DungeonRun r = run("dungeon_a", "EASY", "REGULAR");
        r.spendTurn();
        r.spendTurn(); // grunt hits for 4
        assertEquals(6, r.player().health().current());
        assertTrue(r.castSpell(1));
        assertEquals(10, r.player().health().current());
    }

    @Test
    @Tag("regression")
    @DisplayName("D-10: a spell equipped mid-run can be cast right away")
    void spellEquippedMidRun_castableImmediately() {
        DungeonRun r = run("dungeon_a", "EASY", "REGULAR");
        assertTrue(r.castSpell(0));
        ItemInstance mageBolt = ItemInstance.of(content.item("t_mage_bolt"));
        r.player().inventory().add(mageBolt);
        r.player().equipSpell(mageBolt, 0);
        assertEquals(0, r.cooldownRemaining(mageBolt));
        assertTrue(r.castSpell(0));
    }

    @Test
    @Tag("regression")
    @DisplayName("D-10: cooldown reports a positive number of turns")
    void spell_onCooldown_reportsPositiveTurnsRemaining() {
        DungeonRun r = run("dungeon_a", "EASY", "REGULAR");
        ItemInstance bolt = r.player().spell(0);
        assertTrue(r.castSpell(0));
        assertEquals(2, r.cooldownRemaining(bolt));
        assertFalse(r.castSpell(0));
        assertTrue(io.outputContains("wait 2 turn(s)"));
        r.spendTurn();
        r.spendTurn();
        assertEquals(0, r.cooldownRemaining(bolt));
    }

    @Test
    @Tag("regression")
    @DisplayName("D-17: rooms with more than four mobs can be targeted")
    void targetList_supportsGroupsLargerThanFour() {
        DungeonRun r = run("dungeon_b", "EASY", "REGULAR");
        assertEquals(5, r.aliveMobs().size());
        assertTrue(r.attack(4));
        assertEquals(1, r.aliveMobs().stream().filter(m -> m.mob().health() < m.mob().maxHealth()).count());
    }

    @Test
    @Tag("regression")
    @DisplayName("D-16: boss telegraph is a counter, and dodging on the warning turn avoids the hit")
    void boss_dodgeOnWarningTurn_negatesDamage() {
        DungeonRun r = run("dungeon_a", "EASY", "REGULAR");
        clearFirstRoomAndMoveOn(r);
        r.spendTurn();
        r.spendTurn();
        DungeonRun.RoomMob boss = r.mobs().get(0);
        assertTrue(boss.attacksNextTurn());
        int hp = r.player().health().current();
        assertTrue(r.dodge());
        assertEquals(hp, r.player().health().current());
        assertTrue(io.outputContains("You dodged"));
    }

    @Test
    void dodge_outsideBossRoom_notAllowed() {
        assertFalse(run("dungeon_a", "EASY", "REGULAR").dodge());
    }

    @Test
    void moveOn_beforeRoomCleared_refused() {
        assertFalse(run("dungeon_a", "EASY", "REGULAR").moveOn());
    }

    @Test
    void abandon_endsRunWithoutRewards() {
        DungeonRun r = run("dungeon_a", "EASY", "REGULAR");
        r.abandon();
        assertEquals(DungeonRun.Status.ABANDONED, r.status());
        assertTrue(r.rewards().isEmpty());
    }

    @Test
    @Tag("regression")
    @DisplayName("D-18: dungeons unlock only after the required one is cleared")
    void lockedDungeon_unlocksAfterClear() {
        DungeonPlayer p = player();
        DungeonDefinition b = content.dungeon("dungeon_b");
        assertFalse(DungeonsGame.isUnlocked(p, b));
        p.markCleared("dungeon_a");
        assertTrue(DungeonsGame.isUnlocked(p, b));
    }
}
