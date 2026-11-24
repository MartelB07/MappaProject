package mappa;

import mappa.adventure.AdventureGame;
import mappa.core.content.ContentException;
import mappa.core.content.ContentRegistry;
import mappa.core.content.JsonContentLoader;
import mappa.core.io.ConsoleIO;
import mappa.core.io.GameIO;
import mappa.core.io.InputClosedException;
import mappa.core.io.Menu;
import mappa.core.random.Rng;
import mappa.core.random.SeededRng;
import mappa.dungeons.DungeonsGame;
import mappa.explore.ExploreGame;

import java.util.Arrays;

/**
 * Entry point. Loads content once, then lets the player pick a game mode.
 *
 * <p>Flags: {@code --dev} (developer start: lots of gold / max level),
 * {@code --seed=N} (repeatable randomness).</p>
 * Roadmap: see docs/roadmap.md.
 */
public final class GameLauncher {
    private final ContentRegistry content;
    private final Rng rng;
    private final GameIO io;
    private final boolean devMode;

    public GameLauncher(ContentRegistry content, Rng rng, GameIO io, boolean devMode) {
        this.content = content;
        this.rng = rng;
        this.io = io;
        this.devMode = devMode;
    }

    public static void main(String[] args) {
        boolean dev = Arrays.asList(args).contains("--dev");
        Rng rng = Arrays.stream(args).filter(a -> a.startsWith("--seed="))
                .findFirst()
                .<Rng>map(a -> new SeededRng(Long.parseLong(a.substring("--seed=".length()))))
                .orElseGet(SeededRng::new);
        ContentRegistry content;
        try {
            content = JsonContentLoader.loadClasspath("data");
        } catch (ContentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }
        try {
            new GameLauncher(content, rng, new ConsoleIO(), dev).run();
        } catch (InputClosedException e) {
            System.out.println();
            System.out.println("Goodbye!");
        }
    }

    public void run() {
        Menu menu = new Menu(io);
        while (true) {
            int c = menu.choose("Which game would you like to run?", "exit", "Mappa Adventure", "Mappa Dungeons",
                    "Mappa Explore", "Info Page");
            switch (c) {
                case Menu.CANCEL -> {
                    io.println("Goodbye!");
                    return;
                }
                case 1 -> new AdventureGame(content, rng, io, devMode).run();
                case 2 -> new DungeonsGame(content, rng, io, devMode).run();
                case 3 -> new ExploreGame(content, rng, io).run();
                case 4 -> InfoPage.show(menu);
                default -> { }
            }
        }
    }
}
