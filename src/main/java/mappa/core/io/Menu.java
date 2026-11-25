package mappa.core.io;

import mappa.core.ui.Text;
import mappa.core.ui.Ui;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Prompt helpers. Every numeric input in the game goes through here, so range
 * checking (no negatives, no out-of-range picks) lives in one place.
 *
 * <p>Options are shown as {@code [1] Label} with the number in the mode's accent color;
 * long lists switch to two aligned columns.</p>
 */
public class Menu {
    public static final int CANCEL = 0;
    /** Lists longer than this are shown in two columns (when they fit). */
    static final int TWO_COLUMN_THRESHOLD = 5;
    private static final String PROMPT = "> ";

    private final GameIO io;
    private final Ui ui;

    public Menu(GameIO io) {
        this(new Ui(io, Ansi.Color.CYAN));
    }

    public Menu(Ui ui) {
        this.ui = ui;
        this.io = ui.io();
    }

    public GameIO io() {
        return io;
    }

    public Ui ui() {
        return ui;
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
                io.println(Ansi.bold(title));
            }
            for (String line : renderOptions(options, cancelLabel)) {
                io.println(line);
            }
            io.print(ui.accent(PROMPT));
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
            ui.warn("Invalid input. Pick a number from the list.");
        }
    }

    /** Same as {@link #chooseFrom} but with options passed inline. */
    public int choose(String title, String cancelLabel, String... options) {
        return chooseFrom(title, List.of(options), cancelLabel);
    }

    /**
     * The option lines: "[1] Label" in one column, or two aligned columns (filled top to
     * bottom) for long lists. "[0] cancel" always goes last on its own line.
     */
    List<String> renderOptions(List<String> options, String cancelLabel) {
        int numberWidth = String.valueOf(options.size()).length();
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            cells.add(tag(i + 1, numberWidth) + " " + options.get(i));
        }
        List<String> lines = new ArrayList<>();
        int widest = 0;
        for (String c : cells) {
            widest = Math.max(widest, Text.visibleWidth(c));
        }
        int gap = 3;
        boolean twoColumns = cells.size() > TWO_COLUMN_THRESHOLD && 1 + widest * 2 + gap <= ui.maxWidth();
        if (twoColumns) {
            int rows = (cells.size() + 1) / 2;
            for (int r = 0; r < rows; r++) {
                String left = cells.get(r);
                int ri = r + rows;
                lines.add(ri < cells.size()
                        ? " " + Text.padRight(left, widest) + Text.spaces(gap) + cells.get(ri)
                        : " " + left);
            }
        } else {
            for (String c : cells) {
                lines.add(" " + c);
            }
        }
        if (cancelLabel != null) {
            lines.add(" " + tag(0, numberWidth) + " " + capitalize(cancelLabel));
        }
        return lines;
    }

    private String tag(int n, int width) {
        String num = String.valueOf(n);
        return ui.accent("[" + Text.spaces(width - num.length()) + num + "]");
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Reads an integer in [min, max]. If {@code allowCancel}, 0 returns {@link #CANCEL}
     * even if min > 0.
     */
    public int readInt(String prompt, int min, int max, boolean allowCancel) {
        while (true) {
            io.println();
            io.println(Ansi.bold(prompt.trim()));
            io.print(ui.accent(PROMPT));
            OptionalInt n = parseInt(io.readLine());
            if (n.isPresent()) {
                int v = n.getAsInt();
                if (allowCancel && v == CANCEL) {
                    return CANCEL;
                }
                if (v >= min && v <= max) {
                    return v;
                }
                ui.warn("Enter a number from " + min + " to " + max + (allowCancel ? " (0 to cancel)." : "."));
            } else {
                ui.warn("Please enter a number.");
            }
        }
    }

    public boolean confirm(String question) {
        return choose(question, null, "Yes", "No") == 1;
    }

    public void pause() {
        io.println();
        io.print(Ansi.color("Press enter to continue...", Ansi.Color.GRAY));
        io.readLine();
    }

    /** Reads a non-blank name of at most {@code maxLength} characters. */
    public String readName(String prompt, int maxLength) {
        while (true) {
            io.println();
            io.println(Ansi.bold(prompt.trim()));
            io.print(ui.accent(PROMPT));
            String name = io.readLine().trim();
            if (name.isEmpty()) {
                ui.warn("Sorry, your name is too short. Try again.");
            } else if (name.length() > maxLength) {
                ui.warn("Sorry, your name is too long. Try again.");
            } else {
                return name;
            }
        }
    }
}
