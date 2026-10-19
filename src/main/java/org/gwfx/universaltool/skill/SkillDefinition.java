package org.gwfx.universaltool.skill;

import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * Static skill metadata.
 *
 * @param id            stable identifier, also the translation key suffix
 * @param type          active or passive
 * @param maxLevel      number of purchasable levels
 * @param levelCosts    point cost of each level, indexed from level 1
 * @param cooldownTicks cooldown in ticks after a successful cast; 0 for passives
 * @param chargeTicks   hold time before release; 0 means the skill fires instantly
 * @param unlockKey     translation key describing the unlock condition
 */
public record SkillDefinition(
        String id,
        SkillType type,
        int maxLevel,
        int[] levelCosts,
        int cooldownTicks,
        int chargeTicks,
        String unlockKey
) {
    public ResourceLocation location() {
        return ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, id);
    }

    public int costAt(int nextLevel) {
        if (nextLevel < 1 || nextLevel > levelCosts.length) {
            return Integer.MAX_VALUE;
        }
        return levelCosts[nextLevel - 1];
    }

    public String translationKey() {
        return "skill.universal_tool." + id;
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }

    public String unlockKey() {
        return unlockKey == null ? "" : unlockKey;
    }

    public boolean charged() {
        return chargeTicks > 0;
    }
}
