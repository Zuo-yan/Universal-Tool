package org.gwfx.universaltool.skill;

import net.minecraft.resources.ResourceLocation;
import org.gwfx.universaltool.UniversalToolMod;

/**
 * 技能静态元数据定义（星图节点、派系、前置关系与试炼条件）。
 */
public record SkillDefinition(
        String id,
        SkillConstellation constellation,
        SkillType type,
        SkillTier tier,
        int maxLevel,
        int[] levelCosts,
        int cooldownTicks,
        int chargeTicks,
        String prerequisiteId,
        SkillTrialSpec trial,
        float mapX,
        float mapY
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

    public String awakeningKey() {
        return translationKey() + ".awakening";
    }

    public String unlockKey() {
        return trial != null ? trial.descKey() : "";
    }

    public boolean hasPrerequisite() {
        return prerequisiteId != null && !prerequisiteId.isEmpty();
    }

    public boolean charged() {
        return chargeTicks > 0;
    }
}
