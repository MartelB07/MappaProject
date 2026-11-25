package mappa.core.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Layout helpers that understand ANSI color codes, so colored text lines up exactly
 * like plain text. All widths are "visible" widths: what the player actually sees.
 */
public final class Text {
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*[A-Za-z]");
    private static final String RESET = "\u001B[0m";
    public static final int DEFAULT_WIDTH = 80;
    public static final int MIN_WIDTH = 30;

    private Text() {
    }

    public static String strip(String s) {
        return ANSI.matcher(s).replaceAll("");
    }

    /** Characters the player sees (ANSI codes don't count). */
    public static int visibleWidth(String s) {
        String plain = strip(s);
        return plain.codePointCount(0, plain.length());
    }

    public static String spaces(int n) {
        return n <= 0 ? "" : " ".repeat(n);
    }

    public static String repeat(String s, int n) {
        return n <= 0 ? "" : s.repeat(n);
    }

    /** Pads with spaces on the right to exactly {@code width} visible characters (never truncates). */
    public static String padRight(String s, int width) {
        return s + spaces(width - visibleWidth(s));
    }

    /** Terminal width from the COLUMNS environment variable, or 80. */
    public static int terminalWidth() {
        try {
            String cols = System.getenv("COLUMNS");
            if (cols != null) {
                return Math.max(MIN_WIDTH, Integer.parseInt(cols.trim()));
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return DEFAULT_WIDTH;
    }

    /**
     * Word-wraps {@code s} so no line is wider than {@code width} visible characters.
     * Colors that span a line break are closed at the end of the line and reopened on the
     * next, so borders drawn after a line are never tinted. Words longer than the width are split.
     */
    public static List<String> wrap(String s, int width) {
        width = Math.max(1, width);
        List<String> lines = new ArrayList<>();
        if (visibleWidth(s) <= width) {
            lines.add(s);
            return lines;
        }
        StringBuilder line = new StringBuilder();
        int lineWidth = 0;
        String active = null; // SGR code currently in effect
        for (String word : s.split(" ", -1)) {
            int w = visibleWidth(word);
            if (lineWidth > 0 && lineWidth + 1 + w > width) {
                lines.add(close(line, active));
                line = new StringBuilder(active == null ? "" : active);
                lineWidth = 0;
            }
            if (w > width) { // split a word that can never fit
                for (String piece : splitVisible(word, width)) {
                    if (lineWidth > 0) {
                        lines.add(close(line, active));
                        line = new StringBuilder(active == null ? "" : active);
                        lineWidth = 0;
                    }
                    line.append(piece);
                    lineWidth = visibleWidth(piece);
                    active = trackActive(piece, active);
                }
                continue;
            }
            if (lineWidth > 0) {
                line.append(' ');
                lineWidth++;
            }
            line.append(word);
            lineWidth += w;
            active = trackActive(word, active);
        }
        lines.add(line.toString());
        return lines;
    }

    private static String close(StringBuilder line, String active) {
        return active == null ? line.toString() : line + RESET;
    }

    /** The color left "open" after {@code text}, given the one open before it. */
    private static String trackActive(String text, String active) {
        Matcher m = ANSI.matcher(text);
        while (m.find()) {
            String code = m.group();
            active = code.equals(RESET) || code.equals("\u001B[m") ? null : code;
        }
        return active;
    }

    private static List<String> splitVisible(String word, int width) {
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        int count = 0;
        Matcher m = ANSI.matcher(word);
        int i = 0;
        while (i < word.length()) {
            if (m.find(i) && m.start() == i) {
                piece.append(m.group());
                i = m.end();
                continue;
            }
            int cp = word.codePointAt(i);
            if (count == width) {
                pieces.add(piece.toString());
                piece = new StringBuilder();
                count = 0;
            }
            piece.appendCodePoint(cp);
            count++;
            i += Character.charCount(cp);
        }
        if (piece.length() > 0) {
            pieces.add(piece.toString());
        }
        return pieces;
    }
}
