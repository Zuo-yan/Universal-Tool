package org.gwfx.universaltool.skill;

/**
 * 技能星图四大星座派系。
 */
public enum SkillConstellation {
    POJUN("pojun", 0xFFFF6B4A, 0xFFFFB48A),
    XUANWU("xuanwu", 0xFF3ED8B5, 0xFF9EFFE6),
    TIANSHU("tianshu", 0xFF9F7BFF, 0xFFD4C4FF),
    QIAOJIANG("qiaojiang", 0xFFF2BE49, 0xFFFFE699);

    private final String id;
    private final int primaryColor;
    private final int glowColor;

    SkillConstellation(String id, int primaryColor, int glowColor) {
        this.id = id;
        this.primaryColor = primaryColor;
        this.glowColor = glowColor;
    }

    public String id() {
        return id;
    }

    public int primaryColor() {
        return primaryColor;
    }

    public int glowColor() {
        return glowColor;
    }

    public String translationKey() {
        return "constellation.universal_tool." + id;
    }

    public String resonance2Key() {
        return "constellation.universal_tool." + id + ".res2";
    }

    public String resonance4Key() {
        return "constellation.universal_tool." + id + ".res4";
    }

    public String resonance6Key() {
        return "constellation.universal_tool." + id + ".res6";
    }
}
