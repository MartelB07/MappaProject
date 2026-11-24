# Testing Plan — MappaTestingStuff

Goes with [`scalability_plan.md`](scalability_plan.md). Issue IDs refer to [`current_issues.md`](current_issues.md).

## Why the current code can't be tested
- The game runs inside constructors and reads `System.in` through 8+ separate `Scanner`s (ST-08, ST-10).
- `Math.random()` is called directly, so no run can be repeated (ST-09).
- There is no build tool to run tests (T-02).

The restructure fixes these by passing in `GameIO` and `Rng`, so tests can feed scripted input and fixed random values.

## Status
Implemented: **150 tests, all passing** (`gradlew.bat test`). A few planned files were merged into neighbors:
`AttackTelegraphTest`/`RewardRollerTest`/`DungeonsGameTest` live in `DungeonRunTest`, and the Health tests live in
`StatsTest`. Added beyond the plan: `JsonContentLoaderTest` (broken-data error messages), `DungeonsConfigTest`,
`SurvivalStateTest`, and an index-completeness check in `ContentValidationTest`.

## Tooling
| Piece | Choice |
|---|---|
| Framework | JUnit 5 (`org.junit.jupiter:junit-jupiter`) |
| Runner | `./gradlew test` (Windows: `gradlew.bat test`), or IntelliJ's Gradle test runner |
| Report | `build/reports/tests/test/index.html` |
| Optional | JaCoCo coverage once phase 3 is done (target: core ≥ 80%) |

`build.gradle` test section:
```groovy
dependencies {
    implementation 'com.google.code.gson:gson:2.11.0'
    testImplementation platform('org.junit:junit-bom:5.11.3')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
test {
    useJUnitPlatform()
    testLogging { events 'failed'; exceptionFormat 'full' }
}
```

## Test helpers (`src/test/java/mappa/testsupport/`)
| File | Purpose |
|---|---|
| `ScriptedIO.java` | Implements `GameIO`. Built with `ScriptedIO.of("1", "Hero", "0", "1")`. Records all output and has `output()`, `outputContains(...)`. Throws `NoMoreInputException` when input runs out, so a menu that loops forever fails fast instead of hanging. |
| `FixedRng.java` | Implements `Rng`. Returns a queued list of values (`FixedRng.of(0, 3, 99)`). Use `SeededRng(42)` when you want "random but repeatable". |
| `TestContent.java` | Loads a small, stable data set from `src/test/resources/testdata/` (2 mobs, 1 dungeon, 1 region, a few items), so unit tests don't break when the real game is rebalanced. |
| `Fixtures.java` | Ready-made objects: `dungeonPlayer(level)`, `adventurePlayer(level)`, `itemInstance(id, rarity)`. |

## Conventions
- Names: `methodUnderTest_condition_expectedResult` (e.g. `addExp_enoughForTwoLevels_gainsTwoLevels`).
- A test written for a known bug has `@Tag("regression")` and puts the issue ID in `@DisplayName("D-01: ...")`.
- Anything with a loop or reading input gets `@Timeout(2)`.
- Arrange / Act / Assert, one behavior per test. Use `@ParameterizedTest` for tables (difficulties, rarities, level curves).
- Unit tests use `TestContent`. Only `ContentValidationTest` and `GameSmokeTest` use the real `data/` files.

---

## Planned test files (`src/test/java/mappa/...`)

### Core — phase 1

**`core/stats/StatsTest`**
- `get_noModifiers_returnsBase`
- `addAll_flatAndPercent_appliedInOrder` (flat first, then percent)
- `removeSource_afterAdd_returnsExactlyToBase` — 1000 equip/unequip cycles with no drift (D-08, A-12)
- `removeSource_unknownId_noChange`

**`core/progression/LevelCurveTest`**
- `addExp_enoughForTwoLevels_gainsTwoLevels` (A-05)
- `addExp_leftoverCarriesOver`
- `addExp_atMaxLevel_flagsReachedMaxOnce` (A-05)
- `formulaCurve_thresholdsStrictlyIncreasing` (parameterized over every curve in `level_curves.json`)

