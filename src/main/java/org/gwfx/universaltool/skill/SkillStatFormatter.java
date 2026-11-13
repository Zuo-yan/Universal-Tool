package org.gwfx.universaltool.skill;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Formats the same level formulas used by skill execution for the detail screen. */
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
            case ModSkills.COUNTER_GUARD -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "counter_guard_resist" : "counter_guard"),
                    decimal(125 + 25 * (safeLevel - 1)));
            case ModSkills.GRAVITY_WELL -> Component.translatable(
                    KEY_PREFIX + "gravity_well",
                    (int) SkillManager.gravityWellRadius(safeLevel),
                    SkillManager.gravityWellDurationSeconds(safeLevel));
            case ModSkills.FROZEN_PULL -> Component.translatable(
                    KEY_PREFIX + "frozen_pull",
                    16 + 4 * safeLevel,
                    SkillManager.freezeDurationSeconds(safeLevel));
            case ModSkills.OPPRESSIVE_FIELD -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "oppressive_field_40" : "oppressive_field_20"),
                    (int) SkillManager.oppressiveRadius(safeLevel),
                    SkillManager.oppressiveDurationTicks(safeLevel) / 20);
            case ModSkills.ORE_SIGHT -> Component.translatable(
                    KEY_PREFIX + "ore_sight",
                    12 + 4 * safeLevel,
                    8 + safeLevel);
            case ModSkills.AIR_STEP -> Component.translatable(
                    KEY_PREFIX + "air_step",
                    SkillManager.airStepDurationTicks(safeLevel) / 20);
            case ModSkills.LIGHTNING_SPEAR -> Component.translatable(
                    KEY_PREFIX + "lightning_spear",
                    decimal((8 + 5 * safeLevel) * 2.5),
                    24 + 6 * safeLevel);
            case ModSkills.BERSERK -> Component.translatable(
                    KEY_PREFIX + "berserk",
                    safeLevel,
                    safeLevel);
            case ModSkills.BATTLE_MOMENTUM -> Component.translatable(
                    KEY_PREFIX + "battle_momentum",
                    decimal(4 * safeLevel));
            case ModSkills.FOCUS_MINING -> Component.translatable(
                    KEY_PREFIX + "focus_mining",
                    roman(safeLevel));
            case ModSkills.LAST_STAND -> Component.translatable(
                    KEY_PREFIX + "last_stand",
                    decimal(15 * safeLevel));
            case ModSkills.SURVIVAL_FLOW -> Component.translatable(
                    KEY_PREFIX + "survival_flow",
                    decimal(0.5 * safeLevel));
            case ModSkills.SWIFT_PACE -> Component.translatable(
                    KEY_PREFIX + "swift_pace",
                    safeLevel == 1 ? "10" : safeLevel == 2 ? "18" : "25");
            case ModSkills.LIGHT_FEATHER -> safeLevel >= 3
                    ? Component.translatable(KEY_PREFIX + "light_feather_immune")
                    : Component.translatable(KEY_PREFIX + "light_feather", safeLevel == 1 ? "40" : "75");
            case ModSkills.IRON_PHYSIQUE -> Component.translatable(
                    KEY_PREFIX + (safeLevel >= 3 ? "iron_physique_double" : "iron_physique"),
                    safeLevel == 1 ? "6" : safeLevel == 2 ? "12" : "20");
            case ModSkills.ARMOR_PIERCING -> Component.translatable(
                    KEY_PREFIX + "armor_piercing",
                    15 * safeLevel);
            case ModSkills.BLOODTHIRST -> Component.translatable(
                    KEY_PREFIX + "bloodthirst",
                    5 * safeLevel);
            case ModSkills.ITEM_MAGNET -> Component.translatable(
                    KEY_PREFIX + "item_magnet",
                    5 + 2 * safeLevel);
            case ModSkills.DIAMOND_BULWARK -> Component.translatable(
                    KEY_PREFIX + "diamond_bulwark",
                    (int) SkillManager.bulwarkArmor(safeLevel),
                    (int) SkillManager.bulwarkToughness(safeLevel));
            case ModSkills.DAMAGE_CLAMP -> Component.translatable(
                    KEY_PREFIX + "damage_clamp",
                    Math.round(SkillManager.damageClampRatio(safeLevel) * 100));
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
