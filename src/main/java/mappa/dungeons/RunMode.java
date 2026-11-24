package mappa.dungeons;

import mappa.core.io.Ansi;

/**
 * How a dungeon run treats death, from the "runModes" list in data/dungeons/config.json.
 *
 * <ul>
 *   <li><b>Regular</b> — unlimited lives; dying respawns you at the start of your current
 *       room but costs {@code deathTurnPenalty} turns; a win gives {@code rewardItems} items.</li>
 *   <li><b>Hardcore</b> — {@code lives = 1}; dying ends the run; a win gives more items.</li>
 * </ul>
 *
 * @param lives                deaths allowed before the run fails (0 = unlimited)
 * @param deathTurnPenalty     turns lost each time you respawn
 * @param respawnHealthPercent health restored on respawn (percent of max)
 * @param rewardItems          items rolled when the dungeon is cleared
 */
public record RunMode(String id, String name, Ansi.Color color, int lives, int deathTurnPenalty,
                      int respawnHealthPercent, int rewardItems, String description) {

    public boolean unlimitedLives() {
        return lives <= 0;
    }

    /** True if dying {@code deaths} times ends the run. */
    public boolean isOutOfLives(int deaths) {
        return !unlimitedLives() && deaths >= lives;
    }

    public String colored() {
        return Ansi.color(name, color);
    }
}
