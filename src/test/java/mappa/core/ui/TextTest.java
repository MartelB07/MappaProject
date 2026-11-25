package mappa.core.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";

    @Test
    void visibleWidth_ignoresColorCodes() {
        assertEquals(5, Text.visibleWidth(RED + "hello" + RESET));
        assertEquals(3, Text.visibleWidth("✓ a"));
        assertEquals(0, Text.visibleWidth(""));
    }

    @Test
    void padRight_padsToVisibleWidth_neverTruncates() {
        assertEquals(10, Text.visibleWidth(Text.padRight(RED + "abc" + RESET, 10)));
        assertEquals("abcdef", Text.padRight("abcdef", 3));
    }

    @Test
    void wrap_shortText_unchanged() {
        assertEquals(List.of("one two"), Text.wrap("one two", 20));
    }

    @Test
    void wrap_breaksOnWords_andRespectsWidth() {
        List<String> lines = Text.wrap("the quick brown fox jumps over the lazy dog", 10);
        for (String l : lines) {
            assertTrue(Text.visibleWidth(l) <= 10, l);
        }
        assertEquals("the quick brown fox jumps over the lazy dog", String.join(" ", lines));
    }

    @Test
    void wrap_splitsWordsLongerThanTheWidth() {
        List<String> lines = Text.wrap("abcdefghijklmnop", 5);
        assertEquals(List.of("abcde", "fghij", "klmno", "p"), lines);
    }

    @Test
    void wrap_coloredText_closesAndReopensColorAcrossLines() {
        List<String> lines = Text.wrap(RED + "red words that need to wrap" + RESET, 9);
        assertTrue(lines.size() > 1);
        for (int i = 0; i < lines.size() - 1; i++) {
            assertTrue(lines.get(i).endsWith(RESET), "line closes its color: " + lines.get(i));
            assertTrue(lines.get(i + 1).startsWith(RED), "next line reopens it: " + lines.get(i + 1));
            assertTrue(Text.visibleWidth(lines.get(i)) <= 9);
        }
        assertFalse(Text.strip(String.join(" ", lines)).contains("\u001B"));
    }
}
