# Scalability Plan — MappaProject

## Goal
Restructure the project so new **dungeons, worlds/regions, mobs, weapons, armor, spells and open-world traits** can be added mostly by **adding data files**, with few or no Java changes. Player and mob stats should scale from rules, not hand-typed numbers. Every change should be testable.

## Decisions
| Topic | Decision |
|---|---|
| Build | **Gradle** (wrapper checked in) + **JUnit 5**. Java 21 toolchain; IntelliJ's Semeru 26 can compile it. |
| Content | **JSON files** under `src/main/resources/data/`, read with **Gson**. |
| Sharing | **Shared basics only.** The core provides I/O, RNG, stats, items, inventory, progression, mob scaling and content loading. Each game mode keeps its own Player and combat/run loop. |

Issue IDs (e.g. D-02) refer to [`current_issues.md`](current_issues.md).

---

## Target layout
```
settings.gradle
build.gradle                  application plugin, junit-jupiter, gson
gradlew / gradlew.bat / gradle/wrapper/
README.md
docs/
src/main/java/mappa/
  GameLauncher.java            (was GameRunner) mode menu + Exit; builds ConsoleIO, SeededRng, ContentRegistry once
  InfoPage.java
  core/
    io/         GameIO (interface: readLine, print, println)
                ConsoleIO (the ONE Scanner)
                Menu (numbered choice / confirm / amount prompts with range checks)
                Ansi (colors; can be switched off)
    random/     Rng (interface: nextInt(bound), chance(p), between(a,b), weighted(list))
                SeededRng
    stats/      StatType (enum), Stats (EnumMap<StatType, Integer>), StatModifier (flat/percent, source id)
    progression/ LevelCurve (thresholds/formula, multi-level gains, cap), LevelUpResult
    items/      ItemDefinition (immutable template), ItemInstance (definition + rarity + rolled stats, unique id)
                ItemSlot (enum), ClassTag (enum: WAR, MAGE, GUARD, ANY), Rarity (loaded from rarities.json)
    inventory/  Inventory (capacity, List<ItemStack>, add → returns how many fit, remove, count, hasSpace)
                Equipment (slot → ItemInstance; totalModifiers())
                Wallet (long gold; add / trySpend)
    mobs/       MobDefinition, Mob (runtime state), MobFactory.spawn(def, level, difficulty, rng)
    content/    ContentRegistry (typed id → definition maps), JsonContentLoader, ContentValidator, ContentException
  adventure/    AdventureGame, AdventurePlayer, AdventureCombat (returns WON/FLED/DIED), RegionGraph, Shop, ChestService
  dungeons/     DungeonsGame (lobby), DungeonPlayer (attribute points as StatModifiers), DungeonRun (turns, waves, cooldown map)
                RewardRoller, AttackTelegraph (integer counter → indicator string)
  explore/      ExploreGame (stub → survival loop later), WorldGenerator, SurvivalState
src/main/resources/data/
  common/       rarities.json, difficulties.json, level_curves.json
  adventure/    items.json, mobs.json, regions.json, chests.json
  dungeons/     items/war.json, items/mage.json, items/guard.json, spells.json, consumables.json, mobs.json
                dungeons/dungeon_01.json, dungeon_02.json, dungeon_03.json   ← one file per dungeon
  explore/      biomes.json, resources.json   (schemas defined now, content later)
src/test/java/mappa/...        see testing_plan.md
src/test/resources/testdata/   small fixed content set used by unit tests
```

Package names are lowercase (`mappa.dungeons`, not `MappaDungeons`) — fixes T-04.

---

## Core building blocks

### Stats & modifiers — the main scaling mechanism
```java
enum StatType { MAX_HEALTH, WAR_DAMAGE, MAGE_DAMAGE, DEFENSE, CRIT_CHANCE, CRIT_MULT, DODGE_CHANCE,
                HUNGER, THIRST, STAMINA /* survival stats for Explore */ }

record StatModifier(StatType stat, int flat, double percent, String sourceId) {}

final class Stats {
    Stats base;                                   // from level/class/definition
    List<StatModifier> modifiers;                 // gear, attribute points, buffs
    int get(StatType t) { return (int) Math.round((base(t) + sumFlat(t)) * (1 + sumPercent(t))); }
    void addAll(String sourceId, List<StatModifier> m);
    void removeSource(String sourceId);           // unequip / respec / buff ends
}
```
- Equipping adds modifiers tagged with the item instance id. Unequipping removes them by that id. Nothing keeps running totals that can drift, which fixes **D-02, D-08, A-12** by design.
- Current HP is kept separately and clamped to `[1, MAX_HEALTH]` after a max-HP change (fixes **A-13**).
- A new stat (e.g. `FIRE_RESIST` for a volcano world) is one enum constant plus JSON entries.

