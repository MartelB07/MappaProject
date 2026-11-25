# Current Issues — MappaProject

_Review date: 2026-11-24. Covers every file under `src/` plus the project and tooling setup._

Issue IDs are used in [`scalability_plan.md`](scalability_plan.md) and [`testing_plan.md`](testing_plan.md), so the fixes and tests can point back to them.

**Prefixes:** `D` = Mappa Dungeons · `A` = Mappa Adventure · `G` = shared between modes · `S` = security/integrity · `ST` = structure/scalability · `T` = tooling/repo

| Severity | Count |
|---|---|
| Critical (crash, soft-lock, exploit) | 11 |
| Major (wrong game logic) | 22 |
| Structural / scalability | 14 |
| Tooling / repo | 4 |

## Status (updated 2026-11-24, after the restructure)

The code described below has been replaced (see [`scalability_plan.md`](scalability_plan.md)). The line numbers refer to
the **old** files, which no longer exist.

| Issues | Status | Guarded by |
|---|---|---|
| D-01 … D-18 | ✅ Fixed in the Dungeons rewrite | `DungeonRunTest`, `DungeonPlayerTest`, `ContentValidationTest` |
| A-01 … A-13 | ✅ Fixed in the Adventure rewrite | `AdventureCombatTest`, `ChestServiceTest`, `RegionGraphTest`, `EquipmentTest` |
| G-01, S-01 | ✅ Fixed (`Wallet`; `--dev` launch flag) | `WalletTest`, `AdventurePlayerTest` |
| ST-01 … ST-13 | ✅ Fixed (core package, JSON content, injected IO/RNG) | whole suite |
| ST-14 | ✅ Fixed: per-mode save slots (phase 6) | `SaveStoreTest`, `ItemCodecTest`, `SaveMenuTest`, `*SaveTest` |
| T-01 | ◐ `git init` done; nothing committed yet | — |
| T-02 … T-04 | ✅ Gradle wrapper, README, lowercase packages | — |

**Data decisions made while fixing** (all easy to change in JSON):
- D-06: healing values are now Healing 1-1 L0 heals 5 · 1-2 L10 heals 15 · 2-1 L15 heals 30 · 3-1 L33 heals 80.
- D-12: each difficulty has an explicit `rewardLevelCap`. The old "Easy drops items up to the Medium level" behavior was kept on purpose, but it's now visible in the data.
- D-15: weapons get a real damage curve. The old `setRarity(5)` specials are fixed **Epic**. Guard gear's unused "damage" now gives **Defense**. The second "MS 2-2" became "MS 2-3". Dungeon 3 boss gold is 300.
- A-11: chest level comes from the region (`chestLevel`), and a key opens chests **up to** its level.

---

## Critical — crash, soft-lock, or exploit

### D-01 · Nightmare on the last dungeon crashes
- **Where:** `src/MappaDungeons/RunDungeon.java:35`
- **What:** Nightmare sets the reward level from `dungeon.getNextDungeon().getEasyLevel()`. Dungeon 3 is built without a next dungeon (`RunMappaDungeons.java:787`), so this throws a `NullPointerException`.
- **Repro:** Reach level 40 (or edit the level) → Start a Dungeon → Dungeon 3 → Nightmare.
- **Fix:** Store a reward level cap for each difficulty in the dungeon's own data. Don't read it from the next dungeon.

### D-02 · Attribute reset gives infinite stats
- **Where:** `src/MappaDungeons/Player.java:196-201`
- **What:** A reset (`type == 4`) gives back all spent points, but `wardamage`, `magedamage` and `maxHealth` keep the bonuses those points gave. Spend → reset → spend again stacks stats forever. A reset also replaces `availablePoints` instead of adding to it, so any points you hadn't spent are lost.
- **Fix:** Work out stats as base + modifiers (see the scalability plan). A reset removes the modifiers and adds the refunded points to the current total.

