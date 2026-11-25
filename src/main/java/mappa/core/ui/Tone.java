package mappa.core.ui;

import mappa.core.io.Ansi;

/**
 * The kind of message being shown. Each tone has a color and a symbol. Symbols are
 * single-column characters (never emoji), so boxes stay aligned in every terminal.
 */
public enum Tone {
    INFO(Ansi.Color.CYAN, "•", "-"),
    SUCCESS(Ansi.Color.GREEN, "✓", "+"),
    WARNING(Ansi.Color.YELLOW, "▲", "!"),
    DANGER(Ansi.Color.RED, "✗", "x"),
    REWARD(Ansi.Color.GOLD, "★", "*"),
    MAGIC(Ansi.Color.PURPLE, "✦", "~"),
    MUTED(Ansi.Color.GRAY, "·", "-");

    private final Ansi.Color color;
    private final String unicode;
    private final String ascii;

    Tone(Ansi.Color color, String unicode, String ascii) {
        this.color = color;
        this.unicode = unicode;
        this.ascii = ascii;
    }

    public Ansi.Color color() {
        return color;
    }

    public String symbol(Glyphs glyphs) {
        return glyphs.unicode() ? unicode : ascii;
    }
}