**`core/inventory/InventoryTest`**
- `add_intoEmpty_occupiesOneSlot`
- `add_whenFull_returnsZeroAdded` (A-01)
- `add_partialFit_returnsCountAdded`
- `remove_exactCount_removesOnlyThatMany` (A-02)
- `count_byDefinitionId_ignoresOtherItems`

**`core/inventory/EquipmentTest`**
- `equip_replacesOldItem_returnsOldToInventory`
- `equip_belowLevelReq_rejected`
- `equip_nonEquippableSlot_rejectedWithClearReason` (A-12)
- `unequipArmor_lowHp_clampsCurrentHpToAtLeast1` (A-13)

**`core/inventory/WalletTest`**
- `trySpend_insufficient_returnsFalseAndUnchanged` (G-01)
- `add_negative_throws`

**`core/items/RarityRollTest`**
- `weights_sumPositive_andEveryRarityReachable` (FixedRng walks the boundaries)
- `multiplierRanges_doNotOverlap_andIncreaseWithRarity` (D-14)
- `roll_onlyReturnsAllowedRaritiesForDifficulty` (parameterized per difficulty)
- `roll_nightmare_canProduceLegendary` (D-14)

**`core/items/ItemInstanceTest`**
- `create_twoInstancesFromSameDefinition_areIndependent` (ST-05)
- `definition_isImmutable`

**`core/mobs/MobFactoryTest`**
- `spawn_setsRequestedLevel` (D-13)
- `spawn_higherLevel_neverWeaker` (parameterized, levels 0..maxLevel)
- `spawn_higherDifficulty_neverWeaker`
- `spawn_enhanced_levelAtLeastBase` (A-06)

**`core/io/MenuTest`**
- `choose_outOfRange_repromptsThenAccepts`
- `amount_negative_rejected` (D-04)
- `amount_nonNumeric_rejected`
- `confirm_yesNo_parsesBoth`

### Content — phase 2

**`core/content/ContentValidationTest`** — runs against the **real** `src/main/resources/data/`. This is the test that grows as content is added; new JSON files are checked automatically.
- `allFiles_parseWithoutErrors`
- `ids_uniqueAcrossAllFiles` (D-15 duplicate "MS 2-2")
- `references_allResolve` (mobs in waves, rewardPool items, region neighbors, unlockRequires)
- `numbers_nonNegative_andRangesOrdered`
- `everyDifficulty_hasAtLeastOneDroppableReward` (D-01, D-12)
- `everyRegion_hasEligibleMobAtLevelReq` (A-09)
- `dungeonUnlockChain_hasNoCycles`
- `consumables_healAmountPositive` (D-06)

**`core/content/JsonContentLoaderTest`** (uses `testdata/`)
- `malformedJson_throwsContentExceptionWithFileName`
- `unknownStatType_throwsWithFieldPath`
- `missingRequiredField_throws`

### Dungeons — phase 3

**`dungeons/DungeonPlayerTest`**
- `spendPoints_negative_rejected` (D-04)
- `spendPoints_moreThanAvailable_rejected`
- `resetPoints_removesStatBonuses` (D-02)
- `resetPoints_keepsUnspentPoints` (D-02)
- `resetPoints_chargesGold` (D-03)
- `damage_usesEquippedWeaponClass` (D-09)
- `mageSpell_scalesWithMagePoints` (D-07)

**`dungeons/DungeonRunTest`**
- `start_lastDungeonNightmare_doesNotThrow` (D-01)
- `rewardCap_matchesSelectedDifficulty` (D-12)
- `turnsReachZero_resultOutOfTurns_notDied` (D-11)
- `healingSpell_restoresHealth` (D-05)
- `spellEquippedMidRun_castableImmediately` (D-10)
- `spell_onCooldown_reportsPositiveTurnsRemaining` (D-10)
- `targetList_supportsGroupsLargerThanFour` (D-17)
- `bossKilledBySpell_rewardsGrantedOnce`

**Run modes (in `dungeons/DungeonRunTest`, nested `Regular` / `Hardcore` classes)**
- Regular: `death_respawnsAtStartOfCurrentRoom_andLosesTurns` (respawn in the boss room, room reset, full HP, turns − penalty)
- Regular: `penaltyUsesUpRemainingTurns_endsRunOutOfTurns`
- Regular: `clear_givesTwoRewardItems`, `unlimitedLives`
- Hardcore: `death_endsTheRun_noRewards`, `clear_givesThreeRewardItems`
- `rewards_inventoryFull_reportedAsLost`
- `dungeons/DungeonsConfigTest`: the real config has Regular (unlimited lives, penalty > 0, 2 items) and Hardcore (1 life, 3 items); bad values are rejected

