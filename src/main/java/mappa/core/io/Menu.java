package mappa.core.io;

import java.util.List;
import java.util.OptionalInt;

/**
 * Prompt helpers. Every numeric input in the game goes through here, so range
 * checking (no negatives, no out-of-range picks) lives in one place.
 */
public class Menu {
    public static final int CANCEL = 0;

    private final GameIO io;

    public Menu(GameIO io) {
        this.io = io;
    }

    public GameIO io() {
        return io;
    }

    /** Parses a trimmed integer, or empty if the text is not a number. */
    public static OptionalInt parseInt(String text) {
        if (text == null) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /**
     * Shows a numbered list and returns the chosen 1-based index, or {@link #CANCEL}
     * (0) when {@code cancelLabel} is non-null and the player enters 0.
     * Re-prompts until the input is valid.
     */
    public int chooseFrom(String title, List<String> options, String cancelLabel) {
        while (true) {
            if (title != null && !title.isEmpty()) {
                io.println();
                io.println(title);
            }
            for (int i = 0; i < options.size(); i++) {
                io.println((i + 1) + ". " + options.get(i));
            }
            String prompt = cancelLabel == null ? "Enter choice: " : "Enter choice (enter 0 to " + cancelLabel + "): ";
            io.print(prompt);
            OptionalInt n = parseInt(io.readLine());
            if (n.isPresent()) {
                int v = n.getAsInt();
                if (v >= 1 && v <= options.size()) {
                    return v;
                }
                if (v == CANCEL && cancelLabel != null) {
                    return CANCEL;
                }
            }
            io.println();
            io.println("Invalid input. Try again.");
        }
    }

    /** Same as {@link #chooseFrom} but with options passed inline. */
    public int choose(String title, String cancelLabel, String... options) {
        return chooseFrom(title, List.of(options), cancelLabel);
    }

    /**
     * Reads an integer in [min, max]. If {@code allowCancel}, 0 returns {@link #CANCEL}
     * even if min > 0.
     */
    public int readInt(String prompt, int min, int max, boolean allowCancel) {
        while (true) {
            io.print(prompt);
            OptionalInt n = parseInt(io.readLine());
            if (n.isPresent()) {
                int v = n.getAsInt();
                if (allowCancel && v == CANCEL) {
                    return CANCEL;
                }
                if (v >= min && v <= max) {
                    return v;
                }
                io.println("Enter a number from " + min + " to " + max + (allowCancel ? " (0 to cancel)." : "."));
            } else {
                io.println("Please enter a number.");
            }
        }
    }

    public boolean confirm(String question) {
        return choose(question, null, "Yes", "No") == 1;
    }

    public void pause() {
        io.print("Press enter to continue...");
        io.readLine();
    }

    /** Reads a non-blank name of at most {@code maxLength} characters. */
    public String readName(String prompt, int maxLength) {
        while (true) {
            io.println();
            io.print(prompt);
            String name = io.readLine().trim();
            if (name.isEmpty()) {
                io.println("Sorry, your name is too short. Try again.");
            } else if (name.length() > maxLength) {
                io.println("Sorry, your name is too long. Try again.");
            } else {
                return name;
            }
        }
    }
}
