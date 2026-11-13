package org.gwfx.universaltool.lottery;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.gwfx.universaltool.block.AbstractDoubleEntityBlock;
import org.gwfx.universaltool.init.ModBlockEntities;

import javax.annotation.Nullable;

public class UniversalLotteryMachineBlock extends AbstractDoubleEntityBlock {

    public static final MapCodec<UniversalLotteryMachineBlock> CODEC = simpleCodec(UniversalLotteryMachineBlock::new);
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

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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
        BlockPos lowerPos = getLowerPos(state, pos);
        BlockEntity be = level.getBlockEntity(lowerPos);
        if (be instanceof UniversalLotteryMachineBlockEntity machine) {
            if (player.isShiftKeyDown()) {
                if (!machine.isRolling()) {
                    if (!level.isClientSide) {
                        int nextMode = (machine.getActiveMode() + 1) % 3;
                        machine.setActiveMode(nextMode);
                        Component modeName = switch (nextMode) {
                            case UniversalLotteryMachineBlockEntity.MODE_WHEEL ->
                                    Component.translatable("message.universal_tool.lottery.mode_wheel");
                            case UniversalLotteryMachineBlockEntity.MODE_SLOT ->
                                    Component.translatable("message.universal_tool.lottery.mode_slot");
                            default ->
                                    Component.translatable("message.universal_tool.lottery.mode_dice");
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
    protected void onLowerRemoved(Level level, BlockPos lowerPos, @Nullable BlockEntity be) {
        if (!level.isClientSide && be instanceof UniversalLotteryMachineBlockEntity machine) {
            machine.dropExtraContents(level, lowerPos);
        }
    }
}