### Progression — `level_curves.json`
```json
{
  "adventure": { "maxLevel": 10, "thresholds": [0,4,10,15,25,40,60,80,100,200,400],
                 "perLevel": { "MAX_HEALTH": "table:10,20,40,60,80,100,130,150,180,200,250", "WAR_DAMAGE": "level" },
                 "goldPerLevel": 10 },
  "dungeons":  { "maxLevel": 60, "formula": { "base": 3, "growth": 1.12 },
                 "perLevel": { "attributePoints": 1 } }
}
```
`LevelCurve.addExp(n)` loops until the EXP is used up and returns every level gained, with a `reachedMax` flag (fixes **A-05**). Raising the level cap for a new world is a data edit.

### Items — definitions vs. instances
```json
{ "id": "war_helmet_2_1", "name": "War Helmet 2-1", "slot": "HELMET", "class": "WAR",
  "levelReq": 15, "price": 120,
  "stats": { "MAX_HEALTH": 18, "WAR_DAMAGE": 10 },
  "tags": ["dungeon:2", "set:war"] }
```
- `ItemDefinition` is immutable. Drops, purchases and chest rewards create an `ItemInstance` with its own id and rolled stats, so templates are never shared or changed (fixes **ST-05, D-14**).
- All fields are named, so argument-order bugs go away (fixes **ST-04, D-06**).
- Consumables and spells use the same file format with `slot: CONSUMABLE / SPELL` and an `effect` block (`{"heal": 15}` / `{"damage": 25, "target": "ALL", "cooldown": 2}`).

### Rarity — `rarities.json`
```json
[ { "id": "COMMON",    "color": "WHITE",  "weight": 60, "multiplier": [1.00, 1.15] },
  { "id": "UNCOMMON",  "color": "GREEN",  "weight": 25, "multiplier": [1.15, 1.35] },
  { "id": "RARE",      "color": "BLUE",   "weight": 10, "multiplier": [1.35, 1.60] },
  { "id": "EPIC",      "color": "PURPLE", "weight": 4,  "multiplier": [1.60, 2.00] },
  { "id": "LEGENDARY", "color": "GOLD",   "weight": 1,  "multiplier": [2.00, 2.50] } ]
```
Weighted roll through `Rng.weighted`. The ranges don't overlap. Which rarities can drop is set per difficulty (below).

### Difficulty — `difficulties.json`
```json
[ { "id": "EASY",      "mobScale": 1.0, "allowedRarities": ["COMMON","UNCOMMON","RARE"] },
  { "id": "MEDIUM",    "mobScale": 2.0, "allowedRarities": ["COMMON","UNCOMMON","RARE","EPIC"] },
  { "id": "INSANE",    "mobScale": 2.5, "allowedRarities": ["UNCOMMON","RARE","EPIC"] },
  { "id": "NIGHTMARE", "mobScale": 4.0, "allowedRarities": ["RARE","EPIC","LEGENDARY"] } ]
```
Adding a "Mythic" difficulty is one JSON entry. Difficulty sets mob strength and which rarities can drop.
**How many** items drop is set by the run mode (below).

### Dungeon run modes — `runModes` in `data/dungeons/config.json`
The player picks a run mode after choosing a difficulty.
```json
"runModes": [
  { "id": "REGULAR",  "name": "Regular",  "lives": 0, "deathTurnPenalty": 10, "respawnHealthPercent": 100, "rewardItems": 2 },
  { "id": "HARDCORE", "name": "Hardcore", "lives": 1, "deathTurnPenalty": 0,  "respawnHealthPercent": 100, "rewardItems": 3 }
]
```
- **Regular**: `lives: 0` means unlimited. On death you respawn at the **start of the room you died in**, with `respawnHealthPercent` health, and that room's mobs reset. Earlier rooms stay cleared. You lose `deathTurnPenalty` turns; if that uses up your turns, the run ends as *out of turns*. A win gives **2** items.
- **Hardcore**: `lives: 1`, so the first death ends the run with no rewards. A win gives **3** items.
- New modes (e.g. "Ironman: 3 lives, 4 items") are a JSON entry. `DungeonsConfig` validates the values, and `DungeonRun` handles all the rules (`onDeath`, `complete`).

