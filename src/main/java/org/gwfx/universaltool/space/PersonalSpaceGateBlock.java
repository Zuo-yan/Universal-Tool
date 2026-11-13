package org.gwfx.universaltool.space;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.gwfx.universaltool.init.ModBlockEntities;
import org.jetbrains.annotations.Nullable;

/**
 * 个人空间星门：平时以半高苔石符文基座存在；选中界域后上半截拱门缓缓展开并点亮螺旋能量漩涡，玩家走入后传送并收拢还原。
 */
public class PersonalSpaceGateBlock extends BaseEntityBlock {
    public static final MapCodec<PersonalSpaceGateBlock> CODEC = simpleCodec(PersonalSpaceGateBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public PersonalSpaceGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, HALF);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // 上半截碰撞体隐藏静态模型，由 LOWER 的 BlockEntityRenderer 平滑演绎展开/收拢动画
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(OPEN, false)
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.LOWER
                && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof PersonalSpaceGateBlockEntity gateEntity) {
            gateEntity.setOwner(player);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.is(this) && belowState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos partnerPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState partnerState = level.getBlockState(partnerPos);
            if (partnerState.is(this) && partnerState.getValue(HALF) != half
                    && (half == DoubleBlockHalf.LOWER || partnerState.getValue(OPEN))) {
                level.setBlock(partnerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
        }
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PersonalGateGeometry.shape(state.getValue(FACING), state.getValue(HALF));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(OPEN)) {
            return PersonalGateGeometry.openCollisionShape(state.getValue(FACING), state.getValue(HALF));
        }
        return PersonalGateGeometry.shape(state.getValue(FACING), state.getValue(HALF));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new PersonalSpaceGateBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return null;
        }
        if (level.isClientSide) {
            return createTickerHelper(type, ModBlockEntities.PERSONAL_SPACE_GATE_BE.get(), PersonalSpaceGateBlockEntity::clientTick);
        }
        return createTickerHelper(type, ModBlockEntities.PERSONAL_SPACE_GATE_BE.get(), PersonalSpaceGateBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos basePos = resolveBasePos(pos, state);
        BlockState baseState = level.getBlockState(basePos);
        if (!baseState.is(this)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (baseState.getValue(OPEN) && serverPlayer.isShiftKeyDown() && level instanceof ServerLevel serverLevel) {
                PrivateSpaceManager.closePortalAtGate(serverLevel, basePos);
                return InteractionResult.CONSUME;
            }
            PrivateSpaceManager.onGateUsed(serverPlayer, basePos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && state.getValue(OPEN) && entity instanceof ServerPlayer serverPlayer) {
            PrivateSpaceManager.enterOpenedGate(serverPlayer, resolveBasePos(pos, state));
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return;
        }
        if (!state.getValue(OPEN) || random.nextInt(2) != 0) {
            return;
        }
        float progress = 1.0F;
        if (level.getBlockEntity(pos) instanceof PersonalSpaceGateBlockEntity gate) {
            progress = gate.getDeployProgress(0.0F);
        }
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double radius = 0.18D + random.nextDouble() * 0.28D;
        double cx = pos.getX() + 0.5D + Math.cos(angle) * radius * 0.72D;
        double cy = pos.getY() + 0.75D + progress * 0.55D + Math.sin(angle) * radius * (0.5D + progress);
        double cz = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.18D;
        level.addParticle(
                random.nextInt(3) == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL,
                cx, cy, cz,
                -Math.sin(angle) * 0.025D, Math.cos(angle) * 0.035D, 0.0D
        );
    }

    public static BlockPos resolveBasePos(BlockPos pos, BlockState state) {
        return state.hasProperty(HALF) && state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }
}
