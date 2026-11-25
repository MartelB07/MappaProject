package mappa.testsupport;

import mappa.core.io.GameIO;
import mappa.core.io.InputClosedException;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * GameIO fed by a fixed list of input lines. Records all output. Running out of input
 * throws {@link InputClosedException}, so a menu that would loop forever fails fast.
 */
public class ScriptedIO implements GameIO {
    private final Deque<String> lines;
    private final StringBuilder out = new StringBuilder();
    private int lastClear;
    private int clears;
    private final java.util.List<String> screensBeforeInput = new java.util.ArrayList<>();

    public ScriptedIO(String... lines) {
        this.lines = new ArrayDeque<>(Arrays.asList(lines));
    }

    public static ScriptedIO of(String... lines) {
        return new ScriptedIO(lines);
    }

    public ScriptedIO then(String... more) {
        lines.addAll(Arrays.asList(more));
        return this;
    }

    @Override
    public String readLine() {
        if (lines.isEmpty()) {
            throw new InputClosedException("Script ran out of input. Output so far:\n" + tail());
        }
        screensBeforeInput.add(screen());
        String line = lines.poll();
        out.append(line).append('\n');
        clear(); // like the real console: the screen is wiped after every input
        return line;
    }

    @Override
    public void print(String text) {
        out.append(text);
    }

    @Override
    public void clear() {
        lastClear = out.length();
        clears++;
    }

    /** Everything ever printed (clears don't remove anything from this log). */
    public String output() {
        return out.toString();
    }

    public boolean outputContains(String text) {
        return out.indexOf(text) >= 0;
    }

    /** What the player would currently see: output since the last screen clear, colors removed. */
    public String screen() {
        return out.substring(lastClear).replaceAll("\u001B\\[[0-9;]*[A-Za-z]", "");
    }

    /** Full log with colors removed (handy for asserting on styled text). */
    public String plainOutput() {
        return out.toString().replaceAll("\u001B\\[[0-9;]*[A-Za-z]", "");
    }

    public int clears() {
        return clears;
    }

    /**
     * What was on screen when input number {@code index} (0-based) was asked for, i.e. everything
     * printed after the previous input. Colors removed.
     */
    public String screenBefore(int index) {
        return screensBeforeInput.get(index);
    }

    public int remainingInput() {
        return lines.size();
    }

    private String tail() {
        int from = Math.max(0, out.length() - 1500);
        return out.substring(from);
    }
}