### Mobs — `mobs.json` + `MobFactory`
```json
{ "id": "d2_warrior", "name": "Bone Warrior", "archetype": "WARRIOR",
  "stats":   { "MAX_HEALTH": { "base": 40, "perLevel": 0.12 }, "WAR_DAMAGE": { "base": 20, "perLevel": 0.08 } },
  "rewards": { "exp": 8, "gold": [0, 5] },
  "behavior": { "attackEvery": 3, "telegraph": true } }
```
`MobFactory.spawn(def, level, difficulty, rng)` → `stat = base * (1 + perLevel * level) * difficulty.mobScale`, and the mob gets its real level (fixes **D-13**). "Enhanced" Adventure mobs = spawn at `baseLevel + rng.between(1, 3)` (fixes **A-06**). Attack timing comes from `behavior`, kept as an integer counter in `AttackTelegraph` (fixes **D-16**).

### Dungeons — one file each
```json
{ "id": "dungeon_02", "name": "Dungeon 2", "unlockRequires": "dungeon_01",
  "difficulties": {
    "EASY":      { "levelReq": 15, "mobLevel": 15, "rewardLevelCap": 15 },
    "MEDIUM":    { "levelReq": 17, "mobLevel": 17, "rewardLevelCap": 17 },
    "INSANE":    { "levelReq": 20, "mobLevel": 20, "rewardLevelCap": 20 },
    "NIGHTMARE": { "levelReq": 24, "mobLevel": 24, "rewardLevelCap": 27 } },
  "turnLimit": 75,
  "waves": [ ["d2_mage","d2_warrior","d2_mage"], ["d2_mage2","d2_warrior","d2_mage2"],
             ["d2_mage","d2_brute","d2_warrior","d2_brute"], ["d2_warrior","d2_mage2","d2_warrior","d2_mage2"] ],
  "boss": "d2_boss",
  "rewardPool": { "tags": ["dungeon:2"], "include": ["healing_2_1"] } }
```
- `DungeonsGame` lists every loaded dungeon. Locked ones show "requires Dungeon 1" (fixes **D-18**). There are no `case 1/2/3` blocks.
- Each difficulty has its own `rewardLevelCap`, so nothing depends on the next dungeon (fixes **D-01, D-12**).
- `DungeonRun` keeps `Map<SpellSlot, Integer> lastCastTurn`, so a spell equipped mid-run works right away (fixes **D-10**). Targeting builds a numbered list of whatever mobs are alive, for any group size (fixes **D-17**).
- Win, out-of-turns and death return `RunResult.CLEARED / OUT_OF_TURNS / DIED / ABANDONED` (fixes **D-11**).

### Adventure regions — `regions.json`
```json
{ "id": "old_town", "name": "Old Town", "levelReq": 9, "hasChests": true, "chestLevel": [7, 10],
  "mobs": [ { "id": "homeless_man", "weight": 3, "levelRange": [9, 10] } ],
  "neighbors": ["town_of_pinocchio", "junk_yard", "yorksville", "hidden_village"],
  "isFinal": false }
```
- `RegionGraph` makes every link two-way when it loads and checks that every region can be reached from Home (fixes **A-08**).
- The fight picker filters to eligible mobs first. An empty list is a content error found at load time, not an endless loop (fixes **A-09**).
- `chestLevel` ties chest and key levels to the region, and a key opens chests **up to** its level (fixes **A-11**).
- `"isFinal": true` on Hidden Village + `boss` → beating it triggers a victory screen (fixes **A-07**).

### Open world (Explore) — `biomes.json`
```json
{ "id": "forest", "name": "Whispering Forest", "dangerLevel": [1, 5],
  "resources": [ { "id": "wood", "weight": 5, "yield": [1, 3] }, { "id": "berries", "weight": 3, "yield": [1, 4] } ],
  "mobs": [ { "id": "wolf", "weight": 2, "levelRange": [1, 4], "nightOnly": true } ],
  "hazards": [ { "id": "thorns", "chance": 0.05, "effect": { "MAX_HEALTH": -2 } } ],
  "survival": { "hungerDrainPerHour": 2, "thirstDrainPerHour": 3 },
  "traits": ["shelter", "fresh_water"] }
```
Open-world characteristics are free-form `traits` plus `survival`/`hazards` blocks. They use the same `StatType`, `MobFactory`, `Inventory` and `Rng`, so a new biome (desert, tundra, cave) is a JSON entry. `WorldGenerator` picks and links biomes from a seed.

