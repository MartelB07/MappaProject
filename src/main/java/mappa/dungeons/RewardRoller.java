package mappa.dungeons;

import mappa.core.content.ContentRegistry;
import mappa.core.content.ContentValidator;
import mappa.core.content.Difficulty;
import mappa.core.content.DungeonDefinition;
import mappa.core.items.ItemDefinition;
import mappa.core.items.ItemInstance;
import mappa.core.items.RarityRoller;
import mappa.core.random.Rng;

import java.util.ArrayList;
import java.util.List;

/** Rolls dungeon completion rewards. */
public final class RewardRoller {
    private final ContentRegistry content;

    public RewardRoller(ContentRegistry content) {
        this.content = content;
    }

    /** Items whose level is at most the difficulty's reward cap. */
    public List<ItemDefinition> eligible(DungeonDefinition dungeon, String difficultyId) {
        int cap = dungeon.difficulties().get(difficultyId).rewardLevelCap();
        List<ItemDefinition> out = new ArrayList<>();
        for (ItemDefinition d : ContentValidator.rewardPool(content, dungeon)) {
            if (d.levelReq() <= cap) {
                out.add(d);
            }
        }
        return out;
    }

    /** Rolls {@code count} items with rarities allowed by the difficulty. */
    public List<ItemInstance> roll(DungeonDefinition dungeon, Difficulty difficulty, int count, Rng rng) {
        List<ItemDefinition> pool = eligible(dungeon, difficulty.id());
        RarityRoller roller = content.rarityRoller();
        List<ItemInstance> rewards = new ArrayList<>();
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            ItemDefinition def = rng.pick(pool);
            rewards.add(roller.roll(rng, def, difficulty.allowedRarities(), content.raritiesById()));
        }
        return rewards;
    }
}
