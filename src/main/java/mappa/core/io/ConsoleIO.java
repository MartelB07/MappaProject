package mappa.core.io;

import mappa.core.ui.Glyphs;
import mappa.core.ui.Text;

import java.io.Console;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * The single owner of System.in for the whole program.
 *
 * <p>After every line the player types, the screen is wiped (including scroll-back), so
 * only what happened since that input is visible. This needs a real terminal; in IDE run
 * windows and pipes a thin divider is printed instead. Override with
 * {@code -Dmappa.clear=true|false}.</p>
 */
public class ConsoleIO implements GameIO {
    /** Cursor home, clear screen, clear scroll-back. */
    static final String CLEAR_SEQUENCE = "\u001B[H\u001B[2J\u001B[3J";

    private final Scanner scanner;
    private final PrintStream out;
    private final boolean clearScreen;

    public ConsoleIO() {
        this(System.in, System.out, detectClear());
    }

    public ConsoleIO(InputStream in, PrintStream out, boolean clearScreen) {
        this.scanner = new Scanner(in);
        this.out = out;
        this.clearScreen = clearScreen;
    }

    public boolean clearsScreen() {
        return clearScreen;
    }

    @Override
    public String readLine() {
        String line;
        try {
            line = scanner.nextLine();
        } catch (NoSuchElementException e) {
            throw new InputClosedException("Console input closed");
        }
        clear();
        return line;
    }

    @Override
    public void print(String text) {
        out.print(text);
        out.flush();
    }

    @Override
    public void clear() {
        if (clearScreen) {
            out.print(CLEAR_SEQUENCE);
        } else {
            out.println();
            out.println(Ansi.color(Text.repeat(Glyphs.current().line(), 40), Ansi.Color.GRAY));
        }
        out.flush();
    }

    /** Clear only in a real terminal with colors on, unless -Dmappa.clear says otherwise. */
    static boolean detectClear() {
        String forced = System.getProperty("mappa.clear");
        if (forced != null) {
            return Boolean.parseBoolean(forced);
        }
        return Ansi.enabled() && isTerminal();
    }

    private static boolean isTerminal() {
        Console console = System.console();
        if (console == null) {
            return false;
        }
        try { // Console.isTerminal() exists from JDK 22; the project compiles for 21.
            Method m = Console.class.getMethod("isTerminal");
            return (Boolean) m.invoke(console);
        } catch (ReflectiveOperationException e) {
            return true; // JDK 21: a non-null console means a terminal
        }
    }
}
