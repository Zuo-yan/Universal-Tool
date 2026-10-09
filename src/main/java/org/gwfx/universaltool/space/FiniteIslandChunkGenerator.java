package org.gwfx.universaltool.space;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment.Profile;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment.Prototype;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment.Surface;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment.Vegetation;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Stateless generation: only writes its own chunk, never edits already saved chunks. */
public final class FiniteIslandChunkGenerator extends ChunkGenerator {
    public static final String VERSION = "finite_island_v1";

    /** Satellites orbiting the central landmass in {@link Prototype#SKY}. */
    private static final int SKY_SATELLITES = 8;
    private static final int SKY_SEED_STEP = 0x9E3779B9;

    public static final MapCodec<FiniteIslandChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(FiniteIslandChunkGenerator::getBiomeSource),
            Codec.STRING.fieldOf("environment").forGetter(g -> g.environment.id()),
            Codec.LONG.fieldOf("seed").forGetter(g -> g.seed),
            Codec.intRange(64, 4096).fieldOf("size").forGetter(g -> g.size)
    ).apply(instance, (biomes, environment, seed, size) -> new FiniteIslandChunkGenerator(biomes,
            Objects.requireNonNull(PrivateSpaceEnvironment.byId(environment), "Unknown environment"), seed, size)));
    private final PrivateSpaceEnvironment environment;
    private final Profile profile;
    private final long seed;
    private final int size;
    /** Fixed for the lifetime of this generator: derived only from the seed and size. */
    private final List<SkyIsland> skyIslands;

    /** One floating landmass: a disc in the XZ plane with its own altitude and thickness. */
    private record SkyIsland(double x, double z, double radius, double baseY, double thickness) {}

    /** The winning landmass for a column, or {@link #VOID}. */
    private record SkyColumn(double fraction, double baseY, double thickness) {
        static final SkyColumn VOID = new SkyColumn(-1.0, 0.0, 0.0);
    }

    public FiniteIslandChunkGenerator(BiomeSource biomes, PrivateSpaceEnvironment environment, long seed, int size) {
        super(biomes);
        this.environment = environment;
        this.profile = environment.profile();
        this.seed = seed;
        this.size = size;
        this.skyIslands = profile.prototype() == Prototype.SKY ? buildSkyIslands() : List.of();
    }

    @Override protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getMinY() { return -64; }
    @Override public int getGenDepth() { return 384; }
    @Override public int getSeaLevel() { return 63; }

    public PrivateSpaceEnvironment environment() { return environment; }

    public boolean containsTerrain(int x, int z) { return fieldFraction(x, z) > 0; }

    /** Solidity of a column: the island mask for ordinary landscapes, the sky field for {@link Prototype#SKY}. */
    private double fieldFraction(int x, int z) {
        if (profile.prototype() != Prototype.SKY) return edgeFraction(x, z);
        return skyColumn(x, z).fraction();
    }

    private double edgeFraction(int x, int z) {
        int half = size / 2;
        if (x < -half || x >= half || z < -half || z >= half) return -1;
        double angle = Math.atan2(z + 0.5, x + 0.5);
        double phase = (mix(seed) & 65535) / 65535.0 * Math.PI * 2;
        double radius = half * (0.89 + 0.06 * Math.sin(angle * 3 + phase)
                + 0.03 * Math.cos(angle * 5 - phase));
        return 1 - Math.hypot(x + 0.5, z + 0.5) / radius;
    }

    /**
     * Builds the central landmass plus its ring of satellites. Every disc is sized in fractions of
     * {@code half} so a small island stays proportionate, and the ring is kept inside the footprint:
     * the farthest centre is {@code 0.73 * half} from the origin while the largest radius is
     * {@code 0.11 * half}.
     */
    private List<SkyIsland> buildSkyIslands() {
        int half = size / 2;
        List<SkyIsland> islands = new ArrayList<>(SKY_SATELLITES + 1);
        // The central landmass carries the spawn platform and the protected return gate.
        islands.add(new SkyIsland(0.0, 0.0, half * 0.30, profile.baseHeight(), 34.0));
        for (int i = 0; i < SKY_SATELLITES; i++) {
            long hash = mix(seed ^ ((long) i * SKY_SEED_STEP));
            double angleJitter = unit(hash, 0);
            double distanceRoll = unit(hash, 1);
            double radiusRoll = unit(hash, 2);
            double altitudeRoll = unit(hash, 3);
            double thicknessRoll = unit(hash, 4);
            double angle = (Math.PI * 2) * i / SKY_SATELLITES + (angleJitter - 0.5) * 0.5;
            double distance = half * (0.55 + distanceRoll * 0.18);
            double radius = half * (0.0625 + radiusRoll * 0.046875);
            islands.add(new SkyIsland(Math.cos(angle) * distance, Math.sin(angle) * distance, radius,
                    62.0 + altitudeRoll * 30.0, 8.0 + thicknessRoll * 10.0));
        }
        return List.copyOf(islands);
    }

    /** The {@code index}-th uniform double in [0,1) derived from one hash. */
    private static double unit(long hash, int index) {
        return (mix(hash + index * SKY_SEED_STEP) >>> 11) * 0x1.0p-53;
    }

    /** The highest-fraction island covering this column, so overlapping discs merge into one mass. */
    private SkyColumn skyColumn(int x, int z) {
        int half = size / 2;
        if (x < -half || x >= half || z < -half || z >= half) return SkyColumn.VOID;
        double px = x + 0.5, pz = z + 0.5;
        double bestFraction = -1.0;
        double bestBaseY = 0.0, bestThickness = 0.0;
        for (SkyIsland island : skyIslands) {
            double fraction = 1 - Math.hypot(px - island.x(), pz - island.z()) / island.radius();
            if (fraction > bestFraction) {
                bestFraction = fraction;
                bestBaseY = island.baseY();
                bestThickness = island.thickness();
            }
        }
        if (bestFraction <= 0) return SkyColumn.VOID;
        return new SkyColumn(bestFraction, bestBaseY, bestThickness);
    }

    public int surfaceY(int x, int z) {
        double edge = fieldFraction(x, z);
        if (edge <= 0) return getMinY() - 1;
        if (profile.prototype() == Prototype.FLAT) return (int) profile.baseHeight();
        double variation = variation(x, z);
        if (profile.prototype() == Prototype.SKY) {
            // Each landmass keeps its own altitude; there is no shared centre to blend towards.
            double skyHeight = skyColumn(x, z).baseY() + variation * profile.heightScale();
            return (int) Math.floor(skyHeight - 9 * (1 - Math.min(1, edge * 8)));
        }
        double centerBlend = Math.min(1, Math.max(0, (Math.hypot(x, z) - 18) / 30));
        double height = profile.baseHeight() + variation * profile.heightScale();
        height = profile.centerHeight() * (1 - centerBlend) + height * centerBlend;
        return (int) Math.floor(height - 9 * (1 - Math.min(1, edge * 8)));
    }

    /** Landscape noise before the base height is applied; shared by every prototype except {@link Prototype#FLAT}. */
    private double variation(int x, int z) {
        double broad = noise(x / profile.broadNoiseScale(), z / profile.broadNoiseScale()) * profile.broadNoiseAmplitude();
        double fine = noise(x / profile.fineNoiseScale(), z / profile.fineNoiseScale()) * profile.fineNoiseAmplitude();
        double value = broad + fine;
        if (profile.prototype() == Prototype.PLATEAU) value = Math.floor(value / 6.0) * 6.0;
        return value;
    }

    public int bottomY(int x, int z) {
        double edge = fieldFraction(x, z);
        if (edge <= 0) return getMinY();
        if (profile.prototype() == Prototype.SKY) {
            int thickness = (int) (skyColumn(x, z).thickness() * Math.min(1, Math.max(0, edge * 4)));
            return Math.max(getMinY() + 8, surfaceY(x, z) - thickness);
        }
        int thickness = 4 + (int) (84 * Math.pow(Math.min(1, edge), 0.65));
        return Math.max(getMinY() + 8, surfaceY(x, z) - thickness);
    }

    private BlockState terrain(int x, int y, int z) {
        int surface = surfaceY(x, z);
        if (surface < getMinY() || y > surface || y < bottomY(x, z)) return Blocks.AIR.defaultBlockState();
        return columnBlock(surface, y);
    }

    private BlockState columnBlock(int surface, int y) {
        Surface material = profile.surface();
        if (y == surface) return material.top().defaultBlockState();
        return y >= surface - material.fillerDepth() ? material.filler().defaultBlockState()
                : Blocks.STONE.defaultBlockState();
    }

    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
                                                                  StructureManager structures, ChunkAccess chunk) {
        int startX = chunk.getPos().getMinBlockX(), startZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = startX + dx, z = startZ + dz;
            if (!containsTerrain(x, z)) continue;
            int surface = surfaceY(x, z), bottom = bottomY(x, z);
            for (int y = bottom; y <= surface; y++) {
                chunk.setBlockState(position.set(x, y, z), columnBlock(surface, y), false);
            }
            if (profile.surface().snowLayer()) {
                chunk.setBlockState(position.set(x, surface + 1, z), Blocks.SNOW.defaultBlockState(), false);
            }
        }
        decorate(chunk, startX, startZ);
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,
                Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR,
                Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES));
        return CompletableFuture.completedFuture(chunk);
    }

    private void decorate(ChunkAccess chunk, int startX, int startZ) {
        Vegetation vegetation = profile.vegetation();
        if (Vegetation.NONE.equals(vegetation)) return;
        long hash = mix(seed ^ ((long) startX * 341873128712L) ^ ((long) startZ * 132897987541L));
        int x = startX + 8, z = startZ + 8;
        boolean tree = vegetation.log() != null && (vegetation.treeWhenBitSet()
                ? (hash & vegetation.treeMask()) != 0 : (hash & vegetation.treeMask()) == 0);
        if (tree && Math.hypot(x, z) > 16 && containsTerrain(x - 3, z - 3) && containsTerrain(x + 3, z + 3)
                && containsTerrain(x - 3, z + 3) && containsTerrain(x + 3, z - 3)) {
            int y = surfaceY(x, z) + 1;
            BlockState log = vegetation.log().defaultBlockState();
            for (int h = 0; h < vegetation.trunkHeight(); h++) setVegetation(chunk, x, y + h, z, log);
            if (vegetation.leaves() != null) {
                BlockState leaves = vegetation.leaves().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
                for (int h = 3; h <= 6; h++) {
                    int radius = h == 6 ? 2 : 3;
                    for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) > radius + 1 || (dx == 0 && dz == 0 && h < 5)) continue;
                        setVegetation(chunk, x + dx, y + h, z + dz, leaves);
                    }
                }
            }
        }
        for (int i = 0; i < vegetation.plantCount(); i++) {
            long spot = mix(hash + i);
            int px = startX + (int) (spot & 15), pz = startZ + (int) ((spot >>> 8) & 15);
            if (!containsTerrain(px, pz) || Math.hypot(px, pz) < 12) continue;
            BlockState plant = ((spot & 32) == 0 ? vegetation.plantPrimary() : vegetation.plantSecondary())
                    .defaultBlockState();
            int py = surfaceY(px, pz) + 1;
            if (chunk.getBlockState(new BlockPos(px, py, pz)).isAir()) setVegetation(chunk, px, py, pz, plant);
        }
    }

    private void setVegetation(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        if (containsTerrain(x, z) && (x >> 4) == chunk.getPos().x && (z >> 4) == chunk.getPos().z)
            chunk.setBlockState(new BlockPos(x, y, z), state, false);
    }

    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState random) {
        return Math.max(height.getMinBuildHeight(), surfaceY(x, z) + 1);
    }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        BlockState[] states = new BlockState[height.getHeight()];
        for (int i = 0; i < states.length; i++) states[i] = terrain(x, height.getMinBuildHeight() + i, z);
        return new NoiseColumn(height.getMinBuildHeight(), states);
    }
    @Override public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
                                       StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {}
    @Override public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {}
    @Override public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {}
    @Override public void createStructures(RegistryAccess registries, ChunkGeneratorStructureState state,
                                           StructureManager structures, ChunkAccess chunk, StructureTemplateManager templates) {}
    @Override public void createReferences(WorldGenLevel level, StructureManager structures, ChunkAccess chunk) {}
    @Override public void spawnOriginalMobs(WorldGenRegion region) {}
    @Override public void addDebugScreenInfo(List<String> lines, RandomState random, BlockPos position) {
        lines.add("Private island: " + size + " x " + size + ", " + environment.id()
                + " (" + profile.prototype().name().toLowerCase(Locale.ROOT) + ")");
    }

    private double noise(double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
        double a = sample(ix, iz) * (1 - fx) + sample(ix + 1, iz) * fx;
        double b = sample(ix, iz + 1) * (1 - fx) + sample(ix + 1, iz + 1) * fx;
        return a * (1 - fz) + b * fz;
    }
    private double sample(int x, int z) { return ((mix(seed ^ x * 374761393L ^ z * 668265263L) >>> 11) * 0x1.0p-53) * 2 - 1; }
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
