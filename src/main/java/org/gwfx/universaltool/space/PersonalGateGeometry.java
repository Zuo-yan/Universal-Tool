package org.gwfx.universaltool.space;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
public final class PersonalGateGeometry {
    private static final double[][] BOXES = {
            new double[] {1, 0, 3, 15, 2, 13},
            new double[] {0, 2, 2, 16, 3, 14},
            new double[] {1, 3, 4, 4, 5, 12},
            new double[] {12, 3, 4, 15, 5, 12},
            new double[] {2, 5, 5, 4, 13, 11},
            new double[] {12, 5, 5, 14, 13, 11},
            new double[] {3, 13, 5, 6, 15, 11},
            new double[] {10, 13, 5, 13, 15, 11},
            new double[] {5, 14, 5, 11, 16, 11},
            new double[] {7, 13, 3, 9, 16, 5},
            new double[] {4, 5, 5, 5, 13, 6},
            new double[] {11, 5, 5, 12, 13, 6},
            new double[] {5, 13, 5, 11, 14, 6},
            new double[] {4, 3, 5, 12, 4, 11},
            new double[] {5, 4, 7.5, 11, 13, 8.5},
            new double[] {1, 8, 3, 3, 10, 5},
            new double[] {13, 8, 3, 15, 10, 5}
    };
    private static final VoxelShape[] SHAPES = new VoxelShape[4];
    static {
        for (int rotation=0;rotation<4;rotation++) {
            VoxelShape shape=Shapes.empty();
            for (double[] box : BOXES) {
                double x0=box[0], z0=box[2], x1=box[3], z1=box[5];
                for(int i=0;i<rotation;i++) { double a=x0,b=x1; x0=16-z1;x1=16-z0;z0=a;z1=b; }
                shape=Shapes.or(shape,Block.box(x0,box[1],z0,x1,box[4],z1));
            }
            SHAPES[rotation]=shape.optimize();
        }
    }
    public static VoxelShape shape(Direction facing) {
        return SHAPES[switch(facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; }];
    }
}
