package mappa.core.ui;

import mappa.core.io.Ansi;
import mappa.testsupport.ScriptedIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTest {
    private static Ui ui(Glyphs glyphs, int width) {
        return new Ui(new ScriptedIO(), Ansi.Color.GREEN, glyphs, width);
    }

    /** Every line of a box must be exactly as wide as the top border. */
    private static int assertAligned(List<String> box) {
        int width = Text.visibleWidth(box.get(0));
        for (String line : box) {
            assertEquals(width, Text.visibleWidth(line), "misaligned line: [" + Text.strip(line) + "]");
        }
        return width;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Banners resize to fit: +40 gold vs +10000 gold")
    void banner_resizesToContent(boolean unicode) {
        Ui ui = ui(unicode ? Glyphs.UNICODE : Glyphs.ASCII, 80);
        int small = assertAligned(ui.renderBanner(Tone.REWARD, "LEVEL UP!", List.of("Gold +40")));
        int big = assertAligned(ui.renderBanner(Tone.REWARD, "LEVEL UP!",
                List.of("Health +20   Damage +4   Gold +10000000")));
        assertTrue(big > small, "box grows with its contents");
    }

    @Test
    void banner_titleWiderThanBody_stillAligned() {
        assertAligned(ui(Glyphs.UNICODE, 80).renderBanner(Tone.DANGER,
                "A very long banner title that is wider than the body", List.of("x")));
    }

    @Test
    void banner_coloredBodyLines_stayAligned() {
        assertAligned(ui(Glyphs.UNICODE, 80).renderBanner(Tone.REWARD, "Loot", List.of(
                Ansi.color("Epic Sword", Ansi.Color.PURPLE) + "  (War +40)",
                Ansi.bold("Legendary Staff", Ansi.Color.GOLD),
                "plain")));
    }

    @Test
    void banner_longLine_wrapsInsideMaxWidth() {
        String longLine = "word ".repeat(60).trim();
        List<String> box = ui(Glyphs.UNICODE, 50).renderBanner(Tone.INFO, "Wrap", List.of(longLine));
        int width = assertAligned(box);
        assertTrue(width <= 50, "box width " + width);
        assertTrue(box.size() > 4, "the long line was wrapped onto several rows");
    }

    @Test
    void banner_ascii_usesOnlyAscii() {
        for (String line : ui(Glyphs.ASCII, 80).renderBanner(Tone.SUCCESS, "Done", List.of("ok"))) {
            assertTrue(Text.strip(line).chars().allMatch(c -> c < 128), line);
        }
    }

    @Test
    void banner_printsToIo() {
        ScriptedIO io = new ScriptedIO();
        new Ui(io, Ansi.Color.GREEN, Glyphs.UNICODE, 80).banner(Tone.REWARD, "LEVEL UP!", "Gold +40");
        assertTrue(io.plainOutput().contains("★  LEVEL UP!"));
        assertTrue(io.plainOutput().contains("╔"));
    }

    @Test
    void bar_fillMatchesValue_andClamps() {
        Ui ui = ui(Glyphs.ASCII, 80);
        assertEquals("[#####.....]", Text.strip(ui.bar(5, 10, 10, null)));
        assertEquals("[##########]", Text.strip(ui.bar(50, 10, 10, null)));
        assertEquals("[..........]", Text.strip(ui.bar(-3, 10, 10, null)));
        assertEquals("[#.........]", Text.strip(ui.bar(1, 100, 10, null)), "anything above 0 shows one block");
        assertTrue(Text.strip(ui.bar(72, 100, 10)).endsWith(" 72/100"));
    }

    @Test
    void bar_colorThresholds() {
        assertEquals(Ansi.Color.GREEN, Ui.healthColor(60, 100));
        assertEquals(Ansi.Color.YELLOW, Ui.healthColor(50, 100));
        assertEquals(Ansi.Color.RED, Ui.healthColor(25, 100));
    }

    @Test
    void header_rulesMatchWidestLine() {
        ScriptedIO io = new ScriptedIO();
        new Ui(io, Ansi.Color.GREEN, Glyphs.ASCII, 80).header("Old Town", "short",
                "a much longer status line that sets the width of the strip");
        String[] lines = io.plainOutput().strip().split("\\R");
        assertEquals(Text.visibleWidth(lines[0]), Text.visibleWidth(lines[lines.length - 1]));
    }
}
