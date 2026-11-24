package org.gwfx.universaltool.skill;

/**
 * 技能可视化试炼条件规格，支持单路径或“原版 / 模组联动”双路径达成。
 *
 * @param statKey     主统计键名
 * @param target      主目标阈值
 * @param altStatKey  可选替代统计键名（为空表示无替代条件）
 * @param altTarget   替代目标阈值
 * @param descKey     试炼描述翻译键
 */
public record SkillTrialSpec(
        String statKey,
        int target,
        String altStatKey,
        int altTarget,
        String descKey
) {
    public static SkillTrialSpec of(String statKey, int target, String descKey) {
        return new SkillTrialSpec(statKey, Math.max(1, target), "", 0, descKey);
    }

    public static SkillTrialSpec dual(String statKey, int target, String altStatKey, int altTarget, String descKey) {
        return new SkillTrialSpec(statKey, Math.max(1, target),
                altStatKey == null ? "" : altStatKey, Math.max(0, altTarget), descKey);
    }

    public boolean hasAlt() {
        return altStatKey != null && !altStatKey.isEmpty() && altTarget > 0;
    }
}
