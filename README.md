# Mappa

A text-based Java game with three modes:

| Mode | What it is |
|---|---|
| **Mappa Adventure** | Roguelike: travel regions, fight, shop, open chests, beat the Boss in the Hidden Village. |
| **Mappa Dungeons** | Turn-limited dungeon crawler with attributes, spells, rarity loot, and **Regular / Hardcore** run modes. |
| **Mappa Explore** | Survival prototype: gather, eat, drink, and explore biomes. |

## Requirements
- JDK **21 or newer** (the project compiles to Java 21). IntelliJ's Semeru 26 or OpenJDK 24 both work.
- No Gradle install needed — use the wrapper.

Point `JAVA_HOME` at a JDK 21+ (your PATH `java` may be an older Java 8):
```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\openjdk-24.0.2+12-54"
```

## Build, test, play
Quickest way to play (builds whatever changed, then starts the game):
```powershell
.\play                # or: .\play --dev   /   .\play --seed=42
```
Set `MAPPA_NO_COLOR=1` first if your terminal shows codes like `[32m` instead of colors.

Or step by step:
```powershell
.\gradlew.bat test                 # run all tests (report: build\reports\tests\test\index.html)
.\gradlew.bat jar                  # build\libs\MappaProject-0.2.0.jar (includes Gson)
& "$env:JAVA_HOME\bin\java" -jar build\libs\MappaProject-0.2.0.jar
```
Launch flags: `--dev` (developer start: lots of gold / max level), `--seed=N` (repeatable randomness).
Add `-Dmappa.noColor=true` before `-jar` to turn off terminal colors.

In IntelliJ: **File → Open → build.gradle → Open as Project**, then run `mappa.GameLauncher`.
IntelliJ shows the project as **MappaProject** and creates its own project files from `build.gradle`
(`.idea/` and `*.iml` are not tracked in git).

## Project layout
```
src/main/java/mappa/
  GameLauncher.java, InfoPage.java
  core/       shared basics: io (GameIO, Menu, Ansi), random (Rng), stats, progression,
              items, inventory, mobs, content (JSON loader + validator)
  adventure/  AdventureGame, AdventurePlayer, AdventureCombat, RegionGraph, Shop, ChestService
  dungeons/   DungeonsGame, DungeonPlayer, DungeonRun, RunMode, RewardRoller, screens
  explore/    ExploreGame, SurvivalState, WorldGenerator
src/main/resources/data/   all game content (JSON) — listed in data/index.json
src/test/java/mappa/       JUnit 5 tests; src/test/resources/testdata/ is a small fixed data set
docs/                      issues, scalability plan, testing plan, roadmap
```

## Adding content
Most content is data — no Java changes:

| To add… | Do this |
|---|---|
| A dungeon | Add `data/dungeons/dungeons/dungeon_04.json`, its mobs to `data/dungeons/mobs.json`, items tagged `dungeon:4`, and list the file in `data/index.json`. |
| Weapons / armor / spells | Add entries to `data/dungeons/items/*.json` or `data/adventure/items.json`. |
| An Adventure region | Add an entry to `data/adventure/regions.json` (links become two-way automatically). |
| A difficulty or rarity | Add an entry to `data/common/difficulties.json` / `rarities.json`. |
| A dungeon run mode | Add an entry to `runModes` in `data/dungeons/config.json`. |
| An open-world biome | Add an entry to `data/explore/biomes.json`. |
| A new stat (e.g. fire resist) | Add a constant to `core/stats/StatType.java`, then use it in JSON. |

Then run `.\gradlew.bat test` — `ContentValidationTest` loads and checks every file (unknown ids, bad ranges,
unreachable regions, unlisted files, ...) and the game refuses to start with a clear list of problems.

See `docs/` for the issue report, scalability plan and testing plan.
