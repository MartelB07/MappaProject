package mappa.core.ui;

import mappa.core.io.Ansi;
import mappa.core.io.GameIO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Styled output for one game mode: boxed banners for big moments, one-line events,
 * status headers and bars. Every box is sized to its contents and every line inside
 * it is padded to the same visible width, so borders always line up.
 */
public final class Ui {
    /** Space between a box border and its text, on each side. */
    private static final int PAD = 2;

    private final GameIO io;
    private final Ansi.Color accent;
    private final Glyphs glyphs;
    private final int maxWidth;

    public Ui(GameIO io, Ansi.Color accent) {
        this(io, accent, Glyphs.current(), Text.terminalWidth());
    }

    public Ui(GameIO io, Ansi.Color accent, Glyphs glyphs, int maxWidth) {
        this.io = io;
        this.accent = accent == null ? Ansi.Color.DEFAULT : accent;
        this.glyphs = glyphs;
        this.maxWidth = Math.max(Text.MIN_WIDTH, maxWidth);
    }

    /** Same output and glyphs, different accent color (e.g. a mode's own color). */
    public Ui withAccent(Ansi.Color newAccent) {
        return new Ui(io, newAccent, glyphs, maxWidth);
    }

    public GameIO io() {
        return io;
    }

    public Glyphs glyphs() {
        return glyphs;
    }

    public Ansi.Color accent() {
        return accent;
    }

    public int maxWidth() {
        return maxWidth;
    }

    public String accent(String text) {
        return Ansi.color(text, accent);
    }

    public String symbol(Tone tone) {
        return tone.symbol(glyphs);
    }

    // ------------------------------------------------------------------ banners

    /** Prints a boxed banner: a bold title line (with the tone's symbol) and optional body lines. */
    public void banner(Tone tone, String title, String... lines) {
        banner(tone, title, Arrays.asList(lines));
    }

    public void banner(Tone tone, String title, List<String> lines) {
        io.println();
        for (String l : renderBanner(tone, title, lines)) {
            io.println(l);
        }
    }

    /** Banner with a custom symbol instead of the tone's (e.g. a sun for a new day). */
    public void banner(Tone tone, String symbol, String title, List<String> lines) {
        io.println();
        for (String l : renderBanner(tone, symbol, title, lines)) {
            io.println(l);
        }
    }

    /** The banner as lines (used by {@link #banner} and by tests that check alignment). */
    public List<String> renderBanner(Tone tone, String title, List<String> lines) {
        return renderBanner(tone, symbol(tone), title, lines);
    }

    public List<String> renderBanner(Tone tone, String symbol, String title, List<String> lines) {
        int innerMax = maxWidth - 2 - 2 * PAD; // borders + padding
        String indent = Text.spaces(Text.visibleWidth(symbol) + 2);

        List<String> content = new ArrayList<>();
        content.addAll(Text.wrap(Ansi.bold(symbol + "  " + title, tone.color()), innerMax));
        for (String line : lines) {
            for (String part : Text.wrap(line, innerMax - indent.length())) {
                content.add(indent + part);
            }
        }
        int inner = 0;
        for (String c : content) {
            inner = Math.max(inner, Text.visibleWidth(c));
        }

        String h = Text.repeat(glyphs.horizontal(), inner + 2 * PAD);
        String v = Ansi.color(glyphs.vertical(), tone.color());
        String pad = Text.spaces(PAD);
        List<String> out = new ArrayList<>();
        out.add(Ansi.color(glyphs.topLeft() + h + glyphs.topRight(), tone.color()));
        for (String c : content) {
            out.add(v + pad + Text.padRight(c, inner) + pad + v);
        }
        out.add(Ansi.color(glyphs.bottomLeft() + h + glyphs.bottomRight(), tone.color()));
        return out;
    }

    // ------------------------------------------------------------------ one-liners

    /** One colored line with the tone's symbol, e.g. "✓ You won the fight!". */
    public void event(Tone tone, String text) {
        io.println(Ansi.color(symbol(tone) + " " + text, tone.color()));
    }

    public void info(String text) {
        event(Tone.INFO, text);
    }

    public void success(String text) {
        event(Tone.SUCCESS, text);
    }

