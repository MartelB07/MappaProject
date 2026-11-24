package mappa.adventure;

import mappa.core.content.ContentRegistry;
import mappa.core.inventory.ItemStack;
import mappa.core.items.ItemSlot;
import mappa.testsupport.ScriptedIO;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopTest {
    private final ContentRegistry content = TestContent.small();
    private final AdventureConfig config = AdventureConfig.from(content);
    private final ScriptedIO io = new ScriptedIO();
    private final Shop shop = new Shop(content, config, io);

    private AdventurePlayer player(long gold) {
        AdventurePlayer p = new AdventurePlayer("Ari", config, content);
        p.wallet().add(gold);
        return p;
    }

    @Test
    void forSale_onlyShopTaggedItems() {
        assertEquals(1, shop.forSale(ItemSlot.CONSUMABLE).size());
        assertEquals("t_shop_helm", shop.forSale(ItemSlot.HELMET).get(0).id());
    }

    @Test
    void buy_insufficientGold_noItemNoCharge() {
        AdventurePlayer p = player(4);
        assertEquals(0, shop.buy(p, content.item("t_potion"), 1));
        assertEquals(4, p.wallet().gold());
        assertEquals(3, p.inventory().count("t_potion"));
    }

    @Test
    void buy_partialAffordable_buysOnlyAffordable() {
        AdventurePlayer p = player(12);
        assertEquals(2, shop.buy(p, content.item("t_potion"), 5));
        assertEquals(2, p.wallet().gold());
        assertEquals(5, p.inventory().count("t_potion"));
    }

    @Test
    void buy_gear_limitedByInventorySpace() {
        AdventurePlayer p = player(1000); // 5 slots, 1 used by potions
        assertEquals(4, shop.buy(p, content.item("t_shop_helm"), 10));
        assertEquals(1000 - 4 * 20, p.wallet().gold());
        assertTrue(p.inventory().isFull());
        assertEquals(0, shop.buy(p, content.item("t_shop_helm"), 1));
        assertEquals(1000 - 4 * 20, p.wallet().gold(), "no gold taken when nothing fits");
    }

    @Test
    @Tag("regression")
    @DisplayName("ST-12: selling pays whole gold at the configured percent")
    void sell_returns75PercentAsWholeGold() {
        AdventurePlayer p = player(0);
        ItemStack potions = p.inventory().stacks().get(0);
        assertEquals(6, shop.sell(p, potions, 2)); // 2 x floor(5 * 0.75)
        assertEquals(1, p.inventory().count("t_potion"));
    }
}
