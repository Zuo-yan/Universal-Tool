package org.gwfx.universaltool.lottery;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.gwfx.universaltool.init.ModBlockEntities;

import javax.annotation.Nullable;

public class UniversalLotteryMachineBlock extends BaseEntityBlock {

    public static final MapCodec<UniversalLotteryMachineBlock> CODEC = simpleCodec(UniversalLotteryMachineBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    protected static final VoxelShape SHAPE = Block.box(0.5, 0.0, 0.5, 15.5, 16.0, 15.5);

    public UniversalLotteryMachineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && half == DoubleBlockHalf.LOWER == (direction == Direction.UP)) {
            if (!neighborState.is(this) || neighborState.getValue(HALF) == half) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos lowerPos = half == DoubleBlockHalf.LOWER ? pos : pos.below();
            BlockEntity be = level.getBlockEntity(lowerPos);
            if (be instanceof UniversalLotteryMachineBlockEntity machine) {
                machine.dropExtraContents(level, lowerPos);
                Containers.dropContents(level, lowerPos, machine);
            }
            if (player.isCreative()) {
                BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
                BlockState otherState = level.getBlockState(otherPos);
                if (otherState.is(this) && otherState.getValue(HALF) != half) {
                    level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                    level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
                }
            } else if (half == DoubleBlockHalf.UPPER) {
                BlockPos lowerBlockPos = pos.below();
                BlockState lowerState = level.getBlockState(lowerBlockPos);
                if (lowerState.is(this) && lowerState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                    level.destroyBlock(lowerBlockPos, true, player);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return new UniversalLotteryMachineBlockEntity(pos, state);
        }
        return null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return createTickerHelper(blockEntityType, ModBlockEntities.UNIVERSAL_LOTTERY_MACHINE_BE.get(), UniversalLotteryMachineBlockEntity::tick);
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockEntity be = level.getBlockEntity(lowerPos);
        if (be instanceof UniversalLotteryMachineBlockEntity machine) {
            if (player.isShiftKeyDown()) {
                if (!machine.isRolling()) {
                    if (!level.isClientSide) {
                        int nextMode = (machine.getActiveMode() + 1) % 3;
                        machine.setActiveMode(nextMode);
                        Component modeName = switch (nextMode) {
                            case UniversalLotteryMachineBlockEntity.MODE_WHEEL -> Component.literal("§6[万能抽奖机] §f模式已切换为：§d🎡 虚空轮盘");
                            case UniversalLotteryMachineBlockEntity.MODE_SLOT -> Component.literal("§6[万能抽奖机] §f模式已切换为：§e🎰 虚空老虎机");
                            default -> Component.literal("§6[万能抽奖机] §f模式已切换为：§b🎲 虚空骰子");
                        };
                        player.displayClientMessage(modeName, true);
                        level.playSound(null, lowerPos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.8F, 1.4F);
                        level.playSound(null, lowerPos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.7F, 1.5F);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(machine, lowerPos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof UniversalLotteryMachineBlockEntity machine) {
                Containers.dropContents(level, pos, machine);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
