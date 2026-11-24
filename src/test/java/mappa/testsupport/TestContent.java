package mappa.testsupport;

import mappa.core.content.ContentRegistry;
import mappa.core.content.JsonContentLoader;

/** Loads content for tests. */
public final class TestContent {
    private static ContentRegistry small;
    private static ContentRegistry real;

    private TestContent() {
    }

    /** The small fixed data set in src/test/resources/testdata (stable across rebalances). */
    public static synchronized ContentRegistry small() {
        if (small == null) {
            small = JsonContentLoader.loadClasspath("testdata");
        }
        return small;
    }

    /** The real game data in src/main/resources/data. */
    public static synchronized ContentRegistry real() {
        if (real == null) {
            real = JsonContentLoader.loadClasspath("data");
        }
        return real;
    }
}
