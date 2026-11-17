package org.gwfx.universaltool.space;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable data specification for each pocket realm's runtime rules, natural spawns, ore strata,
 * and lightweight environmental perks.
 */
public record PrivateSpaceWorldTraits(
        TimeRule time,
        WeatherRule weather,
        SpawnPolicy spawns,
        OreProfile ores,
        EnvironmentalPerks perks,
        float ambientLight,
        boolean piglinSafe
) {
    /** Astronomical clock policy enforced through the dimension type. */
    public enum TimeRule {
        NORMAL(OptionalLong.empty()),
        ETERNAL_DAY(OptionalLong.of(6000L)),
        ETERNAL_NIGHT(OptionalLong.of(18000L));

        private final OptionalLong fixedTime;

        TimeRule(OptionalLong fixedTime) {
            this.fixedTime = fixedTime;
        }

        public OptionalLong fixedTime() {
            return fixedTime;
        }
    }

    /** Precipitation behavior enforced independently of the overworld on {@link PersonalDimensionLevelData}. */
    public enum WeatherRule {
        NORMAL,
        ALWAYS_CLEAR,
        SNOW_FLURRY
    }

    /** Natural spawning rule; artificial spawns (spawners, eggs, breeding, commands) are never blocked. */
    public enum SpawnMode {
        NO_NATURAL,
        WHITELIST
    }

    /** Weighted natural spawn entry scoped by {@link MobCategory}. */
    public record SpawnEntry(EntityType<?> type, MobCategory category, int weight, int minCount, int maxCount) {
        public MobSpawnSettings.SpawnerData toSpawnerData() {
            return new MobSpawnSettings.SpawnerData(type, weight, minCount, maxCount);
        }
    }

    /** Natural mob whitelist and hostile spawn rate multiplier for one realm. */
    public static final class SpawnPolicy {
        public static final SpawnPolicy NO_NATURAL = new SpawnPolicy(SpawnMode.NO_NATURAL, 1.0, List.of());

        private final SpawnMode mode;
        private final double hostileRateMultiplier;
        private final List<SpawnEntry> entries;
        private final Set<EntityType<?>> allowedTypes;
        private final Map<MobCategory, List<MobSpawnSettings.SpawnerData>> byCategory;

        public SpawnPolicy(SpawnMode mode, double hostileRateMultiplier, List<SpawnEntry> entries) {
            this.mode = mode;
            this.hostileRateMultiplier = hostileRateMultiplier;
            this.entries = List.copyOf(entries);
            this.allowedTypes = entries.stream().map(SpawnEntry::type).collect(Collectors.toUnmodifiableSet());
            Map<MobCategory, List<MobSpawnSettings.SpawnerData>> grouped = new EnumMap<>(MobCategory.class);
            for (MobCategory cat : MobCategory.values()) {
                List<MobSpawnSettings.SpawnerData> list = entries.stream()
                        .filter(entry -> entry.category() == cat)
                        .map(SpawnEntry::toSpawnerData)
                        .toList();
                if (!list.isEmpty()) {
                    grouped.put(cat, list);
                }
            }
            this.byCategory = grouped;
        }

        public static SpawnPolicy whitelist(double hostileRateMultiplier, SpawnEntry... entries) {
            return new SpawnPolicy(SpawnMode.WHITELIST, hostileRateMultiplier, List.of(entries));
        }

        public SpawnMode mode() {
            return mode;
        }

        public double hostileRateMultiplier() {
            return hostileRateMultiplier;
        }

        public boolean allowsNatural(EntityType<?> entityType) {
            return mode == SpawnMode.WHITELIST && allowedTypes.contains(entityType);
        }

        public List<MobSpawnSettings.SpawnerData> spawnerDataFor(MobCategory category) {
            return byCategory.getOrDefault(category, List.of());
        }

        public List<SpawnEntry> entries() {
            return entries;
        }
    }

    /**
     * One adaptive underground ore distribution rule inside a finite island's interior rock strata.
     * Depth fractions map cleanly onto {@code [rockTop .. rockBottom]} so deep ores remain reachable.
     */
    public record OreVeinRule(
            Block midOre,
            Block deepOre,
            double minDepthFraction,
            double maxDepthFraction,
            int baseAttemptsPerChunk,
            int veinSize
    ) {}

    /** Underground mineral configuration for one realm. */
    public record OreProfile(double rateMultiplier, boolean amethystGeodes, List<OreVeinRule> veins) {
        public static final OreProfile NONE = new OreProfile(0.0, false, List.of());
    }

    /** Gentle passive benefits granted only inside personal spaces (zero punitive debuffs). */
    public record EnvironmentalPerks(
            Holder<MobEffect> potionEffect,
            int amplifier,
            float fallDamageMultiplier,
            boolean freezeImmune,
            int nourishIntervalTicks
    ) {
        public static final EnvironmentalPerks NONE = new EnvironmentalPerks(null, 0, 1.0F, false, 0);
    }

    // ------------------------------------------------------------------ shared ore presets

    private static final List<OreVeinRule> BASIC_OVERWORLD_VEINS = List.of(
            new OreVeinRule(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, 0.00, 0.55, 6, 7),
            new OreVeinRule(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, 0.05, 0.65, 5, 6),
            new OreVeinRule(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, 0.10, 0.85, 6, 6),
            new OreVeinRule(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, 0.35, 0.95, 2, 5)
    );

    private static final List<OreVeinRule> FULL_OVERWORLD_VEINS = List.of(
            new OreVeinRule(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, 0.00, 0.55, 6, 8),
            new OreVeinRule(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, 0.05, 0.65, 5, 7),
            new OreVeinRule(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, 0.10, 0.88, 6, 6),
            new OreVeinRule(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, 0.32, 0.95, 3, 5),
            new OreVeinRule(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, 0.45, 1.00, 4, 6),
            new OreVeinRule(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE, 0.38, 0.95, 3, 5),
            new OreVeinRule(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, 0.30, 0.90, 2, 3),
            new OreVeinRule(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, 0.55, 1.00, 3, 4)
    );

    private static final List<OreVeinRule> EXPERIENCE_RICH_VEINS = List.of(
            new OreVeinRule(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, 0.10, 0.80, 4, 5),
            new OreVeinRule(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, 0.00, 0.50, 4, 6),
            new OreVeinRule(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, 0.35, 1.00, 5, 6),
            new OreVeinRule(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE, 0.30, 0.95, 5, 5),
            new OreVeinRule(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, 0.25, 0.90, 4, 3),
            new OreVeinRule(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, 0.50, 1.00, 3, 4)
    );

    private static final List<OreVeinRule> NETHER_FORGE_VEINS = List.of(
            new OreVeinRule(Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_QUARTZ_ORE, 0.00, 0.90, 9, 6),
            new OreVeinRule(Blocks.NETHER_GOLD_ORE, Blocks.NETHER_GOLD_ORE, 0.10, 0.95, 7, 5),
            new OreVeinRule(Blocks.MAGMA_BLOCK, Blocks.MAGMA_BLOCK, 0.25, 1.00, 4, 6),
            new OreVeinRule(Blocks.ANCIENT_DEBRIS, Blocks.ANCIENT_DEBRIS, 0.65, 1.00, 1, 2)
    );

    private static final List<OreVeinRule> GLACIER_CRYSTAL_VEINS = List.of(
            new OreVeinRule(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, 0.05, 0.55, 3, 6),
            new OreVeinRule(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, 0.15, 0.80, 4, 5),
            new OreVeinRule(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE, 0.30, 0.95, 5, 5),
            new OreVeinRule(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, 0.50, 1.00, 4, 4)
    );

    // ------------------------------------------------------------------ realm definitions

    public static final PrivateSpaceWorldTraits SUPERFLAT = new PrivateSpaceWorldTraits(
            TimeRule.ETERNAL_DAY,
            WeatherRule.ALWAYS_CLEAR,
            SpawnPolicy.NO_NATURAL,
            OreProfile.NONE,
            EnvironmentalPerks.NONE,
            0.0F,
            false
    );

    public static final PrivateSpaceWorldTraits SKY_ISLANDS = new PrivateSpaceWorldTraits(
            TimeRule.NORMAL,
            WeatherRule.NORMAL,
            SpawnPolicy.whitelist(1.0,
                    new SpawnEntry(EntityType.BEE, MobCategory.CREATURE, 6, 1, 3),
                    new SpawnEntry(EntityType.PARROT, MobCategory.CREATURE, 4, 1, 2),
                    new SpawnEntry(EntityType.ALLAY, MobCategory.CREATURE, 3, 1, 2)
            ),
            new OreProfile(1.0, false, BASIC_OVERWORLD_VEINS),
            new EnvironmentalPerks(MobEffects.JUMP, 0, 0.5F, false, 0),
            0.0F,
            false
    );

    public static final PrivateSpaceWorldTraits CHERRY_GROVE = new PrivateSpaceWorldTraits(
            TimeRule.NORMAL,
            WeatherRule.ALWAYS_CLEAR,
            SpawnPolicy.whitelist(1.0,
                    new SpawnEntry(EntityType.BEE, MobCategory.CREATURE, 6, 1, 3),
                    new SpawnEntry(EntityType.RABBIT, MobCategory.CREATURE, 5, 2, 3),
                    new SpawnEntry(EntityType.SHEEP, MobCategory.CREATURE, 5, 2, 4),
                    new SpawnEntry(EntityType.FOX, MobCategory.CREATURE, 3, 1, 2)
            ),
            new OreProfile(1.0, false, List.of(
                    new OreVeinRule(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, 0.00, 0.55, 5, 6),
                    new OreVeinRule(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, 0.05, 0.65, 5, 6),
                    new OreVeinRule(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, 0.15, 0.85, 5, 5),
                    new OreVeinRule(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, 0.30, 0.90, 2, 3)
            )),
            new EnvironmentalPerks(MobEffects.REGENERATION, 0, 1.0F, false, 0),
            0.0F,
            false
    );

    public static final PrivateSpaceWorldTraits AMETHYST_STARFALL = new PrivateSpaceWorldTraits(
            TimeRule.ETERNAL_NIGHT,
            WeatherRule.ALWAYS_CLEAR,
            SpawnPolicy.whitelist(1.0,
                    new SpawnEntry(EntityType.ENDERMAN, MobCategory.MONSTER, 6, 1, 2),
                    new SpawnEntry(EntityType.ALLAY, MobCategory.CREATURE, 4, 1, 2),
                    new SpawnEntry(EntityType.BAT, MobCategory.AMBIENT, 4, 1, 3),
                    new SpawnEntry(EntityType.GLOW_SQUID, MobCategory.UNDERGROUND_WATER_CREATURE, 4, 1, 3)
            ),
            new OreProfile(2.5, true, FULL_OVERWORLD_VEINS),
            new EnvironmentalPerks(MobEffects.NIGHT_VISION, 0, 1.0F, false, 0),
            0.04F,
            false
    );

    public static final PrivateSpaceWorldTraits SCULK_SANCTUM = new PrivateSpaceWorldTraits(
            TimeRule.ETERNAL_NIGHT,
            WeatherRule.ALWAYS_CLEAR,
            SpawnPolicy.whitelist(1.0,
                    new SpawnEntry(EntityType.CAVE_SPIDER, MobCategory.MONSTER, 6, 1, 3),
                    new SpawnEntry(EntityType.SKELETON, MobCategory.MONSTER, 6, 1, 3),
                    new SpawnEntry(EntityType.CREEPER, MobCategory.MONSTER, 5, 1, 2),
                    new SpawnEntry(EntityType.ENDERMAN, MobCategory.MONSTER, 4, 1, 2),
                    new SpawnEntry(EntityType.WITCH, MobCategory.MONSTER, 3, 1, 1),
                    new SpawnEntry(EntityType.SILVERFISH, MobCategory.MONSTER, 4, 1, 3)
            ),
            new OreProfile(1.5, false, EXPERIENCE_RICH_VEINS),
            EnvironmentalPerks.NONE,
            0.0F,
            false
    );

    public static final PrivateSpaceWorldTraits MUSHROOM_WONDERLAND = new PrivateSpaceWorldTraits(
            TimeRule.NORMAL,
            WeatherRule.NORMAL,
            SpawnPolicy.whitelist(1.0,
                    new SpawnEntry(EntityType.MOOSHROOM, MobCategory.CREATURE, 10, 2, 4)
            ),
            OreProfile.NONE,
            new EnvironmentalPerks(MobEffects.SATURATION, 0, 1.0F, false, 0),
            0.0F,
            false
    );

    public static final PrivateSpaceWorldTraits OBSIDIAN_FORGE = new PrivateSpaceWorldTraits(
            TimeRule.ETERNAL_NIGHT,
            WeatherRule.ALWAYS_CLEAR,
            SpawnPolicy.whitelist(2.0,
                    new SpawnEntry(EntityType.ZOMBIFIED_PIGLIN, MobCategory.MONSTER, 8, 2, 4),
                    new SpawnEntry(EntityType.PIGLIN, MobCategory.MONSTER, 6, 2, 3),
                    new SpawnEntry(EntityType.MAGMA_CUBE, MobCategory.MONSTER, 6, 1, 3),
                    new SpawnEntry(EntityType.BLAZE, MobCategory.MONSTER, 5, 1, 2),
                    new SpawnEntry(EntityType.WITHER_SKELETON, MobCategory.MONSTER, 4, 1, 2)
            ),
            new OreProfile(1.0, false, NETHER_FORGE_VEINS),
            EnvironmentalPerks.NONE,
            0.06F,
            true
    );

    public static final PrivateSpaceWorldTraits AURORA_GLACIER = new PrivateSpaceWorldTraits(
            TimeRule.ETERNAL_NIGHT,
            WeatherRule.SNOW_FLURRY,
            SpawnPolicy.whitelist(1.0,
                    new SpawnEntry(EntityType.POLAR_BEAR, MobCategory.CREATURE, 5, 1, 2),
                    new SpawnEntry(EntityType.FOX, MobCategory.CREATURE, 6, 1, 3),
                    new SpawnEntry(EntityType.RABBIT, MobCategory.CREATURE, 5, 2, 3),
                    new SpawnEntry(EntityType.STRAY, MobCategory.MONSTER, 5, 1, 3)
            ),
            new OreProfile(1.8, false, GLACIER_CRYSTAL_VEINS),
            new EnvironmentalPerks(MobEffects.MOVEMENT_SPEED, 0, 1.0F, true, 0),
            0.04F,
            false
    );
}
