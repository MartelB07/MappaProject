package mappa.dungeons;

import mappa.core.inventory.ItemStack;
import mappa.core.io.Ansi;
import mappa.core.io.Menu;
import mappa.core.items.ClassTag;
import mappa.core.items.ItemInstance;
import mappa.core.items.ItemSlot;
import mappa.core.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Inventory / equip / unequip / attributes menus shared by the lobby and dungeon runs. */
final class DungeonInventoryScreen {
    private final DungeonPlayer player;
    private final Menu menu;
    private final Ui ui;

    DungeonInventoryScreen(DungeonPlayer player, Menu menu) {
        this.player = player;
        this.menu = menu;
        this.ui = menu.ui();
    }

    static String describe(ItemInstance i, int count) {
        String stats = i.statSummary();
        return i.displayName() + (count > 1 ? " x" + count : "")
                + (stats.isEmpty() ? "" : Ansi.color("  (" + stats + ")", Ansi.Color.GRAY))
                + (i.classTag() != ClassTag.ANY ? Ansi.color("  [" + i.classTag() + "]", Ansi.Color.GRAY) : "");
    }

    void showInventory() {
        List<String> lines = new ArrayList<>();
        for (ItemStack s : player.inventory().stacks()) {
            lines.add(describe(s.item(), s.count()));
        }
        if (lines.isEmpty()) {
            lines.add("(empty)");
        }
        ui.header("Inventory  " + player.inventory().usedSlots() + "/" + player.inventory().capacity() + " slots",
                lines);
    }

    void showStats() {
        List<String> lines = new ArrayList<>();
        lines.add("Health: " + ui.bar(player.health().current(), player.health().max(), 14));
        lines.add("Level:  " + player.level() + "   Exp: " + (player.progression().isMaxLevel() ? "MAX"
                : player.progression().exp() + "/" + player.progression().expForNext()));
        for (ItemSlot slot : List.of(ItemSlot.WEAPON, ItemSlot.HELMET, ItemSlot.CHEST)) {
            ItemInstance i = player.equipment().get(slot);
            lines.add(label(slot) + ": " + (i == null ? "-" : describe(i, 1)));
        }
        for (int s = 0; s < DungeonPlayer.SPELL_SLOTS; s++) {
            ItemInstance i = player.spell(s);
            lines.add("Spell " + (s + 1) + ": " + (i == null ? "-" : describe(i, 1)));
        }
        lines.add("Attack damage: " + player.attackDamage() + "   Defense: " + player.defense());
        lines.add("Gold: " + Ansi.color(String.valueOf(player.wallet().gold()), Ansi.Color.GOLD));
        ui.header(player.name() + "'s Stats", lines);
    }

    static String label(ItemSlot slot) {
        return switch (slot) {
            case WEAPON -> "Weapon";
            case HELMET -> "Helmet";
            case CHEST -> "Armor";
            default -> slot.name();
        };
    }

    /** Returns true if something was equipped or unequipped. */
    boolean manage() {
        boolean changed = false;
        while (true) {
            showInventory();
            int c = menu.choose("Would you like to:", "cancel", "Equip", "Unequip");
            if (c == Menu.CANCEL) {
                return changed;
            }
            changed |= c == 1 ? equip() : unequip();
        }
    }

    private boolean equip() {
        List<ItemStack> options = player.inventory().find(i -> i.slot().isGear() || i.slot() == ItemSlot.SPELL);
        if (options.isEmpty()) {
            ui.warn("No item to equip.");
            return false;
        }
        List<String> labels = new ArrayList<>();
        for (ItemStack s : options) {
            labels.add(describe(s.item(), s.count()));
        }
        int pick = menu.chooseFrom("Which item would you like to equip?", labels, "cancel");
        if (pick == Menu.CANCEL) {
            return false;
        }
        ItemInstance item = options.get(pick - 1).item();
        DungeonPlayer.EquipOutcome outcome;
        if (item.slot() == ItemSlot.SPELL) {
            List<String> slots = new ArrayList<>();
            for (int s = 0; s < DungeonPlayer.SPELL_SLOTS; s++) {
                slots.add("Spell " + (s + 1) + ": " + (player.spell(s) == null ? "-" : player.spell(s).name()));
            }
            int slot = menu.chooseFrom("Which slot?", slots, "cancel");
            if (slot == Menu.CANCEL) {
                return false;
            }
            outcome = player.equipSpell(item, slot - 1);
        } else {
            outcome = player.equipGear(item);
        }
        return report(outcome, item);
    }

