package mappa.core.inventory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletTest {
    @Test
    @Tag("regression")
    @DisplayName("G-01: spending more than you have changes nothing")
    void trySpend_insufficient_returnsFalseAndUnchanged() {
        Wallet w = new Wallet(10);
        assertFalse(w.trySpend(11));
        assertEquals(10, w.gold());
        assertTrue(w.trySpend(10));
        assertEquals(0, w.gold());
    }

    @Test
    void add_negative_throws() {
        assertThrows(IllegalArgumentException.class, () -> new Wallet().add(-1));
        assertThrows(IllegalArgumentException.class, () -> new Wallet().trySpend(-1));
    }
}