    public void warn(String text) {
        event(Tone.WARNING, text);
    }

    public void danger(String text) {
        event(Tone.DANGER, text);
    }

    public void reward(String text) {
        event(Tone.REWARD, text);
    }

    public void muted(String text) {
        event(Tone.MUTED, text);
    }

    /** Plain line (no symbol), for neutral narration. */
    public void say(String text) {
        io.println(text);
    }

    // ------------------------------------------------------------------ headers & bars

    /**
     * A status strip: "── Title ─────" then the status lines, then a closing rule.
     * The rules stretch to the widest line so the strip looks like one block.
     */
    public void header(String title, String... statusLines) {
        header(title, Arrays.asList(statusLines));
    }

    public void header(String title, List<String> statusLines) {
        int width = Text.visibleWidth(title) + 6;
        for (String s : statusLines) {
            width = Math.max(width, Text.visibleWidth(s) + 1);
        }
        width = Math.min(Math.max(width, 40), maxWidth);
        String line = glyphs.line();
        String top = line + line + " " + Ansi.bold(title, accent) + " ";
        io.println();
        io.println(accent(line + line + " ") + Ansi.bold(title, accent) + accent(" "
                + Text.repeat(line, width - Text.visibleWidth(top))));
        for (String s : statusLines) {
            for (String part : Text.wrap(s, width - 1)) {
                io.println(" " + part);
            }
        }
        io.println(accent(Text.repeat(line, width)));
    }

    /** "[██████░░░░] 72/100", colored green / yellow / red by how full it is. */
    public String bar(int current, int max, int width) {
        return bar(current, max, width, null) + " " + current + "/" + max;
    }

    /** The bar only (no numbers). A null color picks green / yellow / red by fill. */
    public String bar(int current, int max, int width, Ansi.Color fixed) {
        int safeMax = Math.max(1, max);
        int clamped = Math.max(0, Math.min(current, safeMax));
        int filled = (int) Math.round((double) clamped / safeMax * width);
        if (clamped > 0 && filled == 0) {
            filled = 1;
        }
        Ansi.Color c = fixed != null ? fixed : healthColor(clamped, safeMax);
        return "[" + Ansi.color(Text.repeat(glyphs.barFull(), filled), c)
                + Ansi.color(Text.repeat(glyphs.barEmpty(), width - filled), Ansi.Color.GRAY) + "]";
    }

    static Ansi.Color healthColor(int current, int max) {
        if (current <= max * 0.25) {
            return Ansi.Color.RED;
        }
        if (current <= max * 0.50) {
            return Ansi.Color.YELLOW;
        }
        return Ansi.Color.GREEN;
    }

    /** A full-width thin rule in the accent color. */
    public void divider() {
        io.println(accent(Text.repeat(glyphs.line(), Math.min(40, maxWidth))));
    }

    /** The MAPPA title shown on the launcher. */
    public void title(String subtitle) {
        String[] logo = glyphs.unicode()
                ? new String[]{
                "█▀▄▀█ ▄▀█ █▀█ █▀█ ▄▀█",
                "█ ▀ █ █▀█ █▀▀ █▀▀ █▀█"}
                : new String[]{
                "M   M  AAA  PPPP  PPPP   AAA",
                "MM MM A   A P   P P   P A   A",
                "M M M AAAAA PPPP  PPPP  AAAAA",
                "M   M A   A P     P     A   A"};
        List<String> lines = new ArrayList<>();
        for (String l : logo) {
            lines.add(Ansi.bold(l, accent));
        }
        lines.add("");
        lines.add(subtitle);
        int inner = 0;
        for (String l : lines) {
            inner = Math.max(inner, Text.visibleWidth(l));
        }
        String h = Text.repeat(glyphs.horizontal(), inner + 2 * PAD);
        String v = accent(glyphs.vertical());
        io.println();
        io.println(accent(glyphs.topLeft() + h + glyphs.topRight()));
        for (String l : lines) {
            int left = (inner - Text.visibleWidth(l)) / 2;
            io.println(v + Text.spaces(PAD + left) + Text.padRight(l, inner - left) + Text.spaces(PAD) + v);
        }
        io.println(accent(glyphs.bottomLeft() + h + glyphs.bottomRight()));
    }
}
