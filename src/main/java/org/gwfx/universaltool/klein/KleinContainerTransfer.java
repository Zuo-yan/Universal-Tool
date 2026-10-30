package org.gwfx.universaltool.klein;

import java.math.BigInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Transfers item-handler storage from a block into the server-authoritative warehouse. */
public final class KleinContainerTransfer {
    private static final int MAX_SLOTS_PER_REQUEST = 512;
    private static final int MAX_STACKS_PER_SLOT = 1024;

    public static boolean isSupported(Level level, BlockPos pos, Player player, @Nullable Direction face) {
        return findStorage(level, pos, player, face, false) != null;
    }

    /** Returns the number of items moved. */
    public static int transfer(ServerPlayer player, BlockPos pos, @Nullable Direction face) {
        IItemHandler storage = findStorage(player.level(), pos, player, face, true);
        if (storage == null) return 0;

        KleinStorageSavedData saved = KleinStorageSavedData.get(player.server);
        KleinWarehouse warehouse = saved.warehouse(player.getUUID());
        int moved = 0;
        int slotCount = Math.min(storage.getSlots(), MAX_SLOTS_PER_REQUEST);
        for (int slot = 0; slot < slotCount; slot++) {
            for (int attempt = 0; attempt < MAX_STACKS_PER_SLOT; attempt++) {
                ItemStack current = storage.getStackInSlot(slot);
                if (!KleinWarehouse.canStore(current)) break;

                ItemStack extracted = storage.extractItem(slot, current.getCount(), false);
                if (extracted.isEmpty()) break;

                int accepted = warehouse.insert(extracted, BigInteger.valueOf(extracted.getCount())).intValue();
                if (accepted > 0) moved += accepted;
                if (accepted < extracted.getCount()) restoreRemainder(player, storage, slot, extracted, accepted);
                if (accepted < extracted.getCount() || storage.getStackInSlot(slot).isEmpty()) break;
            }
        }

        if (moved > 0) {
            saved.changed();
            saved.syncViewers(player.getUUID());
        }
        return moved;
    }

    private static void restoreRemainder(Player player, IItemHandler storage, int slot,
                                         ItemStack extracted, int accepted) {
        int remainderCount = extracted.getCount() - accepted;
        if (remainderCount <= 0) return;
        ItemStack remainder = extracted.copyWithCount(remainderCount);
        ItemStack rejected = storage.insertItem(slot, remainder, false);
        if (!rejected.isEmpty()) player.drop(rejected, false);
    }

    @Nullable
    private static IItemHandler findStorage(Level level, BlockPos pos, Player player,
                                            @Nullable Direction face, boolean requireOpenedLoot) {
        if (player == null || player.isSpectator()) return null;
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.COMPOSTER)) return null;
        if (state.getBlock() instanceof ChestBlock chest
                && ChestBlock.getContainer(chest, state, level, pos, false) == null) return null;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AbstractFurnaceBlockEntity
                || blockEntity instanceof BrewingStandBlockEntity
                || blockEntity instanceof CrafterBlockEntity) return null;
        if (requireOpenedLoot && blockEntity instanceof RandomizableContainerBlockEntity randomizable
                && randomizable.getLootTable() != null) return null;
        if (blockEntity instanceof BaseContainerBlockEntity container
                && (!container.canOpen(player) || !container.stillValid(player))) return null;

        IItemHandler storage = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (storage == null && face != null) {
            storage = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
        }
        return storage != null && storage.getSlots() > 0 ? storage : null;
    }

    private KleinContainerTransfer() {}
}
