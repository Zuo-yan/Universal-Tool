package org.gwfx.universaltool.phoenix;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.gwfx.universaltool.block.AbstractDoubleEntityBlock;
import org.gwfx.universaltool.init.ModBlockEntities;

import javax.annotation.Nullable;

public class PhoenixPodBlock extends AbstractDoubleEntityBlock {

    public static final MapCodec<PhoenixPodBlock> CODEC = simpleCodec(PhoenixPodBlock::new);
    public static final EnumProperty<PodState> STATE = EnumProperty.create("state", PodState.class);

    protected static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);

    public PhoenixPodBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(STATE, PodState.EMPTY));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onLowerRemoved(Level level, BlockPos lowerPos, @Nullable BlockEntity be) {
        if (!level.isClientSide && level.getServer() != null) {
            PhoenixNetworkSavedData.get(level.getServer()).unregisterPod(level.dimension(), lowerPos);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return new PhoenixPodBlockEntity(pos, state);
        }
        return null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return createTickerHelper(blockEntityType, ModBlockEntities.PHOENIX_POD_BE.get(), PhoenixPodBlockEntity::tick);
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockPos lowerPos = getLowerPos(state, pos);
            BlockEntity be = level.getBlockEntity(lowerPos);
            if (be instanceof PhoenixPodBlockEntity podBe) {
                serverPlayer.openMenu(podBe, lowerPos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STATE);
    }
}
