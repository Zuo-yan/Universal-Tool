package org.gwfx.universaltool.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.gwfx.universaltool.config.ModConfigs;
import org.gwfx.universaltool.init.ModBlockEntities;
import org.gwfx.universaltool.init.ModRecipes;
import org.gwfx.universaltool.menu.PolymerizerMenu;
import org.gwfx.universaltool.recipe.PolymerizerRecipe;
import org.gwfx.universaltool.recipe.PolymerizerRecipeInput;

import javax.annotation.Nullable;
import java.util.Optional;

public class UniversalPolymerizerBlockEntity extends BlockEntity implements MenuProvider, Container {

    public static final int TOTAL_SLOTS = 13;
    public static final int SLOT_RESULT = 12;
    public static final int MAX_PROGRESS = 30; // Default fallback 1.5s

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
    private int craftingProgress = 0;
    private int craftingDuration = MAX_PROGRESS;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> craftingProgress;
                case 1 -> craftingDuration;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                craftingProgress = value;
            } else if (index == 1) {
                craftingDuration = Math.max(1, value);
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public UniversalPolymerizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UNIVERSAL_POLYMERIZER_BE.get(), pos, state);
        this.craftingDuration = Math.max(1, ModConfigs.getPolymerizerDefaultTicks());
    }

    public int getCraftingProgress() {
        return craftingProgress;
    }

    public int getCraftingDuration() {
        return craftingDuration;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, UniversalPolymerizerBlockEntity be) {
        if (level.isClientSide) {
            if (be.craftingProgress > 0) {
                be.craftingProgress--;
            }
            return;
        }

        if (be.craftingProgress > 0) {
            int extra = org.gwfx.universaltool.skill.SkillModSynergyHandler
                    .polymerizerExtraProgressTicks((ServerLevel) level, pos);
            be.craftingProgress = Math.max(0, be.craftingProgress - 1 - extra);
            // Mark chunk dirty without spamming block update packets every tick; start/finish syncs state.
            level.blockEntityChanged(pos);

            if (be.craftingProgress == 0) {
                be.finishCrafting((ServerLevel) level, pos);
            }
        }
    }

    public boolean startCrafting(Player player) {
        if (level == null || level.isClientSide || craftingProgress > 0) {
            return false;
        }

        // 中心槽必须为空
        if (!items.get(SLOT_RESULT).isEmpty()) {
            return false;
        }

        Optional<RecipeHolder<PolymerizerRecipe>> match = getMatchingRecipe();
        if (match.isPresent()) {
            int recipeDuration = match.get().value().getDuration();
            int duration = recipeDuration > 0 ? recipeDuration : Math.max(1, ModConfigs.getPolymerizerDefaultTicks());
            this.craftingDuration = duration;
            this.craftingProgress = duration;
            level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0F, 1.2F);
            setChanged();
            return true;
        }
        return false;
    }

    private Optional<RecipeHolder<PolymerizerRecipe>> getMatchingRecipe() {
        if (level == null) return Optional.empty();
        PolymerizerRecipeInput input = new PolymerizerRecipeInput(items.subList(0, 12));
        return level.getRecipeManager().getRecipeFor(ModRecipes.POLYMERIZING_TYPE.get(), input, level);
    }

    private void finishCrafting(ServerLevel serverLevel, BlockPos pos) {
        Optional<RecipeHolder<PolymerizerRecipe>> match = getMatchingRecipe();
        if (match.isPresent() && items.get(SLOT_RESULT).isEmpty()) {
            PolymerizerRecipe recipe = match.get().value();
            ItemStack output = recipe.assemble(new PolymerizerRecipeInput(items.subList(0, 12)), serverLevel.registryAccess());

            // 扣除四周材料并返还合成容器余物 (如空桶、空瓶等)
            for (int i = 0; i < 12; i++) {
                ItemStack stack = items.get(i);
                if (!stack.isEmpty()) {
                    ItemStack remainder = stack.getCraftingRemainingItem();
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        items.set(i, remainder);
                    } else if (!remainder.isEmpty()) {
                        Containers.dropItemStack(serverLevel, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, remainder);
                    }
                }
            }

            // 产出放到中心槽
            items.set(SLOT_RESULT, output);

            // 聚合成功声效
            serverLevel.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 1.1F);
            serverLevel.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.4F);
            org.gwfx.universaltool.skill.SkillModSynergyHandler.onPolymerizerFinished(serverLevel, pos);
            setChanged();
        } else {
            setChanged();
        }
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        craftingProgress = tag.getInt("CraftingProgress");
        craftingDuration = tag.contains("CraftingDuration") ? Math.max(1, tag.getInt("CraftingDuration"))
                : Math.max(1, ModConfigs.getPolymerizerDefaultTicks());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("CraftingProgress", craftingProgress);
        tag.putInt("CraftingDuration", craftingDuration);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            loadAdditional(tag, lookupProvider);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.universal_tool.universal_polymerizer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PolymerizerMenu(containerId, playerInventory, this, this.dataAccess);
    }

    // ===== Container 接口实现 =====

    @Override
    public int getContainerSize() {
        return TOTAL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack item : items) {
            if (!item.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }
}
