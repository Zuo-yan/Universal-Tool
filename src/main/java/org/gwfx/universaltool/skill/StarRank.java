package org.gwfx.universaltool.skill;

/**
 * 玩家五大星阶境界，随成长等级解锁主被动装配槽位与星座共鸣权限。
 */
public enum StarRank {
    STARDUST(1, "stardust", 2, 2, 0, 0xFF94A3B8),
    ORBITER(10, "orbiter", 3, 4, 0, 0xFF62D8E6),
    RADIANT(20, "radiant", 4, 5, 1, 0xFFF2C95A),
    WEAVER(30, "weaver", 5, 6, 2, 0xFFB48BFF),
    SOVEREIGN(40, "sovereign", 6, 8, 2, 0xFFFF9654);

    private final int minLevel;
    private final String id;
    private final int activeSlots;
    private final int passiveSlots;
    private final int maxResonanceTier;
    private final int accentColor;

    StarRank(int minLevel, String id, int activeSlots, int passiveSlots, int maxResonanceTier, int accentColor) {
        this.minLevel = minLevel;
        this.id = id;
        this.activeSlots = activeSlots;
        this.passiveSlots = passiveSlots;
        this.maxResonanceTier = maxResonanceTier;
        this.accentColor = accentColor;
    }

    public int minLevel() {
        return minLevel;
    }

    public String id() {
        return id;
    }

    public int activeSlots() {
        return activeSlots;
    }

    public int passiveSlots() {
        return passiveSlots;
    }

    /**
     * 0 = 未解锁共鸣；1 = 解锁 2 件共鸣；2 = 解锁 2 件与 4 件共鸣。
     */
    public int maxResonanceTier() {
        return maxResonanceTier;
    }

    public int accentColor() {
        return accentColor;
    }

    public String translationKey() {
        return "star_rank.universal_tool." + id;
    }

    public static StarRank forLevel(int level) {
        StarRank result = STARDUST;
        for (StarRank rank : values()) {
            if (level >= rank.minLevel) {
                result = rank;
            }
        }
        return result;
    }
}