    private boolean unequip() {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        DungeonPlayer.EquipOutcome[] result = new DungeonPlayer.EquipOutcome[1];
        for (Map.Entry<ItemSlot, ItemInstance> e : player.gear().entrySet()) {
            labels.add(label(e.getKey()) + ": " + describe(e.getValue(), 1));
            ItemSlot slot = e.getKey();
            actions.add(() -> result[0] = player.unequipGear(slot));
        }
        for (int s = 0; s < DungeonPlayer.SPELL_SLOTS; s++) {
            if (player.spell(s) != null) {
                labels.add("Spell " + (s + 1) + ": " + describe(player.spell(s), 1));
                int slot = s;
                actions.add(() -> result[0] = player.unequipSpell(slot));
            }
        }
        if (labels.isEmpty()) {
            ui.warn("No item to unequip.");
            return false;
        }
        int pick = menu.chooseFrom("Which item would you like to unequip?", labels, "cancel");
        if (pick == Menu.CANCEL) {
            return false;
        }
        actions.get(pick - 1).run();
        return report(result[0], null);
    }

    private boolean report(DungeonPlayer.EquipOutcome outcome, ItemInstance item) {
        switch (outcome) {
            case OK -> ui.success(item == null ? "Unequipped." : "Equipped " + item.name() + ".");
            case LEVEL_TOO_LOW -> ui.warn("You need to be level " + item.levelReq() + " to equip that.");
            case INVENTORY_FULL -> ui.warn("Your inventory is full.");
            default -> ui.warn("You can't equip that.");
        }
        return outcome == DungeonPlayer.EquipOutcome.OK;
    }

    void attributes() {
        while (true) {
            ui.header("Attributes",
                "War " + player.points(DungeonPlayer.Attribute.WAR)
                        + "   Mage " + player.points(DungeonPlayer.Attribute.MAGE)
                        + "   Health " + player.points(DungeonPlayer.Attribute.HEALTH),
                "Available points: " + Ansi.bold(String.valueOf(player.availablePoints()),
                        player.availablePoints() > 0 ? Ansi.Color.GOLD : Ansi.Color.DEFAULT));
            int c = menu.choose("Which attribute would you like to update?", "go back",
                    "War Points (+" + player.config().damagePerPoint() + " war damage each)",
                    "Mage Points (+" + player.config().damagePerPoint() + " mage damage each)",
                    "Health Points (+" + player.config().healthPerPoint() + " max health each)",
                    "Reset Points (" + player.resetCost() + " gold)");
            if (c == Menu.CANCEL) {
                return;
            }
            if (c == 4) {
                if (player.spentPoints() == 0) {
                    ui.warn("You have no points to reset.");
                } else if (!player.wallet().canAfford(player.resetCost())) {
                    ui.warn("You don't have enough gold to reset points.");
                } else if (menu.confirm("Reset " + player.spentPoints() + " points for " + player.resetCost() + " gold?")) {
                    player.resetPoints();
                    ui.success("Points have been reset.");
                }
                continue;
            }
            if (player.availablePoints() == 0) {
                ui.warn("You have no points to spend. Level up to earn more.");
                continue;
            }
            int amount = menu.readInt("How many points? (1-" + player.availablePoints() + ", 0 to cancel): ",
                    1, player.availablePoints(), true);
            if (amount != Menu.CANCEL) {
                player.spendPoints(DungeonPlayer.Attribute.values()[c - 1], amount);
            }
        }
    }
}
