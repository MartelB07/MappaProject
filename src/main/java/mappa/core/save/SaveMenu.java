package mappa.core.save;

import mappa.core.io.Menu;
import mappa.core.ui.Tone;
import mappa.core.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The shared "New game / Continue / Delete a save" screen shown when a mode opens. */
public class SaveMenu {

    /** What the player picked. {@code slot} is the slot to load from or to save a new game into. */
    public record Choice(Kind kind, int slot) {
        public enum Kind { NEW_GAME, LOAD, BACK }

        public static final Choice BACK = new Choice(Kind.BACK, 0);
    }

    private final SaveStore store;
    private final Menu menu;
    private final Ui ui;

    public SaveMenu(SaveStore store, Menu menu) {
        this.store = store;
        this.menu = menu;
        this.ui = menu.ui();
    }

    /** Loops until the player starts a new game, picks a save to continue, or goes back. */
    public Choice start(String title) {
        while (true) {
            List<String> labels = new ArrayList<>();
            List<Runnable> actions = new ArrayList<>();
            Choice[] picked = new Choice[1];

            labels.add("New game");
            actions.add(() -> {
                int slot = pickSlotForNewGame();
                if (slot > 0) {
                    picked[0] = new Choice(Choice.Kind.NEW_GAME, slot);
                }
            });
            for (int s = 1; s <= store.slotCount(); s++) {
                Optional<SaveSummary> sum = store.summary(s);
                if (sum.isPresent()) {
                    int slot = s;
                    SaveSummary summary = sum.get();
                    labels.add("Continue  Slot " + s + ": " + summary.describe());
                    actions.add(() -> {
                        if (summary.readable()) {
                            picked[0] = new Choice(Choice.Kind.LOAD, slot);
                        } else {
                            ui.danger("Slot " + slot + " can't be loaded. You can delete it to free the slot.");
                        }
                    });
                }
            }
            if (store.anyExist()) {
                labels.add("Delete a save");
                actions.add(this::deleteMenu);
            }

            int c = menu.chooseFrom(title, labels, "go back");
            if (c == Menu.CANCEL) {
                return Choice.BACK;
            }
            actions.get(c - 1).run();
            if (picked[0] != null) {
                return picked[0];
            }
        }
    }

    /** Asks which slot a new game goes in. Returns 0 if cancelled. */
    public int pickSlotForNewGame() {
        List<String> labels = slotLabels();
        int c = menu.chooseFrom("Which slot should this game save to?", labels, "cancel");
        if (c == Menu.CANCEL) {
            return 0;
        }
        if (store.exists(c) && !menu.confirm("Slot " + c + " already has a save. Overwrite it when you start?")) {
            return 0;
        }
        return c;
    }

    private void deleteMenu() {
        List<String> labels = slotLabels();
        int c = menu.chooseFrom("Which save do you want to delete?", labels, "cancel");
        if (c == Menu.CANCEL) {
            return;
        }
        if (!store.exists(c)) {
            ui.warn("Slot " + c + " is already empty.");
            return;
        }
        if (menu.confirm("Delete slot " + c + "? This can't be undone.")) {
            store.delete(c);
            ui.success("Slot " + c + " deleted.");
        }
    }

    private List<String> slotLabels() {
        List<String> labels = new ArrayList<>();
        for (int s = 1; s <= store.slotCount(); s++) {
            labels.add("Slot " + s + ": " + store.summary(s).map(SaveSummary::describe).orElse("(empty)"));
        }
        return labels;
    }

    /** Prints load warnings (skipped items etc.), if any. */
    public static void showWarnings(Ui ui, List<String> warnings) {
        if (warnings.isEmpty()) {
            return;
        }
        ui.banner(Tone.WARNING, "Some saved things couldn't be restored", warnings);
    }
}
