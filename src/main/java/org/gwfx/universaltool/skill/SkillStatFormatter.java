package org.gwfx.universaltool.skill;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * 格式化全部 32 项技能在 Lv.1 / Lv.2 / Lv.3 的数值与觉醒文案，与服务端实际执行公式严格保持一致。
 */
public final class SkillStatFormatter {
    private static final String KEY_PREFIX = "skill_stat.universal_tool.";

    public static String currentAndNext(String skillId, int currentLevel) {
        if (currentLevel <= 0) {
            return Component.translatable(KEY_PREFIX + "current_and_next.unlearned", format(skillId, 1)).getString();
        }
        if (currentLevel >= ModSkills.MAX_SKILL_LEVEL) {
            return Component.translatable(KEY_PREFIX + "current_and_next.maxed",
                    currentLevel, format(skillId, currentLevel)).getString();
        }
        return Component.translatable(KEY_PREFIX + "current_and_next.upgrade",
                currentLevel, format(skillId, currentLevel),
                currentLevel + 1, format(skillId, currentLevel + 1)).getString();
    }

    public static String currentText(String skillId, int currentLevel) {
        if (currentLevel <= 0) {
            return Component.translatable(KEY_PREFIX + "unlearned_detail").getString();
        }
        return Component.translatable(KEY_PREFIX + "level_prefix",
                currentLevel, format(skillId, currentLevel)).getString();
    }

    public static String nextText(String skillId, int currentLevel) {
        if (currentLevel >= ModSkills.MAX_SKILL_LEVEL) {
            return Component.translatable(KEY_PREFIX + "maxed_detail").getString();
        }
        int next = Math.max(1, currentLevel + 1);
        return Component.translatable(KEY_PREFIX + "level_prefix",
                next, format(skillId, next)).getString();
    }

    public static String format(String skillId, int level) {
        return formatComponent(skillId, level).getString();
    }

