package org.gwfx.universaltool.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
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
import org.gwfx.universaltool.init.ModBlockEntities;
import org.gwfx.universaltool.init.ModRecipes;
import org.gwfx.universaltool.menu.PolymerizerMenu;
import org.gwfx.universaltool.recipe.PolymerizerRecipe;
import org.gwfx.universaltool.recipe.PolymerizerRecipeInput;

import java.util.Optional;

public class UniversalPolymerizerBlockEntity extends BlockEntity implements MenuProvider, Container {

    public static final int TOTAL_SLOTS = 13;
    public static final int SLOT_RESULT = 12;
    public static final int MAX_PROGRESS = 30; // 1.5 秒蓄力

    private final NonNullList<ItemStack> items = NonNullList.withSize(TOTAL_SLOTS, ItemStack.EMPTY);
    private int craftingProgress = 0;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> craftingProgress;
                case 1 -> MAX_PROGRESS;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                craftingProgress = value;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public UniversalPolymerizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.UNIVERSAL_POLYMERIZER_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, UniversalPolymerizerBlockEntity be) {
        if (level.isClientSide) {
            return;
        }

        if (be.craftingProgress > 0) {
            be.craftingProgress--;
            be.setChanged();

            if (be.craftingProgress == 0) {
                // 倒计时结束，尝试执行聚合
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
            this.craftingProgress = MAX_PROGRESS;
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

            // 扣除四周材料
            for (int i = 0; i < 12; i++) {
                ItemStack stack = items.get(i);
                if (!stack.isEmpty()) {
                    stack.shrink(1);
                }
            }

            // 产出放到中心槽
            items.set(SLOT_RESULT, output);

            // 聚合成功声效
            serverLevel.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 1.1F);
            serverLevel.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6F, 1.4F);
            setChanged();
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        craftingProgress = tag.getInt("CraftingProgress");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("CraftingProgress", craftingProgress);
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
