package mappa.core.ui;

import java.nio.charset.StandardCharsets;

/**
 * Characters used to draw boxes, lines and bars. Unicode box drawing when the terminal
 * speaks UTF-8, plain ASCII otherwise. Force ASCII with {@code -Dmappa.ascii=true}.
 */
public record Glyphs(boolean unicode, String topLeft, String topRight, String bottomLeft, String bottomRight,
                     String horizontal, String vertical, String line, String barFull, String barEmpty,
                     String sun, String moon, String swords) {

    public static final Glyphs UNICODE = new Glyphs(true, "╔", "╗", "╚", "╝", "═", "║", "─", "█", "░", "☼", "☽", "»");
    public static final Glyphs ASCII = new Glyphs(false, "+", "+", "+", "+", "=", "|", "-", "#", ".", "*", ")", ">>");

    private static final Glyphs CURRENT = detect();

    /** The glyph set for this terminal. */
    public static Glyphs current() {
        return CURRENT;
    }

    private static Glyphs detect() {
        if (Boolean.getBoolean("mappa.ascii")) {
            return ASCII;
        }
        try {
            return StandardCharsets.UTF_8.equals(System.out.charset()) ? UNICODE : ASCII;
        } catch (RuntimeException e) {
            return ASCII;
        }
    }
}
