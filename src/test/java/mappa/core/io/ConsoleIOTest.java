package mappa.core.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsoleIOTest {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    private ConsoleIO console(String input, boolean clear) {
        return new ConsoleIO(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(bytes, true, StandardCharsets.UTF_8), clear);
    }

    private String out() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    void clearEnabled_wipesScreenAfterEveryInput() {
        ConsoleIO io = console("a\nb\n", true);
        assertEquals("a", io.readLine());
        assertEquals("b", io.readLine());
        assertEquals(2, out().split(java.util.regex.Pattern.quote(ConsoleIO.CLEAR_SEQUENCE), -1).length - 1);
    }

    @Test
    void clearDisabled_printsNoClearCodes() {
        ConsoleIO io = console("a\n", false);
        io.readLine();
        assertFalse(out().contains("\u001B[2J"));
    }

    @Test
    void inputClosed_throws() {
        ConsoleIO io = console("", true);
        assertThrows(InputClosedException.class, io::readLine);
    }
}
