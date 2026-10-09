package org.gwfx.universaltool.space;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Every home terrain a player can pick when creating a private space.
 *
 * <p>Each constant carries a {@link Profile}. Terrain code reads the profile instead of branching on
 * the constant, so adding a landscape is a data change rather than a code change.
 *
 * <p><b>Frozen:</b> the id strings are persisted in {@code PersonalSpaceSavedData} and inside the
 * chunk-generator codec. Never rename or remove one; only append.
 */
public enum PrivateSpaceEnvironment {
    SUPERFLAT("superflat", new Profile(Prototype.FLAT, Biomes.PLAINS,
            71.0, 0.0, 71.0, 55.0, 0.0, 19.0, 0.0,
            new Surface(Blocks.GRASS_BLOCK, Blocks.DIRT, 3, false),
            Vegetation.NONE,
            new SpawnArea(3, null, null))),
    PLAINS("plains", new Profile(Prototype.ROLLING, Biomes.PLAINS,
            75.0, 1.0, 79.0, 55.0, 12.0, 19.0, 3.0,
            new Surface(Blocks.GRASS_BLOCK, Blocks.DIRT, 3, false),
            new Vegetation(15, false, 5, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 10, Blocks.SHORT_GRASS, Blocks.DANDELION),
            new SpawnArea(3, null, null))),
    CHERRY_GROVE("cherry_grove", new Profile(Prototype.HIGHLAND, Biomes.CHERRY_GROVE,
            89.0, 1.6, 79.0, 55.0, 12.0, 19.0, 3.0,
            new Surface(Blocks.GRASS_BLOCK, Blocks.DIRT, 3, false),
            new Vegetation(3, true, 5, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES, 10, Blocks.SHORT_GRASS, Blocks.PINK_PETALS),
            new SpawnArea(8, Blocks.CHERRY_LOG, Blocks.CHERRY_LEAVES))),
    DESERT("desert", new Profile(Prototype.DUNES, Biomes.DESERT,
            74.0, 1.0, 79.0, 90.0, 10.0, 38.0, 2.0,
            new Surface(Blocks.SAND, Blocks.SANDSTONE, 3, false),
            new Vegetation(63, false, 3, Blocks.CACTUS, null, 8, Blocks.DEAD_BUSH, Blocks.DEAD_BUSH),
            new SpawnArea(3, null, null))),
    SNOWY_PLAINS("snowy_plains", new Profile(Prototype.TUNDRA, Biomes.SNOWY_PLAINS,
            73.0, 1.0, 79.0, 70.0, 5.0, 26.0, 1.5,
            new Surface(Blocks.GRASS_BLOCK, Blocks.DIRT, 3, true),
            new Vegetation(31, false, 5, Blocks.SPRUCE_LOG, Blocks.SPRUCE_LEAVES, 12, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS),
            new SpawnArea(3, null, null))),
    BIRCH_FOREST("birch_forest", new Profile(Prototype.ROLLING, Biomes.BIRCH_FOREST,
            76.0, 1.0, 79.0, 55.0, 10.0, 19.0, 2.5,
            new Surface(Blocks.GRASS_BLOCK, Blocks.DIRT, 3, false),
            new Vegetation(7, false, 6, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, 12, Blocks.POPPY, Blocks.SHORT_GRASS),
            new SpawnArea(3, null, null))),
    SAVANNA("savanna", new Profile(Prototype.PLATEAU, Biomes.SAVANNA,
            80.0, 1.0, 79.0, 65.0, 12.0, 24.0, 3.0,
            new Surface(Blocks.GRASS_BLOCK, Blocks.DIRT, 3, false),
            new Vegetation(15, false, 6, Blocks.ACACIA_LOG, Blocks.ACACIA_LEAVES, 10, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS),
            new SpawnArea(3, null, null))),
    SKY_ISLANDS("sky_islands", new Profile(Prototype.SKY, Biomes.PLAINS,
            78.0, 1.2, 78.0, 55.0, 12.0, 19.0, 3.0,
            new Surface(Blocks.GRASS_BLOCK, Blocks.DIRT, 3, false),
            new Vegetation(15, false, 5, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 10, Blocks.SHORT_GRASS, Blocks.SHORT_GRASS),
            new SpawnArea(3, null, null)));

    /** Terrain families. Each one changes how a column decides its own solid span. */
    public enum Prototype {
        /** Constant {@code surfaceY}; no noise is sampled at all. */
        FLAT,
        /** Bounded rolling noise around {@code baseHeight}. */
        ROLLING,
        /** Rolling noise lifted onto a taller base, giving peaks near the rim. */
        HIGHLAND,
        /** Long-wavelength, low-amplitude noise: broad dunes. */
        DUNES,
        /** Short-amplitude noise plus a snow cap: near-flat frozen ground. */
        TUNDRA,
        /** Like {@link #ROLLING}, but the noise is quantised into visible terraces. */
        PLATEAU,
        /** Not one island: a central landmass plus separate islands hanging in the void. */
        SKY
    }

    /** Which blocks a column is built from. */
    public record Surface(Block top, Block filler, int fillerDepth, boolean snowLayer) {}

    /**
     * Trees and ground cover scattered per chunk.
     *
     * @param treeMask        bit mask applied to the chunk hash
     * @param treeWhenBitSet  {@code true} plants a tree when the masked bits are non-zero
     * @param trunkHeight     trunk length; canopy rules are shared by every species
     * @param log             trunk block
     * @param leaves          canopy block, or {@code null} for a bare trunk (cactus)
     */
    public record Vegetation(int treeMask, boolean treeWhenBitSet, int trunkHeight, Block log, Block leaves,
                             int plantCount, Block plantPrimary, Block plantSecondary) {
        /** No vegetation at all; used by the deliberately bare superflat preset. */
        public static final Vegetation NONE = new Vegetation(0, false, 0, null, null, 0, null, null);
    }

    /** How the landing platform is carved, and what is planted beside it. */
    public record SpawnArea(int platformRadius, Block treeLog, Block treeLeaves) {}

    /** Everything terrain generation needs to know about one landscape. */
    public record Profile(Prototype prototype, ResourceKey<Biome> biome,
                          double baseHeight, double heightScale, double centerHeight,
                          double broadNoiseScale, double broadNoiseAmplitude,
                          double fineNoiseScale, double fineNoiseAmplitude,
                          Surface surface, Vegetation vegetation, SpawnArea spawnArea) {}

    private final String id;
    private final Profile profile;

    PrivateSpaceEnvironment(String id, Profile profile) {
        this.id = id;
        this.profile = profile;
    }

    public String id() {
        return id;
    }

    public Profile profile() {
        return profile;
    }

    public Component displayName() {
        return Component.translatable("space_environment.universal_tool." + id);
    }

    public static PrivateSpaceEnvironment byId(String id) {
        for (PrivateSpaceEnvironment environment : values()) {
            if (environment.id.equals(id)) {
                return environment;
            }
        }
        return null;
    }
}
