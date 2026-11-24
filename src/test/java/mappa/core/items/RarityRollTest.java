package mappa.core.items;

import mappa.core.content.ContentRegistry;
import mappa.core.content.Difficulty;
import mappa.core.random.SeededRng;
import mappa.testsupport.FixedRng;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RarityRollTest {
    private final ContentRegistry real = TestContent.real();

    @Test
    void weightedRoll_boundaries_reachFirstAndLastRarity() {
        RarityRoller roller = real.rarityRoller();
        Set<String> all = real.raritiesById().keySet();
        int total = real.raritiesById().values().stream().mapToInt(Rarity::weight).sum();
        assertEquals("COMMON", roller.rollRarity(new FixedRng().ints(0), all).id());
        assertEquals("LEGENDARY", roller.rollRarity(new FixedRng().ints(total - 1), all).id());
    }

    @Test
    @Tag("regression")
    @DisplayName("D-14: multiplier ranges don't overlap and rise with rarity")
    void multiplierRanges_doNotOverlap_andIncrease() {
        List<Rarity> list = real.rarityRoller().rarities();
        for (int i = 1; i < list.size(); i++) {
            assertTrue(list.get(i).minMultiplier() >= list.get(i - 1).maxMultiplier(),
                    list.get(i).id() + " overlaps " + list.get(i - 1).id());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"EASY", "MEDIUM", "INSANE", "NIGHTMARE"})
    void roll_onlyReturnsAllowedRarities(String difficultyId) {
        Difficulty d = real.difficulty(difficultyId);
        RarityRoller roller = real.rarityRoller();
        SeededRng rng = new SeededRng(42);
        for (int i = 0; i < 500; i++) {
            assertTrue(d.allowedRarities().contains(roller.rollRarity(rng, d.allowedRarities()).id()));
        }
    }

    @Test
    @Tag("regression")
    @DisplayName("D-14: Legendary can drop on Nightmare")
    void roll_nightmare_canProduceLegendary() {
        Difficulty d = real.difficulty("NIGHTMARE");
        RarityRoller roller = real.rarityRoller();
        SeededRng rng = new SeededRng(1);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            seen.add(roller.rollRarity(rng, d.allowedRarities()).id());
        }
        assertTrue(seen.contains("LEGENDARY"));
    }

    @Test
    void roll_fixedRarityItem_keepsFixedRarity() {
        ContentRegistry small = TestContent.small();
        ItemInstance i = small.rarityRoller().roll(new SeededRng(3), small.item("t_epic"), Set.of("COMMON"),
                small.raritiesById());
        assertEquals("RARE", i.rarity().id());
        assertTrue(i.multiplier() >= 1.15 && i.multiplier() < 1.5);
    }
}
