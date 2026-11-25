# Roadmap

Moved from the TODO comment in the old `GameRunner.java`. ✅ = done.

## Mappa Adventure
- ✅ Locations and travelling (now data-driven, two-way links)
- ✅ Chests (gold, items, or a monster); keys open chests up to their level
- ✅ Monster stats shown in fights; dodge and critical hits
- ✅ Monster health/damage scale with level; damage has a random range
- ✅ EXP resets on level up (and several levels can be gained at once)
- ✅ Revive for gold (offered once per death)
- ✅ Shop prices shown; gold shown while shopping
- ✅ Win condition for beating the Boss in the Hidden Village
- ✅ Save and load: 3 slots, autosave, Continue / New game / Delete when the mode opens

## Mappa Dungeons
- ✅ Dungeons with rewards, spells (2-turn cooldown), melee, inventory, armor and weapons
- ✅ Difficulties (Easy → Nightmare) with rarity rules; Legendary drops only on Nightmare
- ✅ Sell shop
- ✅ Limited turns per dungeon
- ✅ Dungeon unlock chain (clear Dungeon 1 to open Dungeon 2, ...)
- ✅ **Run modes:** Regular (respawn at the current room, lose turns, 2 reward items) and Hardcore (one life, 3 reward items)
- ☐ Boost spells (4-turn cooldown)
- ☐ Bonus reward every five dungeons completed (doesn't stack; only from dungeons beaten at least once)
- ☐ Quest system
- ✅ Save and load (lobby only, so Hardcore deaths can't be undone by reloading)

## Mappa Explore
- ✅ Prototype: biomes from data, gather, eat, drink, hazards, night-only mobs, day counter
- ☐ Crafting / experiments with gathered resources
- ☐ Shelter and building
- ☐ Quest system
- ☐ Larger 2D world map

## Interface
- ✅ Styled text UI: boxed banners for big moments, colored one-line events, status headers with bars
- ✅ Screen clears after every input (only the latest results and options are shown)
- ✅ Two-column menus for long lists; ASCII fallback for terminals without UTF-8
- ✅ Flavor descriptions for regions, dungeons and biomes
- ☐ Typed commands (`/fight`, `/use potion`, `/travel old town`, `/help`) alongside numbers. Design in `docs/scalability_plan.md`, "Future: command input"