**`dungeons/AttackTelegraphTest`** — *(implemented inside `DungeonRunTest` as `boss_dodgeOnWarningTurn_negatesDamage`)*
- `mobAttacks_everyNTurns_fromCounterNotStringLength` (D-16)
- `dodge_onWarningTurn_negatesDamage`
- `colorsDisabled_timingUnchanged` (D-16)

**`dungeons/RewardRollerTest`**
- `rolls_respectRewardLevelCap`
- `rolls_countMatchesDifficultyRewardRolls`
- `inventoryFull_reportsSkippedRewards`

**`dungeons/DungeonsGameTest`**
- `lockedDungeon_notStartableUntilPrerequisiteCleared` (D-18)
- `dungeonList_showsEveryLoadedDungeon` (no hard-coded 3)

### Adventure — phase 4

**`adventure/AdventureCombatTest`**
- `heal_requestThree_consumesExactlyThree` (A-02)
- `heal_atFullHealth_consumesNone` (A-02)
- `heal_takesTurn_monsterRetaliates` (A-10)
- `playerDies_reviveOfferedOnce` (A-04)
- `killFinalBoss_returnsVictory` (A-07)

**`adventure/RegionGraphTest`**
- `allNeighborLinks_areBidirectional` (A-08)
- `everyRegion_reachableFromHome` (A-08)
- `travel_belowLevelReq_rejected`

**`adventure/ChestServiceTest`**
- `healthChest_inventoryFull_terminates` (`@Timeout`) (A-01)
- `keyOpensChestsUpToItsLevel` (A-11)
- `chestLevel_withinRegionRange` (A-11)

**`adventure/ShopTest`**
- `buy_insufficientGold_noItemNoCharge`
- `buy_partialAffordable_buysOnlyAffordable`
- `sell_returns75PercentAsWholeGold` (ST-12)

**`adventure/AdventurePlayerTest`**
- `devMode_onlyViaFlag_notPlayerName` (S-01)

### End-to-end

**`GameSmokeTest`** — real data, `SeededRng(1234)`, `ScriptedIO`, `@Timeout(5)`:
- `launcher_exitOption_terminates`
- `adventure_createFightExit_noExceptions`
- `dungeons_createEquipRunDungeon1Easy_exit_noExceptions`
- `explore_stub_returnsToMenu`
- `infoPage_returnsToMenu`

---

## Issue → test coverage
Every Critical and Major issue in `current_issues.md` has at least one test above. Recheck this when adding issues:

| Issues | Covered by |
|---|---|
| D-01, D-12 | `DungeonRunTest`, `ContentValidationTest` |
| D-02, D-03, D-04, D-07, D-09 | `DungeonPlayerTest`, `MenuTest` |
| D-05, D-10, D-11, D-17 | `DungeonRunTest` |
| D-06, D-15 | `ContentValidationTest` |
| D-08, A-12, A-13 | `StatsTest`, `EquipmentTest` |
| D-13, A-06 | `MobFactoryTest` |
| D-14 | `RarityRollTest` |
| D-16 | `AttackTelegraphTest` |
| D-18 | `DungeonsGameTest` |
| A-01, A-11 | `ChestServiceTest`, `InventoryTest` |
| A-02, A-04, A-07, A-10 | `AdventureCombatTest` |
| A-05 | `LevelCurveTest` |
| A-08, A-09 | `RegionGraphTest`, `ContentValidationTest` |
| A-03 | `EquipmentTest` (equip by instance id) |
| G-01 | `WalletTest` |
| S-01 | `AdventurePlayerTest` |

## Running
```
gradlew.bat test                                  # everything
gradlew.bat test --tests "mappa.core.*"           # one package
gradlew.bat test -PincludeTags=regression         # only bug regressions (add the tag filter in build.gradle)
```
Add `gradlew.bat test` as a required check once the repo is on GitHub (a GitHub Actions workflow can come in phase 0).
