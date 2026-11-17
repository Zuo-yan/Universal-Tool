package org.gwfx.universaltool.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment.LandmarkType;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment.Profile;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment.Prototype;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment.Surface;
import org.gwfx.universaltool.space.PrivateSpaceEnvironment.Vegetation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Stateless generation: only writes its own chunk, never edits already saved chunks. */
public final class FiniteIslandChunkGenerator extends ChunkGenerator {
    public static final String VERSION = "finite_island_v1";

    /** Satellites orbiting the central landmass in {@link Prototype#SKY}. */
    private static final int SKY_SATELLITES = 8;
    private static final int SKY_SEED_STEP = 0x9E3779B9;
    private static final double SPAWN_PROTECTION_RADIUS = 18.0;
    private static final double SAMPLE_POINT_X = 40.0;
    private static final double SAMPLE_POINT_Z = -40.0;
    private static final double SAMPLE_PROTECTION_RADIUS = 8.0;

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

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return 63;
    }

    public PrivateSpaceEnvironment environment() {
        return environment;
    }

    public boolean containsTerrain(int x, int z) {
        return fieldFraction(x, z) > 0;
    }

    /** Solidity of a column: the island mask for ordinary landscapes, the sky field for {@link Prototype#SKY}. */
    private double fieldFraction(int x, int z) {
        if (profile.prototype() != Prototype.SKY) {
            return edgeFraction(x, z);
        }
        return skyColumn(x, z).fraction();
    }

    private double edgeFraction(int x, int z) {
        int half = size / 2;
        if (x < -half || x >= half || z < -half || z >= half) {
            return -1;
        }
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
                    62.0 + altitudeRoll * 30.0, 10.0 + thicknessRoll * 12.0));
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
        if (x < -half || x >= half || z < -half || z >= half) {
            return SkyColumn.VOID;
        }
        double px = x + 0.5;
        double pz = z + 0.5;
        double bestFraction = -1.0;
        double bestBaseY = 0.0;
        double bestThickness = 0.0;
        for (SkyIsland island : skyIslands) {
            double fraction = 1 - Math.hypot(px - island.x(), pz - island.z()) / island.radius();
            if (fraction > bestFraction) {
                bestFraction = fraction;
                bestBaseY = island.baseY();
                bestThickness = island.thickness();
            }
        }
        if (bestFraction <= 0) {
            return SkyColumn.VOID;
        }
        return new SkyColumn(bestFraction, bestBaseY, bestThickness);
    }

    public int surfaceY(int x, int z) {
        double edge = fieldFraction(x, z);
        if (edge <= 0) {
            return getMinY() - 1;
        }
        if (profile.prototype() == Prototype.FLAT) {
            return (int) profile.baseHeight();
        }
        double variation = variation(x, z);
        if (profile.prototype() == Prototype.SKY) {
            double skyHeight = skyColumn(x, z).baseY() + variation * profile.heightScale();
            return (int) Math.floor(skyHeight - 9 * (1 - Math.min(1, edge * 8)));
        }
        double centerBlend = Math.min(1, Math.max(0, (Math.hypot(x, z) - 18) / 30));
        double height = profile.baseHeight() + variation * profile.heightScale();
        height = profile.centerHeight() * (1 - centerBlend) + height * centerBlend;
        return (int) Math.floor(height - 9 * (1 - Math.min(1, edge * 8)));
    }

    /** Landscape shaping before base height is applied; shared by every non-flat prototype. */
    private double variation(int x, int z) {
        double broadRaw = noise(x / profile.broadNoiseScale(), z / profile.broadNoiseScale());
        double fineRaw = noise(x / profile.fineNoiseScale(), z / profile.fineNoiseScale());
        return switch (profile.prototype()) {
            case PEAKS -> {
                double ridgeBroad = (1.0 - Math.abs(broadRaw)) * 2.0 - 1.0;
                double ridgeFine = (1.0 - Math.abs(fineRaw)) * 2.0 - 1.0;
                yield ridgeBroad * profile.broadNoiseAmplitude() + ridgeFine * profile.fineNoiseAmplitude();
            }
            case SANCTUM -> {
                double value = broadRaw * profile.broadNoiseAmplitude() + fineRaw * profile.fineNoiseAmplitude();
                yield Math.floor(value / 4.5) * 4.5;
            }
            case CALDERA -> {
                double radial = Math.hypot(x, z) / (size * 0.5);
                double ring = Math.exp(-Math.pow((radial - 0.56) / 0.20, 2.0)) * 9.0 - 2.0;
                yield ring + broadRaw * (profile.broadNoiseAmplitude() * 0.65)
                        + fineRaw * profile.fineNoiseAmplitude();
            }
            case GLACIER -> {
                double ridge = (1.0 - Math.abs(fineRaw)) * 2.0 - 1.0;
                yield broadRaw * (profile.broadNoiseAmplitude() * 0.85)
                        + ridge * (profile.fineNoiseAmplitude() * 1.45);
            }
            default -> broadRaw * profile.broadNoiseAmplitude() + fineRaw * profile.fineNoiseAmplitude();
        };
    }

    public int bottomY(int x, int z) {
        double edge = fieldFraction(x, z);
        if (edge <= 0) {
            return getMinY();
        }
        double stalactite = Math.pow(Math.max(0.0, 0.5 + 0.5 * noise(x / 10.5, z / 10.5)), 2.2);
        double rib = 0.5 + 0.5 * noise(x / 5.0, z / 5.0);
        if (profile.prototype() == Prototype.SKY) {
            double taper = Math.pow(Math.min(1.0, edge * 4.0), 0.75);
            int thickness = Math.max(4,
                    (int) (skyColumn(x, z).thickness() * taper * (0.65 + 0.55 * stalactite + 0.15 * rib)));
            return Math.max(getMinY() + 8, surfaceY(x, z) - thickness);
        }
        double taper = Math.pow(Math.min(1.0, edge), 0.65);
        int thickness = 4 + (int) ((62.0 + 36.0 * stalactite + 10.0 * rib) * taper);
        return Math.max(getMinY() + 8, surfaceY(x, z) - thickness);
    }

    private BlockState terrain(int x, int y, int z) {
        int surface = surfaceY(x, z);
        if (surface < getMinY() || y > surface) {
            return Blocks.AIR.defaultBlockState();
        }
        int bottom = bottomY(x, z);
        if (y < bottom) {
            return Blocks.AIR.defaultBlockState();
        }
        return columnBlock(x, y, z, surface, bottom);
    }

    private BlockState columnBlock(int x, int y, int z, int surface, int bottom) {
        Surface material = profile.surface();
        if (y == surface) {
            return material.top().defaultBlockState();
        }
        if (y >= surface - material.fillerDepth()) {
            return material.filler().defaultBlockState();
        }
        if (y == bottom && isUndersideAccent(x, z)) {
            return material.undersideAccent().defaultBlockState();
        }
        if (y >= surface - 22 && y >= bottom + 5) {
            return material.midStratum().defaultBlockState();
        }
        return material.deepStratum().defaultBlockState();
    }

    private boolean isUndersideAccent(int x, int z) {
        return (mix(seed ^ (x * 73856093L) ^ (z * 19349663L)) & 7) == 0;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
                                                          StructureManager structures, ChunkAccess chunk) {
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = startX + dx;
                int z = startZ + dz;
                if (!containsTerrain(x, z)) {
                    continue;
                }
                int surface = surfaceY(x, z);
                int bottom = bottomY(x, z);
                for (int y = bottom; y <= surface; y++) {
                    chunk.setBlockState(position.set(x, y, z), columnBlock(x, y, z, surface, bottom), false);
                }
                if (profile.surface().snowLayer()) {
                    chunk.setBlockState(position.set(x, surface + 1, z), Blocks.SNOW.defaultBlockState(), false);
                }
            }
        }
        populateOres(chunk, startX, startZ);
        decorate(chunk, startX, startZ);
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG,
                Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR,
                Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES));
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * Deterministically injects theme-specific ore veins and optional underground amethyst geodes
     * strictly inside this chunk's rock strata, preserving island silhouettes and top layers.
     */
    private void populateOres(ChunkAccess chunk, int startX, int startZ) {
        PrivateSpaceWorldTraits.OreProfile ores = environment.traits().ores();
        if (ores.rateMultiplier() <= 0.0 || ores.veins().isEmpty()) {
            return;
        }
        long chunkSeed = mix(seed ^ ((long) chunk.getPos().x * 0x9E3779B97F4A7C15L)
                ^ ((long) chunk.getPos().z * 0xC2B2AE3D27D4EB4FL));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        int ruleIndex = 0;
        for (PrivateSpaceWorldTraits.OreVeinRule rule : ores.veins()) {
            ruleIndex++;
            int attempts = Math.max(0, (int) Math.round(rule.baseAttemptsPerChunk() * ores.rateMultiplier()));
            for (int attempt = 0; attempt < attempts; attempt++) {
                long roll = mix(chunkSeed ^ ((long) ruleIndex << 20) ^ ((long) attempt * 0x517cc1b727220a95L));
                int vx = startX + (int) (roll & 15);
                int vz = startZ + (int) ((roll >>> 4) & 15);
                if (!containsTerrain(vx, vz)) {
                    continue;
                }
                int surface = surfaceY(vx, vz);
                int bottom = bottomY(vx, vz);
                int rockTop = surface - profile.surface().fillerDepth() - 2;
                int rockBottom = bottom + 2;
                if (rockTop <= rockBottom) {
                    continue;
                }
                int span = rockTop - rockBottom;
                int yHigh = rockTop - (int) Math.floor(span * rule.minDepthFraction());
                int yLow = rockTop - (int) Math.ceil(span * rule.maxDepthFraction());
                int minY = Math.max(rockBottom, Math.min(yLow, yHigh));
                int maxY = Math.min(rockTop, Math.max(yLow, yHigh));
                if (maxY < minY) {
                    continue;
                }
                int vy = minY + (maxY == minY ? 0 : Math.floorMod((int) (roll >>> 16), maxY - minY + 1));
                placeOreVein(chunk, startX, startZ, vx, vy, vz, rule, roll, cursor);
            }
        }

        if (ores.amethystGeodes() && (chunkSeed & 7L) == 0L) {
            placeUndergroundGeode(chunk, startX, startZ, chunkSeed, cursor);
        }
    }

    private void placeOreVein(ChunkAccess chunk, int startX, int startZ, int vx, int vy, int vz,
                              PrivateSpaceWorldTraits.OreVeinRule rule, long roll, BlockPos.MutableBlockPos cursor) {
        int cx = vx;
        int cy = vy;
        int cz = vz;
        long state = roll;
        for (int step = 0; step < rule.veinSize(); step++) {
            if (cx >= startX && cx < startX + 16 && cz >= startZ && cz < startZ + 16 && containsTerrain(cx, cz)) {
                int surface = surfaceY(cx, cz);
                int bottom = bottomY(cx, cz);
                if (isInteriorRock(chunk, cursor.set(cx, cy, cz), cx, cy, cz, surface, bottom)) {
                    Block ore = cy >= surface - 22 ? rule.midOre() : rule.deepOre();
                    chunk.setBlockState(cursor, ore.defaultBlockState(), false);
                }
            }
            state = mix(state ^ (step * 0x94d049bb133111ebL));
            int axis = (int) (state & 3L);
            int dir = ((state >>> 2) & 1L) == 0L ? 1 : -1;
            if (axis == 0) cx += dir;
            else if (axis == 1) cy += dir;
            else if (axis == 2) cz += dir;
            else {
                cx += ((state >>> 4) & 1L) == 0L ? 1 : -1;
                cz += ((state >>> 5) & 1L) == 0L ? 1 : -1;
            }
        }
    }

    private void placeUndergroundGeode(ChunkAccess chunk, int startX, int startZ, long chunkSeed,
                                       BlockPos.MutableBlockPos cursor) {
        int gx = startX + 8;
        int gz = startZ + 8;
        if (!containsTerrain(gx, gz) || Math.hypot(gx, gz) <= SPAWN_PROTECTION_RADIUS) {
            return;
        }
        int surface = surfaceY(gx, gz);
        int bottom = bottomY(gx, gz);
        int rockTop = surface - profile.surface().fillerDepth() - 6;
        int rockBottom = bottom + 5;
        if (rockTop - rockBottom < 10) {
            return;
        }
        int gy = rockBottom + Math.floorMod((int) (chunkSeed >>> 12), rockTop - rockBottom + 1);
        int radius = 3 + ((chunkSeed & 8L) != 0L ? 1 : 0);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = gx + dx;
                    int y = gy + dy;
                    int z = gz + dz;
                    if (x < startX || x >= startX + 16 || z < startZ || z >= startZ + 16 || !containsTerrain(x, z)) {
                        continue;
                    }
                    int colSurface = surfaceY(x, z);
                    int colBottom = bottomY(x, z);
                    if (!isInteriorRock(chunk, cursor.set(x, y, z), x, y, z, colSurface, colBottom)) {
                        continue;
                    }
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist <= radius - 1.5) {
                        BlockState below = chunk.getBlockState(cursor.below());
                        boolean placeCluster = (below.is(Blocks.AMETHYST_BLOCK) || below.is(Blocks.BUDDING_AMETHYST))
                                && ((mix(chunkSeed ^ (x * 31L) ^ (z * 97L)) & 3L) == 0L);
                        chunk.setBlockState(cursor, placeCluster
                                ? Blocks.AMETHYST_CLUSTER.defaultBlockState()
                                        .setValue(AmethystClusterBlock.FACING, Direction.UP)
                                : Blocks.CAVE_AIR.defaultBlockState(), false);
                    } else if (dist <= radius - 0.65) {
                        boolean budding = (mix(chunkSeed ^ (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L)) & 7L) == 0L;
                        chunk.setBlockState(cursor, (budding ? Blocks.BUDDING_AMETHYST : Blocks.AMETHYST_BLOCK)
                                .defaultBlockState(), false);
                    } else if (dist <= radius + 0.15) {
                        Block shell = ((dx + dy + dz) & 1) == 0 ? Blocks.CALCITE : Blocks.SMOOTH_BASALT;
                        chunk.setBlockState(cursor, shell.defaultBlockState(), false);
                    }
                }
            }
        }
    }

    private boolean isInteriorRock(ChunkAccess chunk, BlockPos pos, int x, int y, int z, int surface, int bottom) {
        if (y > surface - profile.surface().fillerDepth() - 2 || y < bottom + 2) {
            return false;
        }
        if (Math.hypot(x, z) <= 10.0 && y >= surface - 6) {
            return false;
        }
        Block existing = chunk.getBlockState(pos).getBlock();
        Surface material = profile.surface();
        return existing == material.midStratum() || existing == material.deepStratum();
    }

    private void decorate(ChunkAccess chunk, int startX, int startZ) {
        long hash = mix(seed ^ ((long) startX * 341873128712L) ^ ((long) startZ * 132897987541L));
        int centerX = startX + 8;
        int centerZ = startZ + 8;

        if (isLandmarkAnchorClear(centerX, centerZ)) {
            placeLandmark(chunk, centerX, centerZ, hash);
        }

        if (profile.landmark() == LandmarkType.SKY_ARCHIPELAGO) {
            placeSkyHangingVines(chunk, startX, startZ, hash);
        }

        Vegetation vegetation = profile.vegetation();
        if (vegetation.plantCount() <= 0 || vegetation.plantPrimary() == null || profile.surface().snowLayer()) {
            return;
        }
        for (int i = 0; i < vegetation.plantCount(); i++) {
            long spot = mix(hash + i * 31L + 17L);
            int px = startX + (int) (spot & 15);
            int pz = startZ + (int) ((spot >>> 8) & 15);
            if (!isSafeForDecoration(px, pz)) {
                continue;
            }
            BlockState plant = ((spot & 32) == 0 || vegetation.plantSecondary() == null
                    ? vegetation.plantPrimary()
                    : vegetation.plantSecondary()).defaultBlockState();
            int py = surfaceY(px, pz) + 1;
            if (chunk.getBlockState(new BlockPos(px, py, pz)).isAir()) {
                setDecorationBlock(chunk, px, py, pz, plant);
            }
        }
    }

    private boolean isLandmarkAnchorClear(int x, int z) {
        return isSafeForDecoration(x, z)
                && containsTerrain(x - 3, z - 3)
                && containsTerrain(x + 3, z + 3)
                && containsTerrain(x - 3, z + 3)
                && containsTerrain(x + 3, z - 3);
    }

    private boolean isSafeForDecoration(int x, int z) {
        return containsTerrain(x, z)
                && Math.hypot(x, z) > SPAWN_PROTECTION_RADIUS
                && Math.hypot(x - SAMPLE_POINT_X, z - SAMPLE_POINT_Z) >= SAMPLE_PROTECTION_RADIUS;
    }

    private void placeLandmark(ChunkAccess chunk, int x, int z, long hash) {
        int baseY = surfaceY(x, z) + 1;
        switch (profile.landmark()) {
            case STARLIT_FLAT -> {
                if ((hash & 63) == 0 && Math.hypot(x, z) > 48.0) {
                    setDecorationBlock(chunk, x, baseY, z, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    setDecorationBlock(chunk, x, baseY + 1, z, Blocks.SEA_LANTERN.defaultBlockState());
                    setDecorationBlock(chunk, x, baseY + 2, z, Blocks.END_ROD.defaultBlockState());
                }
            }
            case SKY_ARCHIPELAGO -> {
                if ((hash & 15) == 0) {
                    placeCanopyTree(chunk, x, baseY, z, 5 + (int) ((hash >>> 4) & 1),
                            Blocks.OAK_LOG.defaultBlockState(),
                            Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true),
                            true);
                }
            }
            case MYSTIC_CHERRY -> {
                if ((hash & 3) == 0) {
                    placeMysticCherryTree(chunk, x, baseY, z, hash);
                }
            }
            case AMETHYST_SPIRE -> {
                if ((hash & 7) == 0) {
                    placeAmethystSpire(chunk, x, baseY, z, hash);
                }
            }
            case SCULK_OBELISK -> {
                if ((hash & 7) == 0) {
                    placeSculkObelisk(chunk, x, baseY, z, hash);
                }
            }
            case GIANT_MUSHROOM -> {
                if ((hash & 7) == 0 || (hash & 7) == 4) {
                    placeGiantMushroom(chunk, x, baseY, z, hash);
                }
            }
            case OBSIDIAN_PILLAR -> {
                if ((hash & 7) == 0) {
                    placeObsidianPillar(chunk, x, baseY, z, hash);
                }
            }
            case ICE_SPIKE -> {
                if ((hash & 7) == 0 || (hash & 7) == 2) {
                    placeIceSpike(chunk, x, baseY, z, hash);
                }
            }
        }
    }

    private void placeCanopyTree(ChunkAccess chunk, int x, int y, int z, int trunkHeight,
                                 BlockState log, BlockState leaves, boolean hangingLantern) {
        for (int h = 0; h < trunkHeight; h++) {
            setDecorationBlock(chunk, x, y + h, z, log);
        }
        int canopyBase = Math.max(3, trunkHeight - 2);
        for (int h = canopyBase; h <= trunkHeight + 1; h++) {
            int radius = h == trunkHeight + 1 ? 2 : 3;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > radius + 1 || (dx == 0 && dz == 0 && h < trunkHeight)) {
                        continue;
                    }
                    setDecorationBlock(chunk, x + dx, y + h, z + dz, leaves);
                }
            }
        }
        if (hangingLantern) {
            setDecorationBlock(chunk, x + 2, y + canopyBase - 1, z,
                    Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
    }

    private void placeMysticCherryTree(ChunkAccess chunk, int x, int y, int z, long hash) {
        BlockState log = Blocks.CHERRY_LOG.defaultBlockState();
        BlockState leaves = Blocks.CHERRY_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int height = 6 + (int) ((hash >>> 3) & 3);
        int dirX = ((hash & 8) == 0) ? 1 : -1;
        int dirZ = ((hash & 16) == 0) ? 1 : -1;
        int curX = x;
        int curZ = z;
        for (int h = 0; h < height; h++) {
            setDecorationBlock(chunk, curX, y + h, curZ, log);
            if (h == 3) {
                curX += dirX;
                setDecorationBlock(chunk, curX, y + h, curZ, log);
            } else if (h == 5) {
                curZ += dirZ;
                setDecorationBlock(chunk, curX, y + h, curZ, log);
            }
        }
        for (int layer = height - 2; layer <= height + 2; layer++) {
            int radius = (layer == height + 2 || layer == height - 2) ? 2 : 3;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > radius + 1) {
                        continue;
                    }
                    setDecorationBlock(chunk, curX + dx, y + layer, curZ + dz, leaves);
                }
            }
        }
        setDecorationBlock(chunk, curX - dirX, y + height - 3, curZ,
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }

    private void placeAmethystSpire(ChunkAccess chunk, int x, int y, int z, long hash) {
        int height = 6 + (int) ((hash >>> 4) & 3);
        int stepX = ((hash & 8) == 0) ? 1 : -1;
        int stepZ = ((hash & 16) == 0) ? 0 : (((hash & 32) == 0) ? 1 : -1);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 1) {
                    setDecorationBlock(chunk, x + dx, y, z + dz, Blocks.CALCITE.defaultBlockState());
                }
            }
        }
        int curX = x;
        int curZ = z;
        for (int h = 0; h < height; h++) {
            if (h > 1 && h % 2 == 0) {
                curX += stepX;
                curZ += stepZ;
            }
            BlockState core = h == height - 1
                    ? Blocks.AMETHYST_CLUSTER.defaultBlockState()
                    : (h % 3 == 1 ? Blocks.BUDDING_AMETHYST.defaultBlockState() : Blocks.AMETHYST_BLOCK.defaultBlockState());
            setDecorationBlock(chunk, curX, y + h, curZ, core);
            if (h < height - 2) {
                setDecorationBlock(chunk, curX - stepX, y + h, curZ, Blocks.AMETHYST_BLOCK.defaultBlockState());
            }
        }
        setDecorationBlock(chunk, curX, y + height, curZ, Blocks.END_ROD.defaultBlockState());
    }

    private void placeSculkObelisk(ChunkAccess chunk, int x, int y, int z, long hash) {
        int height = 5 + (int) ((hash >>> 4) & 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockState base = (dx == 0 && dz == 0)
                        ? Blocks.SCULK_CATALYST.defaultBlockState()
                        : Blocks.POLISHED_DEEPSLATE.defaultBlockState();
                setDecorationBlock(chunk, x + dx, y, z + dz, base);
            }
        }
        for (int h = 1; h < height; h++) {
            BlockState shaft = h == height - 1
                    ? Blocks.SEA_LANTERN.defaultBlockState()
                    : (h % 2 == 0 ? Blocks.DEEPSLATE_TILES.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            setDecorationBlock(chunk, x, y + h, z, shaft);
        }
        setDecorationBlock(chunk, x, y + height, z, Blocks.SOUL_LANTERN.defaultBlockState());
    }

    private void placeGiantMushroom(ChunkAccess chunk, int x, int y, int z, long hash) {
        boolean red = (hash & 8) == 0;
        int stemHeight = 5 + (int) ((hash >>> 4) & 3);
        BlockState stem = Blocks.MUSHROOM_STEM.defaultBlockState();
        BlockState cap = (red ? Blocks.RED_MUSHROOM_BLOCK : Blocks.BROWN_MUSHROOM_BLOCK).defaultBlockState();
        BlockState glow = (red ? Blocks.SHROOMLIGHT : Blocks.VERDANT_FROGLIGHT).defaultBlockState();

        for (int h = 0; h < stemHeight; h++) {
            setDecorationBlock(chunk, x, y + h, z, stem);
        }
        int capRadius = red ? 2 : 3;
        int capY = y + stemHeight;
        for (int dx = -capRadius; dx <= capRadius; dx++) {
            for (int dz = -capRadius; dz <= capRadius; dz++) {
                int manhattan = Math.abs(dx) + Math.abs(dz);
                if (manhattan > capRadius + 1) {
                    continue;
                }
                if (red) {
                    int layerY = (Math.abs(dx) == capRadius || Math.abs(dz) == capRadius) ? capY - 1 : capY;
                    setDecorationBlock(chunk, x + dx, layerY, z + dz, cap);
                } else {
                    setDecorationBlock(chunk, x + dx, capY, z + dz, cap);
                }
            }
        }
        setDecorationBlock(chunk, x, capY, z, cap);
        setDecorationBlock(chunk, x + 1, capY - 1, z, glow);
        setDecorationBlock(chunk, x - 1, capY - 1, z, glow);
    }

    private void placeObsidianPillar(ChunkAccess chunk, int x, int y, int z, long hash) {
        int height = 6 + (int) ((hash >>> 4) & 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 1) {
                    setDecorationBlock(chunk, x + dx, y, z + dz, Blocks.GILDED_BLACKSTONE.defaultBlockState());
                }
            }
        }
        for (int h = 1; h < height; h++) {
            BlockState block;
            if (h == height - 1) {
                block = Blocks.MAGMA_BLOCK.defaultBlockState();
            } else if (h % 3 == 0) {
                block = Blocks.GILDED_BLACKSTONE.defaultBlockState();
            } else if (h % 2 == 0) {
                block = Blocks.CRYING_OBSIDIAN.defaultBlockState();
            } else {
                block = Blocks.OBSIDIAN.defaultBlockState();
            }
            setDecorationBlock(chunk, x, y + h, z, block);
        }
        setDecorationBlock(chunk, x, y + height, z, Blocks.LANTERN.defaultBlockState());
    }

    private void placeIceSpike(ChunkAccess chunk, int x, int y, int z, long hash) {
        int height = 7 + (int) ((hash >>> 4) & 5);
        int tiltX = ((hash & 8) == 0) ? 1 : -1;
        int curX = x;
        for (int h = 0; h < height; h++) {
            if (h > 2 && h % 3 == 0) {
                curX += tiltX;
            }
            BlockState ice = h == height / 2
                    ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState()
                    : (h >= height - 2 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
            setDecorationBlock(chunk, curX, y + h, z, ice);
            if (h < height / 2) {
                setDecorationBlock(chunk, curX - tiltX, y + h, z, Blocks.PACKED_ICE.defaultBlockState());
            }
        }
    }

    private void placeSkyHangingVines(ChunkAccess chunk, int startX, int startZ, long hash) {
        for (int i = 0; i < 4; i++) {
            long spot = mix(hash ^ (i * 0x517cc1b727220a95L));
            int vx = startX + (int) (spot & 15);
            int vz = startZ + (int) ((spot >>> 8) & 15);
            if (!isSafeForDecoration(vx, vz)) {
                continue;
            }
            int bottom = bottomY(vx, vz);
            int length = 2 + (int) ((spot >>> 16) & 3);
            for (int d = 1; d <= length; d++) {
                int vy = bottom - d;
                if (vy <= getMinY() + 2) {
                    break;
                }
                boolean tip = (d == length || vy == getMinY() + 3);
                BlockState state = (tip ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT)
                        .defaultBlockState()
                        .setValue(CaveVines.BERRIES, (d & 1) == 1);
                setDecorationBlock(chunk, vx, vy, vz, state);
            }
        }
    }

    private void setDecorationBlock(ChunkAccess chunk, int x, int y, int z, BlockState state) {
        if (y <= getMinY() + 1 || y >= getMinY() + getGenDepth() - 1) {
            return;
        }
        if (!isSafeForDecoration(x, z)) {
            return;
        }
        if ((x >> 4) != chunk.getPos().x || (z >> 4) != chunk.getPos().z) {
            return;
        }
        chunk.setBlockState(new BlockPos(x, y, z), state, false);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState random) {
        return Math.max(height.getMinBuildHeight(), surfaceY(x, z) + 1);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        BlockState[] states = new BlockState[height.getHeight()];
        for (int i = 0; i < states.length; i++) {
            states[i] = terrain(x, height.getMinBuildHeight() + i, z);
        }
        return new NoiseColumn(height.getMinBuildHeight(), states);
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {}

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {}

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {}

    @Override
    public void createStructures(RegistryAccess registries, ChunkGeneratorStructureState state,
                                 StructureManager structures, ChunkAccess chunk, StructureTemplateManager templates) {}

    @Override
    public void createReferences(WorldGenLevel level, StructureManager structures, ChunkAccess chunk) {}

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        PrivateSpaceWorldTraits.SpawnPolicy policy = environment.traits().spawns();
        if (policy.mode() == PrivateSpaceWorldTraits.SpawnMode.NO_NATURAL) {
            return;
        }
        List<MobSpawnSettings.SpawnerData> creatures = policy.spawnerDataFor(MobCategory.CREATURE);
        if (creatures.isEmpty()) {
            return;
        }
        ChunkPos chunkPos = region.getCenter();
        long chunkSeed = mix(seed ^ (chunkPos.x * 341873128712L) ^ (chunkPos.z * 132897987541L));
        RandomSource random = RandomSource.create(chunkSeed);
        if (random.nextFloat() >= 0.25F) {
            return;
        }
        PrivateSpaceWorldTraitHandler.trySpawnCreatureHerd(environment, region, creatures,
                chunkPos.getMinBlockX(), chunkPos.getMinBlockZ(), 0, MobSpawnType.CHUNK_GENERATION, random);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState random, BlockPos position) {
        lines.add("Private island: " + size + " x " + size + ", " + environment.id()
                + " (" + profile.prototype().name().toLowerCase(Locale.ROOT) + ")");
    }

    private double noise(double x, double z) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);
        double fx = x - ix;
        double fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = sample(ix, iz) * (1 - fx) + sample(ix + 1, iz) * fx;
        double b = sample(ix, iz + 1) * (1 - fx) + sample(ix + 1, iz + 1) * fx;
        return a * (1 - fz) + b * fz;
    }

    private double sample(int x, int z) {
        return ((mix(seed ^ x * 374761393L ^ z * 668265263L) >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