### D-03 · Attribute reset is free
- **Where:** `src/MappaDungeons/RunMappaDungeons.java:330-338`, `src/MappaDungeons/RunDungeon.java:541-549`
- **What:** The menu says the reset costs `points * 25` gold and checks you have enough, but the gold is never taken.
- **Fix:** Take the gold before calling the reset (inside one method, so both callers can't drift apart).

### D-04 · Negative attribute amounts are accepted
- **Where:** `RunMappaDungeons.java:303-327`, `RunDungeon.java:514-538` (copied 6 times in total)
- **What:** The only check is `option <= availablePoints`. Input `-5` passes `isNumeric`, so `updateAttributes(1, -5)` lowers war damage and **adds** 5 points. Typing a letter returns `-1`, which also gets through.
- **Repro:** Open Attributes with ≥1 point → War Points → enter `-5`.
- **Fix:** Require `1 <= amount <= availablePoints` inside `Player`, not in the UI.

### D-05 · Healing spells never heal
- **Where:** `src/MappaDungeons/RunDungeon.java:296`
- **What:** It checks `spell.getName().equals("Healing")`, but the names are things like "Healing 1-1". It should check `getType()`. Healing spells go down the damage branch and do 0 damage.
- **Fix:** Check the item's type/slot, ideally an enum instead of a string.

### D-06 · Healing item data is in the wrong argument order
- **Where:** `src/MappaDungeons/RunMappaDungeons.java:571-574`
- **What:** `Spell(name, type, Class, level, amount, price, number)` gets level/amount/price in the wrong places. "Healing 1-1" ends up level 5 and heals **0**. "Healing 3-1" is level 30 (same as 2-1) and costs 400.
- **Fix:** Move the data to JSON with named fields (no positional arguments). See ST-04.

### A-01 · Health chest soft-locks when the inventory is full
- **Where:** `src/MappaAdventure/RunMappaAdventure.java:977-996`
- **What:** The "inventory full" check `i == length && j == length` can never be true inside the loop. If the inventory fills before `amount` reaches 0, `while (amount > 0)` loops forever.
- **Repro:** Fill all but one inventory slot, keep one matching key, open a Chest of Health that rolls more than 1 potion.
- **Fix:** Use an `Inventory.add()` that returns how many items fit, and stop when that number is 0.

### A-02 · Healing uses extra potions
- **Where:** `src/MappaAdventure/RunMappaAdventure.java:321-368`
- **What:** The `break` at line 353 only leaves the inner loop, so every later row with a matching potion uses one more. At full health `startingAmount` works out to 0 but a potion is still used. The "x1 … +0" message is wrong too.
- **Fix:** Work out how many potions are needed, cap that at what the player has, then remove exactly that many.

### A-03 · Equip loop equips duplicates again
- **Where:** `src/MappaAdventure/RunMappaAdventure.java:154-165`, `src/MappaDungeons/RunMappaDungeons.java:181-191`, `src/MappaDungeons/RunDungeon.java:391-401`
- **What:** `break` only leaves the inner loop. If another row has an item with the same name, it gets equipped too (the old item gets swapped back in). In Dungeons, `equipSpell` asks for a slot again for each duplicate. Adventure also compares names with `==` (line 156).
- **Fix:** Equip by inventory slot/instance id, once.

### A-04 · Two revive prompts when you die in a fight
- **Where:** `src/MappaAdventure/RunMappaAdventure.java:207` and `:58`
- **What:** `fight()` calls `gameOver(2)`, which offers a revive. If you say No, the main loop's `gameOver(1)` offers it again and prints "You Died" again. With too little gold, `gameOver(2)` returns quietly and `gameOver(1)` prints the death.
- **Fix:** Handle death in one place. Combat returns an outcome (WON / FLED / DIED) and the game loop decides what happens.

### S-01 · Hidden developer backdoor
- **Where:** `src/MappaAdventure/RunMappaAdventure.java:50`, `src/MappaDungeons/RunMappaDungeons.java:56`
- **What:** The name `"Password123  "` (two trailing spaces) gives a DEVELOPER player with 99999 gold (and level 10 in Adventure).
- **Fix:** Replace it with a debug flag at launch (`--dev` or a system property) that is off in release builds.

---

## Major — wrong game logic

### D-07 · Mage spells never scale
- **Where:** `src/MappaDungeons/Spell.java:49`
- **What:** `mageextra = startingDamage + mageextra;;` never updates `spellDamage`, so mage points don't affect spells. There's also a stray `;;`.

### D-08 · Swapping between gear classes moves damage to the wrong stat
- **Where:** `src/MappaDungeons/Player.java:369-436`
- **What:** `equip()` calls `unequip(newItem)`. `unequip` picks war or mage from the **new** item's class, then takes away the **old** item's damage. Swapping a War helmet for a Mage helmet lowers mage damage and leaves war damage too high.

### D-09 · Damage ignores the weapon's class
- **Where:** `src/MappaDungeons/Player.java:108-114`
- **What:** `getDamage()` returns max(war, mage), whatever the equipped weapon is. A staff gets the warrior's attribute points.

### D-10 · A spell equipped mid-dungeon can never be cast
- **Where:** `src/MappaDungeons/RunDungeon.java:38-39`, `:300-321`
- **What:** Cooldowns are only set for the spells equipped when the dungeon starts. A newly equipped spell has cooldown 0, so `(0 - 2) >= turns` is always false. It prints "wait -77 turn(s)".
- **Fix:** Keep cooldowns in the dungeon run (spell slot → turn it was last used). Don't store them on the item.

### D-11 · Running out of turns says "You Died"
- **Where:** `src/MappaDungeons/RunDungeon.java:869-876`
- **What:** The first branch tests `health <= 0 || turns == 0`, so the "You Ran Out of Turns" branch can never run. It uses `== 0`; `<= 0` is safer.

### D-12 · Difficulty → reward level cap is shifted by one step
- **Where:** `src/MappaDungeons/RunDungeon.java:28-36`
- **What:** Easy uses the Medium level, Medium uses Insane, Insane uses Nightmare. It also calls `setLevel` on the **shared** `Dungeon` object, so the value carries over between runs.

### D-13 · Every dungeon mob shows as level 0
- **Where:** `src/MappaDungeons/RunDungeon.java:675-699`, `Monsters.java:8`
- **What:** `Monsters` has no level parameter and `copy()` never sets one.

### D-14 · Reward rarity roll is skewed, and Legendary never drops
- **Where:** `src/MappaDungeons/RunDungeon.java:701-826`
- **What:**
  - The `% 25`, `% 10`, `% 3` checks on `0..100` give uneven odds (0 counts as Epic).
  - Legendary (rarity 4) is never rolled, even though `InfoPage` promises it on Nightmare.
  - Multiplier ranges overlap: Common ×1–2, Uncommon ×1–3, Rare ×1–4, Epic ×1–5, so an Epic can be weaker than a Common.
  - The 25-line roll block is copied three times.

### D-15 · Placeholder and wrong item data
- **Where:** `src/MappaDungeons/RunMappaDungeons.java:542-741`
- **What:**
  - `setRarity(5)` has no matching case, so the "special" weapons get rarity `""` and no color.
  - Every weapon does 2 damage and every price is 5.
  - "MS 2-2" is used twice (`:597`, `:598`).
  - Mage Armor 1-x is numbered 1-2…1-5 instead of 1-1…1-4.
  - Dungeon 3 boss gives 10 gold, Dungeon 2 boss gives 60.
  - In lists, the color variable `start` isn't reset for items with no rarity, so the previous item's color carries over.

### D-16 · Attack timing depends on the length of the color codes
- **Where:** `src/MappaDungeons/RunDungeon.java:132`, `:608`, `:613`, `:636`
- **What:** Boss/mob attacks and dodge timing are worked out from `getIndicator().length() - 7*3`. Each "-" in the indicator is 10 characters of ANSI codes. Changing a color, or turning colors off, breaks combat.
- **Fix:** Keep an integer counter. The display string is built from that counter.

### D-17 · Copy-pasted target selection; fixed group layout
- **Where:** `src/MappaDungeons/RunDungeon.java:181-243`, `:658-673`
- **What:** `attack()` cases 1–4 are copies of each other (only case 1 has the boss special case). A group with 5+ mobs can't be targeted. `generateGroup()` hard-codes 5 groups with fixed mob indexes and assumes index 4 is the boss.

### D-18 · Missing progression and unfinished menus
- **Where:** `RunMappaDungeons.java:96-100`, `:455-494`
- **What:**
  - Nothing stops you entering Dungeon 2 before clearing Dungeon 1 (level is the only check; `getNextDungeon` isn't used for unlocking).
  - "Test Damage" prints "Still in development".
  - With 0 attribute points, Open Attributes waits for Enter without saying so.
  - `startDungeon` uses a hard-coded switch with cases 1–3.

### A-05 · Level-ups: one per fight and a broken max-level check
- **Where:** `src/MappaAdventure/Player.java:185-199`, `src/MappaDungeons/Player.java:347-359`
- **What:** `checkLevelUp` goes up at most one level per call, even if the EXP covers several. The max-level message checks `exp == exps[last]`, but EXP resets each level, so it almost never prints. Dungeons' `updateExp` caps EXP at 6000, which doesn't matter for the same reason.

### A-06 · "Enhanced" monsters can be weaker than normal ones
- **Where:** `src/MappaAdventure/RunMappaAdventure.java:195-202`
- **What:** `copyLevel = random * playerLevel` can be below the base monster's level (even 0), yet it's labeled "Enhanced".

### A-07 · No win condition
- **Where:** `src/MappaAdventure/RunMappaAdventure.java:370-392`
- **What:** Beating the Boss in Hidden Village counts as a normal fight. The game never ends and no victory is shown.

### A-08 · Travel map is hard-coded and one-way
- **Where:** `src/MappaAdventure/RunMappaAdventure.java:757-911`
- **What:**
  - One `if` block per location, comparing names with `==`.
  - Links only go one way: Yorksville → Old Town (no way back); Old Town → Town of Pinocchio (no way back).
  - If a location name isn't matched, the outer `while (true)` loops forever.
  - Adding a location means adding another block of code.

### A-09 · Fights can spin forever
- **Where:** `src/MappaAdventure/RunMappaAdventure.java:178-185`
- **What:** It keeps picking random monsters until one is ≤ the player's level. If a location has none, this never ends. The current data avoids it only because every location happens to have a low enough monster.

### A-10 · Healing in battle costs nothing
- **Where:** `src/MappaAdventure/RunMappaAdventure.java:242-275`
- **What:** The monster doesn't attack after you heal, so you can heal as often as you like at no risk.

### A-11 · Chests almost never open
- **Where:** `src/MappaAdventure/RunMappaAdventure.java:922-962`, `:377-389`
- **What:** The chest level is a random 1–10, unrelated to the region, and the key's level has to match it **exactly**. Keys drop at ≤ the monster's level. `lookForChest` also changes the level on the shared `Chest` object.

### A-12 · Player equipment edge cases
- **Where:** `src/MappaAdventure/Player.java:60`, `:235-257`
- **What:** The dev constructor sets `damage = 5` and ignores the weapon. `updateDamage()` is empty. The default items `unequip` creates use the type `"Boot"`, but real boots use `"Boots"`. Trying to equip a potion or key says "Sorry you aren't level X".

### A-13 · Unequipping armor can kill you
- **Where:** `src/MappaAdventure/Player.java:235-247`, `src/MappaDungeons/Player.java:410-427`
- **What:** Current health is lowered by the armor's bonus with no floor, so a hurt player can drop to ≤0 HP from the inventory screen.

### G-01 · `updateGold(amount, direction)` API
- **Where:** both `Player.java` files
- **What:** A boolean decides add or subtract. In Dungeons, `direction == true` with `amount <= 0` goes to the subtract branch. There's no check against going below zero.
- **Fix:** Separate `addGold(long)` / `trySpendGold(long) → boolean` methods.

---

## Structural / scalability

| ID | Issue | Where |
|---|---|---|
| ST-01 | All content is hard-coded in `generateX()` methods with fixed-size arrays (`new Monsters[7]` with only 6 filled; `new Helmet[][]{[10],[13],[10]}`). Adding a dungeon means editing 6+ arrays plus switch statements. | `RunMappaAdventure.java:1028-1122`, `RunMappaDungeons.java:542-791` |
| ST-02 | Each mode has its own copy of `Item`, `Player`, `Monsters`, `Helmet`, `Weapon`. `Helmet`, `Chestplate` and `Boot` are the same class three times. | `src/MappaAdventure/*`, `src/MappaDungeons/*` |
| ST-03 | `type`, `Class`, `rarity` and `difficulty` are plain strings. `getclass()` is confusingly close to Java's `getClass()`. `new Item()` leaves `Class` as null → NPE risk on every `getclass().equals(...)`. | `MappaDungeons/Item.java` and callers |
| ST-04 | Every item constructor takes price/level/damage/health in a different order. This is what caused D-06. | `Weapon`, `Armor`, `Helmet`, `Spell`, `Healing`, `Key` |
| ST-05 | Template items are shared, changeable objects placed straight into inventories (Adventure). Any later per-item upgrade would change every copy. | `RunMappaAdventure.buy`, `openChest` |
| ST-06 | The inventory is a fixed 2D array of placeholder `Item()` objects. An empty slot is detected with `getName().equals("")` or `== ""`. Search, count and stack code is rewritten about 15 times. | everywhere |
| ST-07 | Rarity colors, stat display, menu prompts and confirm dialogs are copied 6+ times. `inventory()` and `attributes()` are copied between `RunMappaDungeons` and `RunDungeon`. | `RunMappaDungeons.java`, `RunDungeon.java` |
| ST-08 | Game logic and console I/O are mixed together, and the game runs from constructors (`new RunMappaAdventure()` plays the whole game). Nothing can be unit-tested. | all `Run*` classes |
| ST-09 | `Math.random()` everywhere with no seed, so results can't be reproduced in tests. `(int)(Math.random()*101) % n` gives uneven odds. | all modes |
| ST-10 | 8+ separate `new Scanner(System.in)` objects. Each buffers input, so typed-ahead or piped input gets lost. `GameRunner` has no exit option. | all `Run*` classes, `Player` (Dungeons) |
| ST-11 | `protected static` arrays (`healings`) are shared between runs and changeable. Adventure's `Player` constructor only works if `RunMappaAdventure.healings` was filled first. | `RunMappaAdventure.java:13`, `RunMappaDungeons.java:17` |
| ST-12 | Gold is a `double` (shows as `2.0`, `7.5`, and can drift with floating-point error). | both `Player` classes |
| ST-13 | `checkInput` clears the screen with ANSI codes on every input (garbled on older Windows consoles). `nonNumerical` is never used. Typos: "Sycthe", "recieve", "brought" (should be bought), "Avaliable". | `UserInputChecker.java`, various |
| ST-14 | No save/load (it's on the TODO list in `GameRunner.java`). | — |

---

## Tooling / repo

| ID | Issue |
|---|---|
| T-01 | Not a git repository. Compiled `out/` (classes + jar) and `.idea/workspace.xml` sit in the project folder. |
| T-02 | No build tool. IntelliJ uses the `semeru-26` SDK, but `java`/`javac` on PATH is 1.8, so IDE and command-line builds don't match. |
| T-03 | No tests and no README. The roadmap is a comment block in `GameRunner.java`. |
| T-04 | `GameRunner`/`InfoPage` are in the default package. Package names are PascalCase (`MappaAdventure`) instead of the lowercase Java convention. |