### Content validation (runs at startup and in tests)
`ContentValidator` stops with a clear `ContentException` listing every problem when:
- an id is duplicated across files;
- a reference (mob, item, region, dungeon, rarity, unlock) doesn't exist;
- a number is negative where it shouldn't be, or a `levelRange` is reversed;
- some difficulty has no reward that can drop at its `rewardLevelCap`;
- a region has no mob the player could fight at its `levelReq`;
- a region can't be reached from the start, or two dungeons each require the other to unlock.

---

## Input, randomness and state (makes testing possible)
- One `GameIO` is passed to every mode. `ConsoleIO` holds the only `Scanner` (fixes **ST-10**). Tests use `ScriptedIO`.
- `Menu` handles numbered choices, confirms and amounts with min/max checks in one place (fixes **D-04** and the copy-paste in **ST-07**).
- `Rng` is passed in everywhere. There's no `Math.random()` in game code (fixes **ST-09**).
- Game classes have a `run()` method. Constructors only set things up (fixes **ST-08**).
- No `static` mutable state (fixes **ST-11**). Gold is a `long` inside `Wallet` (fixes **ST-12, G-01**).
- The developer backdoor becomes a launch flag `--dev` (fixes **S-01**).

---

## Phased migration
Each phase ends with `./gradlew test` passing and the game still playable.

**Progress (2026-11-24):** phases 0–5 are done and all 150 tests pass. Phase 6 (save/load, quests) is still open.
Nothing has been committed to git yet.

| Phase | Work | Issues closed |
|---|---|---|
| **0. Repo & build** | `git init`. `.gitignore` covers `out/`, `build/`, `.gradle/`, `.idea/workspace.xml`. Add the Gradle wrapper (create it from IntelliJ's bundled Gradle: *File → New → Module from existing sources → Gradle*, or `gradle wrapper` if Gradle gets installed). Move code to `src/main/java/mappa/...`, use lowercase packages, add README. Move the TODO comment from `GameRunner` into `docs/roadmap.md`. | T-01…T-04 |
| **1. Core basics** | `GameIO`/`ConsoleIO`/`Menu`/`Ansi`, `Rng`/`SeededRng`, `Stats`/`StatModifier`, `LevelCurve`, `Rarity`, `Inventory`/`Equipment`/`Wallet`, with unit tests. | ST-06, ST-07, ST-09, ST-10, ST-12, G-01 |
| **2. Content pipeline** | `JsonContentLoader`, `ContentRegistry`, `ContentValidator`. Move the **Dungeons** data into JSON, fixing D-06 / D-15 values on the way. | ST-01, ST-03, ST-04, D-06, D-15 |
| **3. Dungeons on core** | `DungeonPlayer`, `DungeonsGame`, `DungeonRun`, `RewardRoller`, `AttackTelegraph`. Delete the old `MappaDungeons` package. | D-01…D-05, D-07…D-14, D-16…D-18 |
| **4. Adventure on core** | Move Adventure data into JSON. Add `AdventurePlayer`, `AdventureCombat`, `RegionGraph`, `Shop`, `ChestService`. Delete the old package. | A-01…A-13, ST-02, ST-05, ST-11 |
| **5. Explore skeleton** | `biomes.json`, `resources.json`, `WorldGenerator`, a basic survival day loop. | — |
| **6. Later** | Save/load (JSON: player stats, level, wallet, item instances, unlocked dungeons). Quest system (`quests.json`: objectives on mob/item/region ids). Bonus-reward-every-5-dungeons from the roadmap. | ST-14 |

### Adding content after the migration
| To add… | Do this |
|---|---|
| A dungeon | Drop `dungeon_04.json` into `data/dungeons/dungeons/`, add its mobs to `mobs.json` and items tagged `dungeon:4`. Run tests. |
| A weapon/armor set | Add entries to `data/dungeons/items/*.json` or `data/adventure/items.json`. |
| An Adventure region | Add an entry to `regions.json` with its neighbors. |
| A difficulty or rarity | Add an entry to `difficulties.json` / `rarities.json`. |
| A new stat (e.g. fire resist) | Add a `StatType` constant (Java change), then use it in JSON. |
| An open-world biome | Add an entry to `explore/biomes.json`. |

`ContentValidationTest` runs on every build and catches broken references in new content.
