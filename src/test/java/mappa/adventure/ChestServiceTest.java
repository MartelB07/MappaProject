package mappa.adventure;

import mappa.core.content.ChestDefinition;
import mappa.core.content.ContentRegistry;
import mappa.core.inventory.ItemStack;
import mappa.core.items.ItemInstance;
import mappa.testsupport.FixedRng;
import mappa.testsupport.ScriptedIO;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(5)
class ChestServiceTest {
    private final ContentRegistry content = TestContent.small();
    private final AdventureConfig config = AdventureConfig.from(content);
    private final ScriptedIO io = new ScriptedIO();

    private AdventurePlayer emptyPlayer() {
        AdventurePlayer p = new AdventurePlayer("Ari", config, content);
        p.inventory().removeByDefinition("t_potion", 99);
        return p;
    }

    private ChestDefinition chest(String id) {
        return content.chests().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    @Tag("regression")
    @DisplayName("A-01: opening a potion chest with a full inventory finishes and reports it")
    void healthChest_inventoryFull_terminates() {
        AdventurePlayer p = emptyPlayer();
        p.inventory().add(ItemInstance.of(content.item("adv_key_1")), 2); // 1 slot; using one key frees nothing
        p.inventory().add(ItemInstance.of(content.item("t_potion")), 99);
        while (!p.inventory().isFull()) {
            p.inventory().add(ItemInstance.of(content.item("t_helm")));
        }
        ChestService s = new ChestService(content, config, new FixedRng(), io);
        ItemStack key = s.bestKey(p, 1);
        assertEquals(ChestService.Result.OPENED,
                s.open(p, new ChestService.FoundChest(chest("t_chest_potions"), 1), key));
        assertTrue(io.outputContains("couldn't be collected"));
        assertEquals(99, p.inventory().count("t_potion"));
    }

    @Test
    @Tag("regression")
    @DisplayName("A-11: a key opens chests up to its level, preferring the cheapest key")
    void bestKey_opensChestsUpToItsLevel() {
        AdventurePlayer p = emptyPlayer();
        ChestService s = new ChestService(content, config, new FixedRng(), io);
        p.inventory().add(ItemInstance.of(content.item("adv_key_3")));
        assertNotNull(s.bestKey(p, 2));
        assertNull(s.bestKey(p, 4));
        p.inventory().add(ItemInstance.of(content.item("adv_key_1")));
        assertEquals("adv_key_1", s.bestKey(p, 1).item().definitionId());
        assertEquals("adv_key_3", s.bestKey(p, 2).item().definitionId());
    }

    @Test
    @Tag("regression")
    @DisplayName("A-11: chest levels stay within the region's range")
    void search_chestLevelWithinRegionRange() {
        ChestService low = new ChestService(content, config, new FixedRng().ints(0, 0), io);
        assertEquals(1, low.search(content.region("t_town")).level());
        ChestService high = new ChestService(content, config, new FixedRng().ints(0, 99), io);
        assertEquals(3, high.search(content.region("t_town")).level());
        assertNull(low.search(content.region("t_home")), "no chests at home");
    }

    @Test
    void search_countdownNotDone_findsNothing() {
        ChestService s = new ChestService(content, config, new FixedRng(), io);
        s.setCountdown(2);
        assertNull(s.search(content.region("t_town")));
    }

    @Test
    void goldChest_paysPerChestLevel() {
        AdventurePlayer p = emptyPlayer();
        p.inventory().add(ItemInstance.of(content.item("adv_key_3")));
        ChestService s = new ChestService(content, config, new FixedRng(), io);
        s.open(p, new ChestService.FoundChest(chest("t_chest_gold"), 3), s.bestKey(p, 3));
        assertEquals(6, p.wallet().gold());
        assertEquals(0, p.inventory().count("adv_key_3"), "key used up");
        assertEquals(2, s.countdown());
    }

    @Test
    void monsterChest_returnsMonster() {
        AdventurePlayer p = emptyPlayer();
        p.inventory().add(ItemInstance.of(content.item("adv_key_1")));
        ChestService s = new ChestService(content, config, new FixedRng(), io);
        assertEquals(ChestService.Result.MONSTER,
                s.open(p, new ChestService.FoundChest(chest("t_chest_monster"), 1), s.bestKey(p, 1)));
    }

    @Test
    void onFightWon_dropsKeyOnlyAfterLevelZero() {
        AdventurePlayer p = emptyPlayer();
        ChestService s = new ChestService(content, config, FixedRng.alwaysLucky(), io);
        s.onFightWon(p, 1);
        assertEquals(0, p.inventory().count("adv_key_1"));
        p.addExp(5);
        s.onFightWon(p, 1);
        assertEquals(1, p.inventory().count("adv_key_1"));
    }
}
