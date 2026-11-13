package org.gwfx.universaltool.space;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Every Pocket Realm landscape a player can pick when creating a private realm.
 *
 * <p>Each constant carries a {@link Profile}. Terrain code reads the profile instead of branching on
 * the constant, so adding a landscape is a data change rather than a code change.
 *
 * <p><b>Frozen:</b> existing id strings are persisted in {@code PersonalSpaceSavedData} and inside
 * the chunk-generator codec. Legacy ids from earlier versions are mapped in {@link #byId(String)}.
 */
public enum PrivateSpaceEnvironment {
    SUPERFLAT("superflat", new Profile(Prototype.FLAT, LandmarkType.STARLIT_FLAT, Biomes.PLAINS,
            71.0, 0.0, 71.0, 55.0, 0.0, 19.0, 0.0,
            new Surface(Blocks.GRASS_BLOCK, Blocks.DIRT, 3, false,
                    Blocks.STONE, Blocks.DEEPSLATE, Blocks.SEA_LANTERN),
            Vegetation.NONE,
            new SpawnArea(3, null, null)), PrivateSpaceWorldTraits.SUPERFLAT),
    SKY_ISLANDS("sky_islands", new Profile(Prototype.SKY, LandmarkType.SKY_ARCHIPELAGO, Biomes.PLAINS,
            78.0, 1.2, 78.0, 55.0, 12.0, 19.0, 3.0,
            new Surface(Blocks.GRASS_BLOCK, Blocks.DIRT, 3, false,
                    Blocks.STONE, Blocks.DEEPSLATE, Blocks.GLOWSTONE),
            new Vegetation(15, false, 5, Blocks.OAK_LOG, Blocks.FLOWERING_AZALEA_LEAVES,
                    10, Blocks.SHORT_GRASS, Blocks.AZURE_BLUET),
            new SpawnArea(3, null, null)), PrivateSpaceWorldTraits.SKY_ISLANDS),
    CHERRY_GROVE("cherry_grove", new Profile(Prototype.HIGHLAND, LandmarkType.MYSTIC_CHERRY, Biomes.CHERRY_GROVE,
            89.0, 1.6, 79.0, 55.0, 12.0, 19.0, 3.0,
            new Surface(Blocks.GRASS_BLOCK, Blocks.DIRT, 3, false,
                    Blocks.STONE, Blocks.DEEPSLATE, Blocks.AMETHYST_BLOCK),
            new Vegetation(3, true, 6, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES,
                    14, Blocks.PINK_PETALS, Blocks.SHORT_GRASS),
            new SpawnArea(8, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES)), PrivateSpaceWorldTraits.CHERRY_GROVE),
    AMETHYST_STARFALL("amethyst_starfall", new Profile(Prototype.PEAKS, LandmarkType.AMETHYST_SPIRE, Biomes.MEADOW,
            84.0, 1.45, 79.0, 48.0, 13.0, 16.0, 3.5,
            new Surface(Blocks.CALCITE, Blocks.AMETHYST_BLOCK, 2, false,
                    Blocks.SMOOTH_BASALT, Blocks.DEEPSLATE, Blocks.SEA_LANTERN),
            new Vegetation(7, false, 7, Blocks.AMETHYST_BLOCK, null,
                    10, Blocks.AMETHYST_CLUSTER, Blocks.LARGE_AMETHYST_BUD),
            new SpawnArea(3, null, null)), PrivateSpaceWorldTraits.AMETHYST_STARFALL),
    SCULK_SANCTUM("sculk_sanctum", new Profile(Prototype.SANCTUM, LandmarkType.SCULK_OBELISK, Biomes.DEEP_DARK,
            78.0, 1.1, 79.0, 62.0, 11.0, 22.0, 2.5,
            new Surface(Blocks.SCULK, Blocks.DEEPSLATE_TILES, 3, false,
                    Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE, Blocks.SEA_LANTERN),
            new Vegetation(7, false, 7, Blocks.DEEPSLATE_TILES, null,
                    6, Blocks.TWISTING_VINES, Blocks.SCULK_CATALYST),
            new SpawnArea(3, null, null)), PrivateSpaceWorldTraits.SCULK_SANCTUM),
    MUSHROOM_WONDERLAND("mushroom_wonderland", new Profile(Prototype.ROLLING, LandmarkType.GIANT_MUSHROOM, Biomes.MUSHROOM_FIELDS,
            76.0, 1.1, 79.0, 54.0, 11.0, 18.0, 2.8,
            new Surface(Blocks.MYCELIUM, Blocks.MOSS_BLOCK, 3, false,
                    Blocks.TUFF, Blocks.DEEPSLATE, Blocks.SHROOMLIGHT),
            new Vegetation(7, false, 6, Blocks.MUSHROOM_STEM, Blocks.RED_MUSHROOM_BLOCK,
                    14, Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM),
            new SpawnArea(3, null, null)), PrivateSpaceWorldTraits.MUSHROOM_WONDERLAND),
    OBSIDIAN_FORGE("obsidian_forge", new Profile(Prototype.CALDERA, LandmarkType.OBSIDIAN_PILLAR, Biomes.BASALT_DELTAS,
            77.0, 1.25, 79.0, 68.0, 12.0, 24.0, 3.0,
            new Surface(Blocks.BLACKSTONE, Blocks.CRYING_OBSIDIAN, 2, false,
                    Blocks.POLISHED_BLACKSTONE, Blocks.BASALT, Blocks.MAGMA_BLOCK),
            new Vegetation(7, false, 7, Blocks.OBSIDIAN, null,
                    6, Blocks.BLACKSTONE_SLAB, Blocks.SOUL_LANTERN),
            new SpawnArea(3, null, null)), PrivateSpaceWorldTraits.OBSIDIAN_FORGE),
    AURORA_GLACIER("aurora_glacier", new Profile(Prototype.GLACIER, LandmarkType.ICE_SPIKE, Biomes.ICE_SPIKES,
            76.0, 1.2, 79.0, 60.0, 9.0, 20.0, 2.6,
            new Surface(Blocks.PACKED_ICE, Blocks.BLUE_ICE, 3, true,
                    Blocks.SNOW_BLOCK, Blocks.DEEPSLATE, Blocks.PEARLESCENT_FROGLIGHT),
            new Vegetation(7, false, 8, Blocks.PACKED_ICE, null,
                    0, null, null),
            new SpawnArea(3, null, null)), PrivateSpaceWorldTraits.AURORA_GLACIER);

    /** Terrain families. Each one changes how a column decides its own solid span. */
    public enum Prototype {
        /** Constant {@code surfaceY}; no noise is sampled at all. */
        FLAT,
        /** Not one island: a central landmass plus separate islands hanging in the void. */
        SKY,
        /** Rolling noise lifted onto a taller base, giving peaks near the rim. */
        HIGHLAND,
        /** Sharp crystalline mountain ridges and valleys. */
        PEAKS,
        /** Stepped terraced sanctum rings. */
        SANCTUM,
        /** Bounded rolling noise around {@code baseHeight}. */
        ROLLING,
        /** Raised outer forge ring around a sunken volcanic basin. */
        CALDERA,
        /** Sweeping frozen ridges topped with snow. */
        GLACIER
    }

    /** Procedural landmark archetype placed per landscape during chunk decoration. */
    public enum LandmarkType {
        STARLIT_FLAT,
        SKY_ARCHIPELAGO,
        MYSTIC_CHERRY,
        AMETHYST_SPIRE,
        SCULK_OBELISK,
        GIANT_MUSHROOM,
        OBSIDIAN_PILLAR,
        ICE_SPIKE
    }

    /** Which multi-layered geological strata a column is built from. */
    public record Surface(Block top, Block filler, int fillerDepth, boolean snowLayer,
                          Block midStratum, Block deepStratum, Block undersideAccent) {}

    /**
     * Landmarks and ground cover scattered per chunk.
     *
     * @param treeMask        bit mask applied to the chunk hash
     * @param treeWhenBitSet  {@code true} plants a landmark when the masked bits are non-zero
     * @param trunkHeight     base pillar/trunk height
     * @param log             primary structure block
     * @param leaves          canopy/cap block, or {@code null} for spires and obelisks
     */
    public record Vegetation(int treeMask, boolean treeWhenBitSet, int trunkHeight, Block log, Block leaves,
                             int plantCount, Block plantPrimary, Block plantSecondary) {
        /** No vegetation at all; used by the deliberately bare superflat preset. */
        public static final Vegetation NONE = new Vegetation(0, false, 0, null, null, 0, null, null);
    }

    /** How the landing platform is carved, and what is planted beside it. */
    public record SpawnArea(int platformRadius, Block treeLog, Block treeLeaves) {}

    /** Everything terrain generation needs to know about one landscape. */
    public record Profile(Prototype prototype, LandmarkType landmark, ResourceKey<Biome> biome,
                          double baseHeight, double heightScale, double centerHeight,
                          double broadNoiseScale, double broadNoiseAmplitude,
                          double fineNoiseScale, double fineNoiseAmplitude,
                          Surface surface, Vegetation vegetation, SpawnArea spawnArea) {}

    private final String id;
    private final Profile profile;
    private final PrivateSpaceWorldTraits traits;

    PrivateSpaceEnvironment(String id, Profile profile, PrivateSpaceWorldTraits traits) {
        this.id = id;
        this.profile = profile;
        this.traits = traits;
    }

    public String id() {
        return id;
    }

    public Profile profile() {
        return profile;
    }

    public PrivateSpaceWorldTraits traits() {
        return traits;
    }

    public Component displayName() {
        return Component.translatable("space_environment.universal_tool." + id);
    }

    public static PrivateSpaceEnvironment byId(String id) {
        if (id == null) {
            return null;
        }
        for (PrivateSpaceEnvironment environment : values()) {
            if (environment.id.equals(id)) {
                return environment;
            }
        }
        return switch (id) {
            case "plains" -> AMETHYST_STARFALL;
            case "desert" -> OBSIDIAN_FORGE;
            case "snowy_plains" -> AURORA_GLACIER;
            case "birch_forest" -> MUSHROOM_WONDERLAND;
            case "savanna" -> SCULK_SANCTUM;
            default -> null;
        };
    }
}
