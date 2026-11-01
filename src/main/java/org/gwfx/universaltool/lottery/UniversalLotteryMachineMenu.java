package org.gwfx.universaltool.lottery;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.gwfx.universaltool.init.ModMenuTypes;

public class UniversalLotteryMachineMenu extends AbstractContainerMenu {

    public static final int INPUT_SLOT_X = 20;
    public static final int INPUT_SLOT_Y = 46;
    public static final int OUTPUT_SLOT_X = 158;
    public static final int OUTPUT_SLOT_Y = 46;

    private final Container container;
    private final ContainerData data;

    public UniversalLotteryMachineMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, new SimpleContainer(UniversalLotteryMachineBlockEntity.TOTAL_SLOTS), new SimpleContainerData(15));
    }

    public UniversalLotteryMachineMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenuTypes.UNIVERSAL_LOTTERY_MACHINE_MENU.get(), containerId);
        checkContainerSize(container, UniversalLotteryMachineBlockEntity.TOTAL_SLOTS);
        checkContainerDataCount(data, 15);
        this.container = container;
        this.data = data;

        container.startOpen(playerInventory.player);

        // 0: 献祭/投入物品输入槽 (虚空残片禁止投入)
        this.addSlot(new Slot(container, UniversalLotteryMachineBlockEntity.SLOT_INPUT, INPUT_SLOT_X, INPUT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !isRolling() && !stack.is(org.gwfx.universaltool.init.ModItems.VOID_SCRAP.get()) && LotteryValuator.evaluate(stack) > 0;
            }

            @Override
            public boolean mayPickup(Player player) {
                return !isRolling();
            }
        });

        // 1: 奖励产出槽 (不可放入，仅供取出)
        this.addSlot(new Slot(container, UniversalLotteryMachineBlockEntity.SLOT_OUTPUT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return !isRolling();
            }
        });

        // 玩家背包 (3 排 9 列, x=17, y=134)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 17 + col * 18, 134 + row * 18));
            }
        }

        // 玩家快捷栏 (1 排 9 列, x=17, y=192)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 17 + col * 18, 192));
        }

        this.addDataSlots(data);
    }

    public boolean isRolling() {
        return this.data.get(0) == 1;
    }

    public int getRollTicks() {
        return this.data.get(1);
    }

    public long getEstimatedPoints() {
        long low = (long) this.data.get(2) & 0xFFFFL;
        long high = (long) this.data.get(3) & 0xFFFFL;
        return (high << 16) | low;
    }

    public int getOddsPercent() {
        return this.data.get(4);
    }

    public LotteryValuator.RarityTier getTier() {
        int index = this.data.get(5);
        var tiers = LotteryValuator.RarityTier.values();
        if (index >= 0 && index < tiers.length) {
            return tiers[index];
        }
        return LotteryValuator.RarityTier.COMMON;
    }

    public int getActiveMode() {
        return this.data.get(6);
    }

    public int getWheelTargetSlice() {
        return this.data.get(7);
    }

    public int getSlotReel1() {
        return this.data.get(8);
    }

    public int getSlotReel2() {
        return this.data.get(9);
    }

    public int getSlotReel3() {
        return this.data.get(10);
    }

    public int getBetCount() {
        int bet = this.data.get(11);
        return Math.max(1, bet);
    }

    public int getDiceTargetNumber() {
        int val = this.data.get(12);
        return (val >= 1 && val <= 6) ? val : 1;
    }

    public int getRemainingMultiRolls() {
        return this.data.get(13);
    }

    public int getMultiRollRequested() {
        return Math.max(1, this.data.get(11));
    }

    public int getTotalRollDurationTicks() {
        int val = this.data.get(14);
        return val > 0 ? val : UniversalLotteryMachineBlockEntity.DEFAULT_ROLL_DURATION_TICKS;
    }

    public ItemStack getInputStack() {
        return this.container.getItem(UniversalLotteryMachineBlockEntity.SLOT_INPUT);
    }

    public boolean hasInput() {
        return !this.container.getItem(UniversalLotteryMachineBlockEntity.SLOT_INPUT).isEmpty();
    }

    public boolean hasOutput() {
        return !this.container.getItem(UniversalLotteryMachineBlockEntity.SLOT_OUTPUT).isEmpty();
    }

    public boolean canRoll() {
        if (!isRolling() && hasInput() && !hasOutput()) {
            ItemStack input = this.container.getItem(UniversalLotteryMachineBlockEntity.SLOT_INPUT);
            return !input.is(org.gwfx.universaltool.init.ModItems.VOID_SCRAP.get()) && LotteryValuator.evaluate(input) > 0;
        }
        return false;
    }

    public boolean canRoll(int mode, int bet) {
        if (isRolling() || !hasInput() || hasOutput()) return false;
        ItemStack input = this.container.getItem(UniversalLotteryMachineBlockEntity.SLOT_INPUT);
        if (input.is(org.gwfx.universaltool.init.ModItems.VOID_SCRAP.get()) || LotteryValuator.evaluate(input) <= 0) return false;
        return input.getCount() >= bet;
    }

    public void handleRollButton(Player player, int mode, int betCount) {
        if (this.container instanceof UniversalLotteryMachineBlockEntity be) {
            be.startRoll(player, mode, betCount);
        }
    }

    public void handleRollButton(Player player) {
        handleRollButton(player, getActiveMode(), getBetCount());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();

            if (index == UniversalLotteryMachineBlockEntity.SLOT_OUTPUT) {
                // 取出奖励 (槽位 2..37 为背包和快捷栏)
                if (!this.moveItemStackTo(stackInSlot, 2, 38, true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stackInSlot, itemstack);
            } else if (index == UniversalLotteryMachineBlockEntity.SLOT_INPUT) {
                // 移出输入槽
                if (!this.moveItemStackTo(stackInSlot, 2, 38, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 玩家背包快捷放入输入槽 (排除虚空残片与0价值物品)
                if (!isRolling() && !stackInSlot.is(org.gwfx.universaltool.init.ModItems.VOID_SCRAP.get()) && LotteryValuator.evaluate(stackInSlot) > 0) {
                    if (!this.moveItemStackTo(stackInSlot, UniversalLotteryMachineBlockEntity.SLOT_INPUT, UniversalLotteryMachineBlockEntity.SLOT_INPUT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stackInSlot.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, stackInSlot);
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }
}
