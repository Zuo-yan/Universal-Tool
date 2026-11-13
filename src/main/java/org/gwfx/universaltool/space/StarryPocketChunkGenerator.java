package org.gwfx.universaltool.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.gwfx.universaltool.init.ModBlocks;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Generates an enclosed 25x25x25 cosmic sanctuary bordered by {@link StarryVoidBlock} shells.
 * Interior coordinates span {@code x in [-12, 12]}, {@code z in [-12, 12]}, {@code y in [64, 88]}.
 */
public final class StarryPocketChunkGenerator extends ChunkGenerator {
    public static final String VERSION = "starry_pocket_v1";
    public static final int INNER_HALF = 12; // 25 blocks across: -12..+12
    public static final int SHELL_HALF = INNER_HALF + 1;
    public static final int FLOOR_Y = 63;
    public static final int INTERIOR_MIN_Y = FLOOR_Y + 1;
    public static final int CEILING_Y = INTERIOR_MIN_Y + 25; // 25 blocks of interior headroom: 64..88

    public static final MapCodec<StarryPocketChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(StarryPocketChunkGenerator::getBiomeSource),
            Codec.INT.optionalFieldOf("inner_half", INNER_HALF).forGetter(g -> INNER_HALF)
    ).apply(instance, (biomes, unused) -> new StarryPocketChunkGenerator(biomes)));

    public StarryPocketChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
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

    private static boolean isFloorBlock(int x, int y, int z) {
        return y == FLOOR_Y && x >= -INNER_HALF && x <= INNER_HALF && z >= -INNER_HALF && z <= INNER_HALF;
    }

    private static boolean isBoundaryBarrier(int x, int y, int z) {
        if (x < -SHELL_HALF || x > SHELL_HALF || z < -SHELL_HALF || z > SHELL_HALF
                || y < FLOOR_Y || y > CEILING_Y) {
            return false;
        }
        return x == -SHELL_HALF || x == SHELL_HALF
                || z == -SHELL_HALF || z == SHELL_HALF
                || y == CEILING_Y;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random,
                                                          StructureManager structures, ChunkAccess chunk) {
        int startX = chunk.getPos().getMinBlockX();
        int startZ = chunk.getPos().getMinBlockZ();
        if (startX > SHELL_HALF || startX + 15 < -SHELL_HALF || startZ > SHELL_HALF || startZ + 15 < -SHELL_HALF) {
            primeHeightmaps(chunk);
            return CompletableFuture.completedFuture(chunk);
        }

        BlockState floor = ModBlocks.STARRY_VOID_BLOCK.get().defaultBlockState();
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            int x = startX + dx;
            if (x < -SHELL_HALF || x > SHELL_HALF) continue;
            for (int dz = 0; dz < 16; dz++) {
                int z = startZ + dz;
                if (z < -SHELL_HALF || z > SHELL_HALF) continue;
                for (int y = FLOOR_Y; y <= CEILING_Y; y++) {
                    cursor.set(x, y, z);
                    if (isFloorBlock(x, y, z)) {
                        BlockPos pos = cursor.immutable();
                        chunk.setBlockState(pos, floor, false);
                        chunk.setBlockEntity(new StarryVoidBlockEntity(pos, floor));
                    } else if (isBoundaryBarrier(x, y, z)) {
                        chunk.setBlockState(cursor, barrier, false);
                    }
                }
            }
        }
        primeHeightmaps(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    private static void primeHeightmaps(ChunkAccess chunk) {
        Heightmap.primeHeightmaps(chunk, EnumSet.of(
                Heightmap.Types.WORLD_SURFACE_WG,
                Heightmap.Types.OCEAN_FLOOR_WG,
                Heightmap.Types.WORLD_SURFACE,
                Heightmap.Types.OCEAN_FLOOR,
                Heightmap.Types.MOTION_BLOCKING,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
        ));
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor height, RandomState random) {
        return (x >= -INNER_HALF && x <= INNER_HALF && z >= -INNER_HALF && z <= INNER_HALF)
                ? INTERIOR_MIN_Y
                : height.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor height, RandomState random) {
        BlockState[] states = new BlockState[height.getHeight()];
        BlockState floor = ModBlocks.STARRY_VOID_BLOCK.get().defaultBlockState();
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        for (int i = 0; i < states.length; i++) {
            int y = height.getMinBuildHeight() + i;
            if (isFloorBlock(x, y, z)) {
                states[i] = floor;
            } else if (isBoundaryBarrier(x, y, z)) {
                states[i] = barrier;
            } else {
                states[i] = Blocks.AIR.defaultBlockState();
            }
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
    public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState random, BlockPos position) {
        lines.add("Private Starry Sanctuary: 25x25x25");
    }
}
