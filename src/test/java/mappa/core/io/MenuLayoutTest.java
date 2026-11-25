package mappa.core.io;

import mappa.core.ui.Glyphs;
import mappa.core.ui.Text;
import mappa.core.ui.Ui;
import mappa.testsupport.ScriptedIO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuLayoutTest {
    private final Menu menu = new Menu(new Ui(new ScriptedIO(), Ansi.Color.GREEN, Glyphs.ASCII, 80));

    @Test
    void shortList_oneColumn_withCancelLast() {
        List<String> lines = menu.renderOptions(List.of("Attack", "Heal"), "go back");
        assertEquals(List.of(" [1] Attack", " [2] Heal", " [0] Go back"), lines.stream().map(Text::strip).toList());
    }

    @Test
    void longList_twoAlignedColumns_filledTopToBottom() {
        List<String> lines = menu.renderOptions(List.of("Open Stats", "Open Inventory", "Shop", "Travel", "Fight",
                "Look for Chest", "Save Game"), "save and exit");
        List<String> plain = lines.stream().map(Text::strip).toList();
        assertEquals(5, plain.size(), "4 rows + cancel");
        assertTrue(plain.get(0).startsWith(" [1] Open Stats"));
        assertTrue(plain.get(0).contains("[5] Fight"));
        int column = plain.get(0).indexOf("[5]");
        assertEquals(column, plain.get(1).indexOf("[6]"));
        assertEquals(column, plain.get(2).indexOf("[7]"));
        assertEquals(" [4] Travel", plain.get(3));
        assertEquals(" [0] Save and exit", plain.get(4));
    }

    @Test
    void numbersPadded_whenTenOrMoreOptions() {
        List<String> lines = menu.renderOptions(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), null);
        assertTrue(Text.strip(lines.get(0)).startsWith(" [ 1] a"));
    }
}
