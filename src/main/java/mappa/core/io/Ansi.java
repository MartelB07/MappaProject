package mappa.core.io;

import java.util.Locale;

/**
 * Terminal colors. Disable with -Dmappa.noColor=true (or the NO_COLOR env var).
 * Game logic must never depend on the length of these strings.
 */
public final class Ansi {
    public enum Color {
        RED("31"), GREEN("32"), YELLOW("33"), BLUE("34"), PURPLE("35"), CYAN("36"), WHITE("37"),
        GRAY("90"), GOLD("33"), BLACK("30"), DEFAULT("0");

        private final String code;

        Color(String code) {
            this.code = code;
        }

        public static Color parse(String name) {
            if (name == null || name.isBlank()) {
                return DEFAULT;
            }
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        }
    }

    private static final String RESET = "\u001B[0m";
    private static final boolean ENABLED =
            !Boolean.getBoolean("mappa.noColor") && System.getenv("NO_COLOR") == null;

    private Ansi() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static String color(String text, Color color) {
        if (!ENABLED || color == null || color == Color.DEFAULT) {
            return text;
        }
        return "\u001B[" + color.code + "m" + text + RESET;
    }

    /** Bold text, optionally colored. */
    public static String bold(String text, Color color) {
        if (!ENABLED) {
            return text;
        }
        String code = color == null || color == Color.DEFAULT ? "1" : "1;" + color.code;
        return "\u001B[" + code + "m" + text + RESET;
    }

    public static String bold(String text) {
        return bold(text, null);
    }

    /** Green above 50%, yellow above 25%, red otherwise. */
    public static String health(int current, int max) {
        Color c;
        if (current <= max * 0.25) {
            c = Color.RED;
        } else if (current <= max * 0.50) {
            c = Color.YELLOW;
        } else {
            c = Color.GREEN;
        }
        return color(current + "/" + max, c);
    }
}
