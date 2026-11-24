package mappa.adventure;

import mappa.core.content.ContentRegistry;
import mappa.core.content.RegionDefinition;
import mappa.core.mobs.Mob;
import mappa.core.random.SeededRng;
import mappa.testsupport.FixedRng;
import mappa.testsupport.TestContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionGraphTest {

    @Test
    @Tag("regression")
    @DisplayName("A-08: every travel link works in both directions (real map)")
    void allNeighborLinks_areBidirectional() {
        ContentRegistry c = TestContent.real();
        for (RegionDefinition r : c.regions()) {
            for (String n : r.neighbors()) {
                assertTrue(c.region(n).neighbors().contains(r.id()), r.id() + " -> " + n + " has no way back");
            }
        }
    }

    @Test
    @Tag("regression")
    @DisplayName("A-08: every region can be reached from the start (real map)")
    void everyRegion_reachableFromStart() {
        ContentRegistry c = TestContent.real();
        RegionGraph g = new RegionGraph(c, AdventureConfig.from(c));
        Set<String> seen = new HashSet<>();
        Deque<RegionDefinition> todo = new ArrayDeque<>();
        todo.add(g.start());
        while (!todo.isEmpty()) {
            RegionDefinition r = todo.poll();
            if (seen.add(r.id())) {
                todo.addAll(g.neighbors(r));
            }
        }
        assertEquals(c.regions().size(), seen.size());
    }

    @Test
    @Tag("regression")
    @DisplayName("A-09: encounters only pick mobs the player can fight")
    void encounter_onlyEligibleMobs() {
        ContentRegistry c = TestContent.small();
        RegionGraph g = new RegionGraph(c, AdventureConfig.from(c));
        SeededRng rng = new SeededRng(5);
        for (int i = 0; i < 200; i++) {
            Mob m = g.encounter(g.region("t_home"), 0, rng);
            assertEquals("t_rat", m.definition().id());
        }
    }

    @Test
    @Tag("regression")
    @DisplayName("A-06: enhanced mobs are never below the base mob's level")
    void encounter_enhanced_levelAboveBase() {
        ContentRegistry c = TestContent.small();
        RegionGraph g = new RegionGraph(c, AdventureConfig.from(c));
        Mob m = g.encounter(g.region("t_town"), 3, FixedRng.alwaysLucky());
        assertTrue(m.name().startsWith("Enhanced"));
        assertTrue(m.level() >= 2 && m.level() <= 3, "level " + m.level());
    }

    @Test
    void neighbors_twoWayEvenIfListedOnce() {
        ContentRegistry c = TestContent.small();
        // t_town lists no neighbors itself; links come from t_home and t_castle.
        assertEquals(Set.of("t_home", "t_castle"), c.region("t_town").neighbors());
    }
}
