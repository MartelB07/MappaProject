package mappa.core.io;

import mappa.testsupport.ScriptedIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(2)
class MenuTest {

    @Test
    void choose_outOfRange_repromptsThenAccepts() {
        ScriptedIO io = ScriptedIO.of("9", "abc", "2");
        assertEquals(2, new Menu(io).choose("Pick", "cancel", "A", "B"));
        assertTrue(io.outputContains("Invalid input"));
    }

    @Test
    void choose_zeroWithCancel_returnsCancel() {
        assertEquals(Menu.CANCEL, new Menu(ScriptedIO.of("0")).choose("Pick", "cancel", "A"));
    }

    @Test
    void choose_zeroWithoutCancel_isInvalid() {
        assertEquals(1, new Menu(ScriptedIO.of("0", "1")).choose("Pick", null, "A"));
    }

    @Test
    @Tag("regression")
    @DisplayName("D-04: negative amounts are rejected")
    void readInt_negative_rejected() {
        ScriptedIO io = ScriptedIO.of("-5", "3");
        assertEquals(3, new Menu(io).readInt("n: ", 1, 5, true));
        assertTrue(io.outputContains("Enter a number from 1 to 5"));
    }

    @Test
    void readInt_nonNumeric_rejected() {
        ScriptedIO io = ScriptedIO.of("x", " 4 ");
        assertEquals(4, new Menu(io).readInt("n: ", 1, 5, false));
        assertTrue(io.outputContains("Please enter a number"));
    }

    @Test
    void confirm_parsesYesAndNo() {
        assertTrue(new Menu(ScriptedIO.of("1")).confirm("Sure?"));
        assertFalse(new Menu(ScriptedIO.of("2")).confirm("Sure?"));
    }

    @Test
    void readName_trimsAndValidatesLength() {
        assertEquals("Bob", new Menu(ScriptedIO.of("   ", "x".repeat(25), "  Bob  ")).readName("Name: ", 20));
    }

    @Test
    void scriptRunsOut_throwsInsteadOfHanging() {
        assertThrows(InputClosedException.class, () -> new Menu(ScriptedIO.of("x")).choose("Pick", null, "A"));
    }
}
