package mappa.adventure;

import mappa.core.content.ContentRegistry;
import mappa.core.inventory.ItemStack;
import mappa.core.io.GameIO;
import mappa.core.io.Menu;
import mappa.core.items.ItemDefinition;
import mappa.core.items.ItemInstance;
import mappa.core.items.ItemSlot;

import java.util.ArrayList;
import java.util.List;

/** Buying and selling. Items for sale are those with the config's shopTag (default "adv:shop"). */
public class Shop {
    private final ContentRegistry content;
    private final AdventureConfig config;
    private final GameIO io;

    public Shop(ContentRegistry content, AdventureConfig config, GameIO io) {
        this.content = content;
        this.config = config;
        this.io = io;
    }

    public List<ItemDefinition> forSale(ItemSlot... slots) {
        List<ItemDefinition> out = new ArrayList<>();
        for (ItemDefinition d : content.itemsWithTag(config.shopTag())) {
            for (ItemSlot s : slots) {
                if (d.slot() == s) {
                    out.add(d);
                }
            }
        }
        out.sort((a, b) -> a.levelReq() != b.levelReq() ? Integer.compare(a.levelReq(), b.levelReq())
                : Integer.compare(a.price(), b.price()));
        return out;
    }

    /**
     * Buys up to {@code amount}, limited by gold and inventory space.
     * Returns how many were bought (0 = nothing bought, no gold taken).
     */
    public int buy(AdventurePlayer player, ItemDefinition def, int amount) {
        if (amount <= 0) {
            return 0;
        }
        long price = def.price();
        int affordable = price == 0 ? amount : (int) Math.min(amount, player.wallet().gold() / price);
        if (affordable == 0) {
            io.println("You don't have enough gold. " + def.name() + " costs " + price + ".");
            return 0;
        }
        int bought = 0;
        if (def.slot().isStackable()) {
            ItemInstance item = ItemInstance.of(def);
            bought = player.inventory().add(item, Math.min(affordable, player.inventory().spaceFor(item)));
        } else {
            while (bought < affordable && player.inventory().add(ItemInstance.of(def))) {
                bought++;
            }
        }
        if (bought == 0) {
            io.println("You don't have enough inventory space.");
            return 0;
        }
        player.wallet().trySpend(price * bought);
        io.println("You bought " + def.name() + " x" + bought + " for " + (price * bought) + " gold.");
        if (bought < amount) {
            io.println("(You could only get " + bought + " of the " + amount + " you asked for.)");
        }
        return bought;
    }

    /** Sells up to {@code amount} from a stack. Returns gold received. */
    public long sell(AdventurePlayer player, ItemStack stack, int amount) {
        ItemInstance item = stack.item();
        int value = item.sellValue(config.sellPercent());
        int n = Math.min(amount, stack.count());
        for (int i = 0; i < n; i++) {
            player.inventory().remove(item);
        }
        long gold = (long) value * n;
        player.wallet().add(gold);
        io.println("You sold " + item.name() + " x" + n + " for " + gold + " gold.");
        return gold;
    }

    /** Interactive shop. */
    public void visit(AdventurePlayer player, Menu menu) {
        while (true) {
            io.println();
            io.println("Gold: " + player.wallet().gold());
            int c = menu.choose("Shop", "leave", "Armor", "Weapons", "Heals", "Sell");
            switch (c) {
                case Menu.CANCEL -> {
                    return;
                }
                case 1 -> buyMenu(player, menu, forSale(ItemSlot.HELMET, ItemSlot.CHEST, ItemSlot.BOOTS));
                case 2 -> buyMenu(player, menu, forSale(ItemSlot.WEAPON));
                case 3 -> buyMenu(player, menu, forSale(ItemSlot.CONSUMABLE));
                case 4 -> sellMenu(player, menu);
                default -> { }
            }
        }
    }

    private void buyMenu(AdventurePlayer player, Menu menu, List<ItemDefinition> items) {
        List<String> labels = new ArrayList<>();
        for (ItemDefinition d : items) {
            String desc = ItemInstance.of(d).statSummary();
            labels.add("[Level " + d.levelReq() + "] " + d.name() + "  [Cost: " + d.price() + "]  (" + desc + ")");
        }
        io.println();
        io.println("Gold: " + player.wallet().gold());
        int pick = menu.chooseFrom("What would you like to buy?", labels, "go back");
        if (pick == Menu.CANCEL) {
            return;
        }
        ItemDefinition def = items.get(pick - 1);
        if (def.levelReq() > player.level()
                && !menu.confirm("You have to be level " + def.levelReq() + " to use this. Buy it anyway?")) {
            return;
        }
        int amount = 1;
        if (def.slot().isStackable()) {
            amount = menu.readInt("How many do you want to buy? (0 to cancel): ", 1, 999, true);
            if (amount == Menu.CANCEL) {
                return;
            }
        }
        buy(player, def, amount);
    }

    private void sellMenu(AdventurePlayer player, Menu menu) {
        List<ItemStack> stacks = new ArrayList<>(player.inventory().stacks());
        if (stacks.isEmpty()) {
            io.println("No item to sell.");
            return;
        }
        List<String> labels = new ArrayList<>();
        for (ItemStack s : stacks) {
            labels.add(s.item().displayName() + "  (You have: " + s.count() + ")  -> "
                    + s.item().sellValue(config.sellPercent()) + " gold each");
        }
        io.println();
        io.println("*Items sell for " + config.sellPercent() + "% of their original cost.*");
        int pick = menu.chooseFrom("Which item would you like to sell?", labels, "go back");
        if (pick == Menu.CANCEL) {
            return;
        }
        ItemStack stack = stacks.get(pick - 1);
        int amount = stack.count() == 1 ? 1
                : menu.readInt("How many would you like to sell? (1-" + stack.count() + ", 0 to cancel): ",
                1, stack.count(), true);
        if (amount != Menu.CANCEL && menu.confirm("Sell " + stack.item().name() + " x" + amount + "?")) {
            sell(player, stack, amount);
        }
    }
}
