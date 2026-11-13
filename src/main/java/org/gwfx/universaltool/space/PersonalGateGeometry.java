package org.gwfx.universaltool.space;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * 个人空间星门碰撞与轮廓：两格高尖拱石门；未开启时门框实心阻挡，开启后中空通道开放走入传送。
 */
final class PersonalGateGeometry {
    private static final double[][] LOWER_SOLID = {
            {0.0D, 0.0D, 3.0D, 16.0D, 4.0D, 13.0D},   // 底座基台
            {0.0D, 4.0D, 5.0D, 3.0D, 16.0D, 11.0D},   // 左立柱
            {13.0D, 4.0D, 5.0D, 16.0D, 16.0D, 11.0D}, // 右立柱
            {3.0D, 4.0D, 6.5D, 13.0D, 16.0D, 9.5D}    // 未开启时阻挡门洞
    };
    private static final double[][] LOWER_OPEN_COLLISION = {
            {0.0D, 0.0D, 3.0D, 16.0D, 2.5D, 13.0D},   // 仅保留低矮门槛供走入
            {0.0D, 2.5D, 5.0D, 2.0D, 16.0D, 11.0D},   // 左立柱外侧
            {14.0D, 2.5D, 5.0D, 16.0D, 16.0D, 11.0D}  // 右立柱外侧
    };

    private static final double[][] UPPER_SOLID = {
            {0.0D, 0.0D, 5.0D, 3.0D, 10.0D, 11.0D},   // 左上拱肩
            {13.0D, 0.0D, 5.0D, 16.0D, 10.0D, 11.0D}, // 右上拱肩
            {2.0D, 10.0D, 5.0D, 14.0D, 16.0D, 11.0D}, // 尖拱顶冠
            {3.0D, 0.0D, 6.5D, 13.0D, 10.0D, 9.5D}    // 未开启时阻挡门洞上半
    };
    private static final double[][] UPPER_OPEN_COLLISION = {
            {0.0D, 0.0D, 5.0D, 2.0D, 10.0D, 11.0D},
            {14.0D, 0.0D, 5.0D, 16.0D, 10.0D, 11.0D},
            {2.0D, 11.0D, 5.0D, 14.0D, 16.0D, 11.0D}
    };

    private static final Map<Direction, VoxelShape> LOWER_SHAPES = buildDirectionMap(LOWER_SOLID);
    private static final Map<Direction, VoxelShape> LOWER_OPEN_SHAPES = buildDirectionMap(LOWER_OPEN_COLLISION);
    private static final Map<Direction, VoxelShape> UPPER_SHAPES = buildDirectionMap(UPPER_SOLID);
    private static final Map<Direction, VoxelShape> UPPER_OPEN_SHAPES = buildDirectionMap(UPPER_OPEN_COLLISION);

    private PersonalGateGeometry() {
    }

    static VoxelShape shape(Direction facing) {
        return shape(facing, DoubleBlockHalf.LOWER);
    }

    static VoxelShape shape(Direction facing, DoubleBlockHalf half) {
        Map<Direction, VoxelShape> map = half == DoubleBlockHalf.UPPER ? UPPER_SHAPES : LOWER_SHAPES;
        return map.getOrDefault(facing, map.get(Direction.NORTH));
    }

    static VoxelShape openCollisionShape(Direction facing, DoubleBlockHalf half) {
        Map<Direction, VoxelShape> map = half == DoubleBlockHalf.UPPER ? UPPER_OPEN_SHAPES : LOWER_OPEN_SHAPES;
        return map.getOrDefault(facing, map.get(Direction.NORTH));
    }

    private static Map<Direction, VoxelShape> buildDirectionMap(double[][] boxes) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            VoxelShape combined = Shapes.empty();
            for (double[] box : boxes) {
                combined = Shapes.joinUnoptimized(combined, rotateBox(dir, box[0], box[1], box[2], box[3], box[4], box[5]), BooleanOp.OR);
            }
            map.put(dir, combined.optimize());
        }
        return map;
    }

    private static VoxelShape rotateBox(Direction dir, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return switch (dir) {
            case SOUTH -> Block.box(16.0D - maxX, minY, 16.0D - maxZ, 16.0D - minX, maxY, 16.0D - minZ);
            case WEST -> Block.box(minZ, minY, 16.0D - maxX, maxZ, maxY, 16.0D - minX);
            case EAST -> Block.box(16.0D - maxZ, minY, minX, 16.0D - minZ, maxY, maxX);
            default -> Block.box(minX, minY, minZ, maxX, maxY, maxZ);
        };
    }
}