    public static Component formatComponent(String skillId, int level) {
        int safeLevel = Math.max(0, Math.min(ModSkills.MAX_SKILL_LEVEL, level));
        if (safeLevel == 0) {
            return Component.translatable(KEY_PREFIX + "unlearned");
        }
        return switch (skillId) {
            // 破军
            case ModSkills.BATTLE_MOMENTUM -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "battle_momentum_awake" : "battle_momentum"),
                    decimal(4 * safeLevel));
            case ModSkills.BLOODTHIRST -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "bloodthirst_awake" : "bloodthirst"),
                    5 * safeLevel);
            case ModSkills.ARMOR_PIERCING -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "armor_piercing_awake" : "armor_piercing"),
                    15 * safeLevel, safeLevel == 1 ? 10 : safeLevel == 2 ? 18 : 25);
            case ModSkills.ASTRAL_CLEAVE -> Component.translatable(
                    KEY_PREFIX + "astral_cleave", 14 + (safeLevel - 1) * 8, safeLevel == 1 ? 4 : safeLevel == 2 ? 7 : 10);
            case ModSkills.TOOL_RESONANCE -> Component.translatable(
                    KEY_PREFIX + "tool_resonance",
                    decimal(0.4 + 0.3 * safeLevel),
                    6 * safeLevel,
                    safeLevel == 1 ? 3 : safeLevel == 2 ? 2 : 1);
            case ModSkills.BERSERK -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "berserk_awake" : "berserk"),
                    roman(safeLevel), roman(safeLevel));
            case ModSkills.LIGHTNING_SPEAR -> Component.translatable(
                    KEY_PREFIX + "lightning_spear",
                    decimal((8 + 5 * safeLevel) * 2.5),
                    safeLevel == 1 ? 5 : safeLevel == 2 ? 8 : 12);
            case ModSkills.THUNDER_JUDGMENT -> Component.translatable(
                    KEY_PREFIX + "thunder_judgment",
                    22 + (safeLevel - 1) * 12,
                    safeLevel == 1 ? 8 : safeLevel == 2 ? 11 : 15,
                    decimal(3.5 + safeLevel));

            // 玄武
            case ModSkills.IRON_PHYSIQUE -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "iron_physique_double" : "iron_physique"),
                    safeLevel == 1 ? "6" : safeLevel == 2 ? "12" : "20");
            case ModSkills.SURVIVAL_FLOW -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "survival_flow_awake" : "survival_flow"),
                    decimal(safeLevel));
            case ModSkills.COUNTER_GUARD -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "counter_guard_resist" : "counter_guard"),
                    safeLevel == 1 ? "150" : safeLevel == 2 ? "200" : "260");
            case ModSkills.LAST_STAND -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "last_stand_awake" : "last_stand"),
                    decimal(15 * safeLevel));
            case ModSkills.DIAMOND_BULWARK -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "diamond_bulwark_awake" : "diamond_bulwark"),
                    (int) SkillManager.bulwarkArmor(safeLevel),
                    (int) SkillManager.bulwarkToughness(safeLevel));
            case ModSkills.STARLIGHT_SANCTUARY -> Component.translatable(
                    KEY_PREFIX + "starlight_sanctuary",
                    decimal(4.5 + safeLevel), 5 + safeLevel * 2, decimal(1.5 + safeLevel));
            case ModSkills.DAMAGE_CLAMP -> Component.translatable(
                    KEY_PREFIX + "damage_clamp", Math.round(SkillManager.damageClampRatio(safeLevel) * 100));
            case ModSkills.PHOENIX_EMBER -> Component.translatable(
                    KEY_PREFIX + "phoenix_ember", 7 - safeLevel, 6 + 4 * safeLevel);

            // 天枢
            case ModSkills.SWIFT_PACE -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "swift_pace_awake" : "swift_pace"),
                    safeLevel == 1 ? "10" : safeLevel == 2 ? "18" : "25");
            case ModSkills.LIGHT_FEATHER -> safeLevel >= 3
                    ? Component.translatable(KEY_PREFIX + "light_feather_immune")
                    : Component.translatable(KEY_PREFIX + "light_feather", safeLevel == 1 ? "40" : "75");
            case ModSkills.AIR_STEP -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "air_step_awake" : "air_step"),
                    SkillManager.airStepDurationTicks(safeLevel) / 20);
            case ModSkills.GRAVITY_WELL -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "gravity_well_awake" : "gravity_well"),
                    (int) SkillManager.gravityWellRadius(safeLevel),
                    SkillManager.gravityWellDurationSeconds(safeLevel));
            case ModSkills.FROZEN_PULL -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "frozen_pull_awake" : "frozen_pull"),
                    16 + 4 * safeLevel, SkillManager.freezeDurationSeconds(safeLevel));
            case ModSkills.OPPRESSIVE_FIELD -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "oppressive_field_40" : "oppressive_field_20"),
                    (int) SkillManager.oppressiveRadius(safeLevel),
                    SkillManager.oppressiveDurationTicks(safeLevel) / 20,
                    safeLevel == 1 ? 4 : safeLevel == 2 ? 6 : 10,
                    safeLevel == 1 ? 4 : safeLevel == 2 ? 6 : 10);
            case ModSkills.SPATIAL_ATTUNEMENT -> Component.translatable(
                    KEY_PREFIX + "spatial_attunement",
                    5 * safeLevel,
                    decimal(0.5 * safeLevel),
                    8 * safeLevel,
                    safeLevel == 1 ? "20" : safeLevel == 2 ? "35" : "50");
            case ModSkills.VOID_BLINK -> Component.translatable(
                    KEY_PREFIX + "void_blink",
                    24 + (safeLevel - 1) * 12,
                    safeLevel == 1 ? 30 : safeLevel == 2 ? 45 : 60,
                    safeLevel == 1 ? 6 : safeLevel == 2 ? 9 : 12);

            // 巧匠
            case ModSkills.FOCUS_MINING -> safeLevel >= 3
                    ? Component.translatable(KEY_PREFIX + "focus_mining_awake", roman(safeLevel))
                    : Component.translatable(KEY_PREFIX + "focus_mining", roman(safeLevel));
            case ModSkills.ITEM_MAGNET -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "item_magnet_awake" : "item_magnet"),
                    safeLevel == 1 ? 8 : safeLevel == 2 ? 12 : 16);
            case ModSkills.ORE_SIGHT -> Component.translatable(
                    KEY_PREFIX + "ore_sight", 12 + 4 * safeLevel, 8 + safeLevel);
            case ModSkills.EXPLORERS_EYE -> Component.translatable(
                    KEY_PREFIX + (safeLevel == 1 ? "explorers_eye" : safeLevel == 2 ? "explorers_eye_lv2" : "explorers_eye_awake"),
                    safeLevel == 1 ? "20" : safeLevel == 2 ? "35" : "50");
            case ModSkills.VEIN_RESONANCE -> Component.translatable(
                    KEY_PREFIX + "vein_resonance",
                    safeLevel == 1 ? 6 : safeLevel == 2 ? 9 : 12,
                    safeLevel == 1 ? 6 : safeLevel == 2 ? 12 : 20);
            case ModSkills.ARCANE_TRANSMUTE -> Component.translatable(
                    KEY_PREFIX + "arcane_transmute",
                    safeLevel == 1 ? 16 : safeLevel == 2 ? 32 : 64,
                    safeLevel == 1 ? "8" : safeLevel == 2 ? "16" : "25");
            case ModSkills.HYPER_CONDUIT -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "hyper_conduit_awake" : "hyper_conduit"),
                    safeLevel,
                    safeLevel == 1 ? "20" : safeLevel == 2 ? "35" : "50");
            case ModSkills.VOID_FORTUNE -> Component.translatable(
                    KEY_PREFIX + "void_fortune",
                    safeLevel,
                    safeLevel == 1 ? "10" : safeLevel == 2 ? "20" : "35",
                    safeLevel == 1 ? "25" : safeLevel == 2 ? "50" : "80",
                    safeLevel == 1 ? "12" : safeLevel == 2 ? "24" : "40");
            default -> Component.translatable(KEY_PREFIX + "none");
        };
    }

    private static String decimal(double value) {
        if (value == Math.rint(value)) {
            return String.format(Locale.ROOT, "%.0f", value);
        }
        if (Math.rint(value * 10) == value * 10) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> Integer.toString(value);
        };
    }

    private SkillStatFormatter() {}
}
