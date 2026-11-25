package mappa.core.io;

/**
 * All console input/output goes through this interface so game logic can be
 * driven by scripted input in tests (see ScriptedIO in the test sources).
 */
public interface GameIO {

    /**
     * Reads one line of input (without the line terminator).
     *
     * @throws InputClosedException when no more input is available
     */
    String readLine();

    void print(String text);

    default void println(String text) {
        print(text + System.lineSeparator());
    }

    default void println() {
        print(System.lineSeparator());
    }

    /**
     * Wipes the screen (and scroll-back) where the output supports it. Implementations
     * call this after each input, so the player only sees what happened since their last
     * input. The default does nothing.
     */
    default void clear() {
    }
}
