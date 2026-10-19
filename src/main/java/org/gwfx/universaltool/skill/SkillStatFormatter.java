package org.gwfx.universaltool.skill;

import java.util.Locale;

/** Formats the same level formulas used by skill execution for the detail screen. */
public final class SkillStatFormatter {
    public static String currentAndNext(String skillId, int currentLevel) {
        if (currentLevel <= 0) {
            return "未学会  →  Lv.1 " + format(skillId, 1);
        }
        return "当前 Lv." + currentLevel + " " + format(skillId, currentLevel)
                + (currentLevel >= ModSkills.MAX_SKILL_LEVEL
                ? "  ·  已达最高级"
                : "  →  Lv." + (currentLevel + 1) + " " + format(skillId, currentLevel + 1));
    }

    public static String currentText(String skillId, int currentLevel) {
        if (currentLevel <= 0) return "未学习此技能";
        return "Lv." + currentLevel + " " + format(skillId, currentLevel);
    }

    public static String nextText(String skillId, int currentLevel) {
        if (currentLevel >= ModSkills.MAX_SKILL_LEVEL) return "已达到最高等级";
        int next = Math.max(1, currentLevel + 1);
        return "Lv." + next + " " + format(skillId, next);
    }

    public static String format(String skillId, int level) {
        int safeLevel = Math.max(0, Math.min(ModSkills.MAX_SKILL_LEVEL, level));
        if (safeLevel == 0) return "未学会";
        return switch (skillId) {
            case ModSkills.COUNTER_GUARD -> decimal(125 + 25 * (safeLevel - 1)) + "% 反伤"
                    + (safeLevel >= 3 ? " · 反弹后抗性 4秒" : "");
            case ModSkills.GRAVITY_WELL -> (12 + 2 * safeLevel)
                    + "格半径 · 黑洞持续 5秒 · 连续牵引";
            case ModSkills.FROZEN_PULL -> (16 + 4 * safeLevel)
                    + "格索敌 · 冻结 5秒 · 每0.5秒4%最大生命伤害";
            case ModSkills.OPPRESSIVE_FIELD -> (int) SkillManager.oppressiveRadius(safeLevel) + "格半径 · 虚弱 III "
                    + (SkillManager.oppressiveDurationTicks(safeLevel) / 20) + "秒"
                    + (safeLevel >= 3 ? " · 自身减伤 40%" : " · 自身减伤 20%");
            case ModSkills.ORE_SIGHT -> (12 + 4 * safeLevel) + "格半径 · 垂直 "
                    + (8 + safeLevel) + "格 · 持续 10秒";
            case ModSkills.AIR_STEP -> "按住持续推进 " + (SkillManager.airStepDurationTicks(safeLevel) / 20) + "秒 · 松开结束";
            case ModSkills.LIGHTNING_SPEAR -> decimal(8 + 5 * safeLevel) + " 满蓄伤害 · "
                    + (24 + 6 * safeLevel) + "格射程";
            case ModSkills.BERSERK -> "+" + safeLevel + "速度 · +" + safeLevel + "力量"
                    + (safeLevel >= 3 ? " · 减伤 40%" : " · 减伤 20%") + " · 持续 10秒";
            case ModSkills.BATTLE_MOMENTUM -> "每层 +" + decimal(4 * safeLevel)
                    + "%伤害 · 最多5层";
            case ModSkills.FOCUS_MINING -> "急迫 " + roman(safeLevel) + " · 持续刷新";
            case ModSkills.LAST_STAND -> decimal(15 * safeLevel) + "%减伤 · 低于35%生命";
            case ModSkills.SURVIVAL_FLOW -> decimal(0.5 * safeLevel) + "生命/秒 · 脱战";
            case ModSkills.SWIFT_PACE -> "+" + (safeLevel == 1 ? "10" : safeLevel == 2 ? "18" : "25") + "% 移速 · 疾跑跳跃减耗";
            case ModSkills.LIGHT_FEATHER -> safeLevel >= 3 ? "完全免疫摔落伤害" : "-" + (safeLevel == 1 ? "40" : "75") + "% 摔落伤害";
            case ModSkills.IRON_PHYSIQUE -> "+" + (safeLevel == 1 ? "6" : safeLevel == 2 ? "12" : "20") + " 最大生命值" + (safeLevel >= 3 ? " (血量翻倍)" : "");
            case ModSkills.ARMOR_PIERCING -> "穿透 " + (15 * safeLevel) + "% 目标护甲";
            case ModSkills.BLOODTHIRST -> "近战吸血 " + (5 * safeLevel) + "%";
            case ModSkills.ITEM_MAGNET -> (5 + 2 * safeLevel) + "格 物品经验吸附半径";
            case ModSkills.DIAMOND_BULWARK -> "+" + (int) SkillManager.bulwarkArmor(safeLevel) + " 护甲 · +" + (int) SkillManager.bulwarkToughness(safeLevel) + " 护甲韧性";
            case ModSkills.DAMAGE_CLAMP -> "单次最终伤害上限：最大生命值的 " + Math.round(SkillManager.damageClampRatio(safeLevel) * 100) + "%";
            default -> "无";
        };
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, value == Math.rint(value) ? "%.0f" : "%.2f", value);
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> Integer.toString(value);
        };
    }

    private SkillStatFormatter() {
    }
}
