package mappa.core.save;

/** A save file could not be read, written, or understood. The message is shown to the player. */
public class SaveException extends RuntimeException {
    public SaveException(String message) {
        super(message);
    }

    public SaveException(String message, Throwable cause) {
        super(message, cause);
    }
}
