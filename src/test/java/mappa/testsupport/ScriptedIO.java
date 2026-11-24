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
        String line = lines.poll();
        out.append(line).append('\n');
        return line;
    }

    @Override
    public void print(String text) {
        out.append(text);
    }

    public String output() {
        return out.toString();
    }

    public boolean outputContains(String text) {
        return out.indexOf(text) >= 0;
    }

    public int remainingInput() {
        return lines.size();
    }

    private String tail() {
        int from = Math.max(0, out.length() - 1500);
        return out.substring(from);
    }
}
