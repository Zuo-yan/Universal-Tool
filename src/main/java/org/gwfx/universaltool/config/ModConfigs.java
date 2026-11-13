package org.gwfx.universaltool.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common NeoForge configuration for Universal Tool.
 * Safe getters fall back to default values if accessed prior to config load (e.g. in unit/GameTests).
 */
public final class ModConfigs {

    private static final int DEFAULT_POLYMERIZER_TICKS = 30;
    private static final int DEFAULT_TELEPORT_COOLDOWN_TICKS = 100;
    private static final int DEFAULT_PHOENIX_CULTIVATION_TICKS = 1200;
    private static final double DEFAULT_SKILL_XP_MULTIPLIER = 1.0;
    private static final double DEFAULT_LOTTERY_BASE_SCRAP_CHANCE = 0.35;

    public static final ModConfigSpec COMMON_SPEC;

    public static final ModConfigSpec.IntValue POLYMERIZER_DEFAULT_TICKS;
    public static final ModConfigSpec.IntValue TELEPORT_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue PHOENIX_CULTIVATION_TICKS;
    public static final ModConfigSpec.DoubleValue SKILL_XP_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LOTTERY_BASE_SCRAP_CHANCE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Common gameplay balance configuration for Universal Tool").push("general");

        POLYMERIZER_DEFAULT_TICKS = builder
                .comment("Default crafting duration in ticks for the Universal Polymerizer when a recipe does not specify one.")
                .defineInRange("polymerizerDefaultTicks", DEFAULT_POLYMERIZER_TICKS, 1, 72000);

        TELEPORT_COOLDOWN_TICKS = builder
                .comment("Cooldown in ticks applied to the Teleport Codex after a completed teleport.")
                .defineInRange("teleportCooldownTicks", DEFAULT_TELEPORT_COOLDOWN_TICKS, 0, 72000);

        PHOENIX_CULTIVATION_TICKS = builder
                .comment("Time in ticks required for an Operation Phoenix Pod to cultivate a clone.")
                .defineInRange("phoenixCultivationTicks", DEFAULT_PHOENIX_CULTIVATION_TICKS, 20, 72000);

        SKILL_XP_MULTIPLIER = builder
                .comment("Global multiplier applied to Skill Circuit growth XP gains.")
                .defineInRange("skillXpMultiplier", DEFAULT_SKILL_XP_MULTIPLIER, 0.0, 100.0);

        LOTTERY_BASE_SCRAP_CHANCE = builder
                .comment("Base probability (0.0 - 1.0) of receiving Void Scrap on a low-value lottery draw.")
                .defineInRange("lotteryBaseScrapChance", DEFAULT_LOTTERY_BASE_SCRAP_CHANCE, 0.0, 1.0);

        builder.pop();
        COMMON_SPEC = builder.build();
    }

    private ModConfigs() {
    }

    /**
     * Registers the COMMON config specification on the provided {@link ModContainer}.
     */
    public static void register(ModContainer container) {
        if (container != null) {
            container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
        }
    }

    public static int getPolymerizerDefaultTicks() {
        return COMMON_SPEC.isLoaded() ? POLYMERIZER_DEFAULT_TICKS.get() : DEFAULT_POLYMERIZER_TICKS;
    }

    public static int getTeleportCooldownTicks() {
        return COMMON_SPEC.isLoaded() ? TELEPORT_COOLDOWN_TICKS.get() : DEFAULT_TELEPORT_COOLDOWN_TICKS;
    }

    public static int getPhoenixCultivationTicks() {
        return COMMON_SPEC.isLoaded() ? PHOENIX_CULTIVATION_TICKS.get() : DEFAULT_PHOENIX_CULTIVATION_TICKS;
    }

    public static double getSkillXpMultiplier() {
        return COMMON_SPEC.isLoaded() ? SKILL_XP_MULTIPLIER.get() : DEFAULT_SKILL_XP_MULTIPLIER;
    }

    public static double getLotteryBaseScrapChance() {
        return COMMON_SPEC.isLoaded() ? LOTTERY_BASE_SCRAP_CHANCE.get() : DEFAULT_LOTTERY_BASE_SCRAP_CHANCE;
    }
}
