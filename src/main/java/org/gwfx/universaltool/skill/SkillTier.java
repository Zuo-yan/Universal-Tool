package org.gwfx.universaltool.skill;

/**
 * 技能星阶层级：决定节点在星图上的视觉大小与重要程度。
 */
public enum SkillTier {
    INITIATE("initiate"),
    INTERMEDIATE("intermediate"),
    ADVANCED("advanced"),
    ULTIMATE("ultimate");

    private final String id;

    SkillTier(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "skill_tier.universal_tool." + id;
    }
}
