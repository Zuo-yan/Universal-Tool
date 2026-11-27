package org.gwfx.universaltool.waypoint;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/** Resolves a standing player's feet position near a stored waypoint. */
final class WaypointLanding {
    private WaypointLanding() {}

    @Nullable
    static Vec3 find(ServerLevel level, Vec3 target) {
        if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)) return null;
        if (level.isOutsideBuildHeight(Mth.floor(target.y)) || !level.getWorldBorder().isWithinBounds(target)) return null;

        // Load the bounded destination area before testing collision. The check is also used at arrival.
        BlockPos center = BlockPos.containing(target);
        level.getChunkAt(center);
        if (isSafe(level, target)) return target;

        Set<Long> loaded = new HashSet<>();
        loaded.add(ChunkPos.asLong(center));
        BlockPos.MutableBlockPos candidateBlock = new BlockPos.MutableBlockPos();
        Vec3 best = null;
        double closest = Double.POSITIVE_INFINITY;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    candidateBlock.setWithOffset(center, dx, dy, dz);
                    Vec3 candidate = Vec3.atBottomCenterOf(candidateBlock);
                    double distance = target.distanceToSqr(candidate);
                    if (distance >= closest || !level.getWorldBorder().isWithinBounds(candidate)) continue;
                    if (loaded.add(ChunkPos.asLong(candidateBlock))) level.getChunkAt(candidateBlock);
                    if (isSafe(level, candidate)) {
                        best = candidate;
                        closest = distance;
                    }
                }
            }
        }
        return best;
    }

    static boolean isSafe(ServerLevel level, Vec3 feet) {
        AABB body = EntityType.PLAYER.getDimensions().makeBoundingBox(feet);
        if (body.minY <= level.getMinBuildHeight() || body.maxY > level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(body)) return false;
        BlockPos support = BlockPos.containing(feet.x, feet.y - 0.05D, feet.z);
        BlockState base = level.getBlockState(support);
        if (isHazard(base) || !base.getFluidState().isEmpty()
                || !level.getBlockCollisions(null, body.move(0, -0.05D, 0)).iterator().hasNext()
                || level.getBlockCollisions(null, body).iterator().hasNext()) return false;

        BlockPos min = BlockPos.containing(body.minX, body.minY, body.minZ);
        BlockPos max = BlockPos.containing(body.maxX - 1.0E-7D, body.maxY - 1.0E-7D, body.maxZ - 1.0E-7D);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (isHazard(state) || !state.getFluidState().isEmpty()) return false;
        }
        // Contact with the supporting surface may span multiple blocks.
        BlockPos lower = min.atY(support.getY());
        BlockPos upper = max.atY(support.getY());
        for (BlockPos pos : BlockPos.betweenClosed(lower, upper)) {
            if (isHazard(level.getBlockState(pos))) return false;
        }
        return true;
    }

    private static boolean isHazard(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.POINTED_DRIPSTONE)
                || CampfireBlock.isLitCampfire(state);
    }
}
