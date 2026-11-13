package org.gwfx.universaltool.space;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.gwfx.universaltool.init.ModBlockEntities;
import org.gwfx.universaltool.init.ModBlocks;

/**
 * Anchor entity driving the animated cosmic starfield renderer on {@link StarryVoidBlock}.
 */
public class StarryVoidBlockEntity extends BlockEntity {
    public StarryVoidBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.STARRY_VOID_BE.get(), pos, blockState);
    }

    /** Culls shared faces between adjacent starry blocks so only room-facing surfaces emit quads. */
    public boolean shouldRenderFace(Direction face) {
        if (level == null) {
            return true;
        }
        return !level.getBlockState(worldPosition.relative(face)).is(ModBlocks.STARRY_VOID_BLOCK.get());
    }
}
