package mappa.core.io;

/** Thrown when the input stream ends (e.g. Ctrl+Z / Ctrl+D, or a test script ran out). */
public class InputClosedException extends RuntimeException {
    public InputClosedException(String message) {
        super(message);
    }
}
