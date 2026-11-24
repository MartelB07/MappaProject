package mappa.core.content;

import java.util.List;

/** Content failed to load or validate. The message lists every problem found. */
public class ContentException extends RuntimeException {
    private final List<String> problems;

    public ContentException(String message) {
        this(List.of(message));
    }

    public ContentException(List<String> problems) {
        super("Game content has " + problems.size() + " problem(s):\n  - " + String.join("\n  - ", problems));
        this.problems = List.copyOf(problems);
    }

    public ContentException(String message, Throwable cause) {
        super(message, cause);
        this.problems = List.of(message);
    }

    public List<String> problems() {
        return problems;
    }
}
