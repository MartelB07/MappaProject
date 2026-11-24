package mappa.adventure;

import mappa.core.content.ContentRegistry;
import mappa.core.items.ItemDefinition;
import mappa.core.mobs.Mob;
import mappa.core.mobs.MobFactory;
import mappa.testsupport.FixedRng;
import mappa.testsupport.ScriptedIO;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test content: player has 10 HP at level 0 (30 at level 2), a club (4 dmg), 3 potions (+5 each). */
@Timeout(5)
class AdventureCombatTest {
    private final ContentRegistry content = TestContent.small();
    private final AdventureConfig config = AdventureConfig.from(content);
    private final ItemDefinition potion = content.item("t_potion");

    private AdventurePlayer player() {
        return new AdventurePlayer("Ari", config, content);
    }

    private Mob rat() {
        return MobFactory.spawn(content.mob("t_rat"), 0, 1.0, new FixedRng());
    }

    @Test
    @Tag("regression")
    @DisplayName("A-02: asking for 3 potions uses exactly 3 when all are needed")
    void heal_requestThree_consumesExactlyThree() {
        AdventurePlayer p = player();
        p.addExp(20); // level 2 -> 30 max HP
        p.health().damage(29);
        int used = AdventureCombat.drinkPotions(p, potion, 3, new ScriptedIO());
        assertEquals(3, used);
        assertEquals(0, p.inventory().count("t_potion"));
        assertEquals(16, p.health().current());
    }

    @Test
    @Tag("regression")
    @DisplayName("A-02: never drinks more potions than needed")
    void heal_requestMoreThanNeeded_usesOnlyNeeded() {
        AdventurePlayer p = player();
        p.health().damage(6); // missing 6 -> 2 potions
        assertEquals(2, AdventureCombat.drinkPotions(p, potion, 3, new ScriptedIO()));
        assertEquals(1, p.inventory().count("t_potion"));
    }

    @Test
    @Tag("regression")
    @DisplayName("A-02: at full health no potion is used")
    void heal_atFullHealth_consumesNone() {
        AdventurePlayer p = player();
        ScriptedIO io = new ScriptedIO();
        assertEquals(0, AdventureCombat.drinkPotions(p, potion, 2, io));
        assertEquals(3, p.inventory().count("t_potion"));
        assertTrue(io.outputContains("already at full health"));
    }

    @Test
    @Tag("regression")
    @DisplayName("A-10: healing in battle takes a turn, so the monster attacks")
    void heal_takesTurn_monsterRetaliates() {
        AdventurePlayer p = player();
        p.health().damage(5);
        AdventureCombat c = new AdventureCombat(p, rat(), config, content, FixedRng.neverLucky(), new ScriptedIO());
        assertNull(c.healTurn(potion, 1));
        assertEquals(8, p.health().current(), "healed to 10, then the rat hit for 2");
    }

    @Test
    void attackTurn_killsMob_returnsWon() {
        AdventurePlayer p = player();
        AdventureCombat c = new AdventureCombat(p, rat(), config, content, FixedRng.neverLucky(), new ScriptedIO());
        assertNull(c.attackTurn()); // 6 hp - 4
        assertEquals(AdventureCombat.Outcome.WON, c.attackTurn());
    }

    @Test
    @Tag("regression")
    @DisplayName("A-04: dying offers a revive exactly once")
    void playerDies_reviveOfferedOnce() {
        AdventurePlayer p = player();
        p.wallet().add(35);
        ScriptedIO io = ScriptedIO.of("1", "1", "2");
        AdventureGame game = new AdventureGame(content, FixedRng.neverLucky(), io, false).withPlayer(p, "t_home");
        Mob king = MobFactory.spawn(content.mob("t_king"), 3, 1.0, new FixedRng());
        assertFalse(game.fight(king));
        String out = io.output();
        assertEquals(1, out.split("Revive for", -1).length - 1);
        assertEquals(1, out.split("You Died", -1).length - 1);
    }

    @Test
    void playerDies_acceptsRevive_continuesWithFullHealth() {
        AdventurePlayer p = player();
        p.wallet().add(35);
        ScriptedIO io = ScriptedIO.of("1", "1", "1");
        AdventureGame game = new AdventureGame(content, FixedRng.neverLucky(), io, false).withPlayer(p, "t_home");
        assertTrue(game.fight(MobFactory.spawn(content.mob("t_king"), 3, 1.0, new FixedRng())));
        assertEquals(p.health().max(), p.health().current());
        assertEquals(0, p.wallet().gold());
    }

    @Test
    @Tag("regression")
    @DisplayName("A-07: beating the final boss wins the game")
    void killFinalBoss_returnsVictory() {
        AdventurePlayer p = player();
        p.makeDeveloper(); // level 3: 40 HP, 6 + 4 damage
        ScriptedIO io = ScriptedIO.of("1", "1", "1", "2");
        AdventureGame game = new AdventureGame(content, FixedRng.neverLucky(), io, false).withPlayer(p, "t_castle");
        assertFalse(game.fight(MobFactory.spawn(content.mob("t_king"), 3, 1.0, new FixedRng())), "chose to stop playing");
        assertTrue(game.hasWon());
        assertTrue(io.outputContains("You win!"));
    }

    @Test
    void escape_success_returnsFled() {
        AdventureCombat c = new AdventureCombat(player(), rat(), config, content, FixedRng.alwaysLucky(),
                new ScriptedIO());
        assertEquals(AdventureCombat.Outcome.FLED, c.escapeTurn());
    }
}
