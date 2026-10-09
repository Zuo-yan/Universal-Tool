package org.gwfx.universaltool.phoenix;

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

public class PhoenixPodMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;

    public PhoenixPodMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, new SimpleContainer(PhoenixPodBlockEntity.TOTAL_SLOTS), new SimpleContainerData(3));
    }

    public PhoenixPodMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenuTypes.PHOENIX_POD_MENU.get(), containerId);
        checkContainerSize(container, PhoenixPodBlockEntity.TOTAL_SLOTS);
        checkContainerDataCount(data, 3);
        this.container = container;
        this.data = data;

        container.startOpen(playerInventory.player);

        // 0: DNA 槽
        this.addSlot(new Slot(container, PhoenixPodBlockEntity.SLOT_DNA, 56, 36) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof DnaSampleItem;
            }
        });

        // 1: 营养液材料槽
        this.addSlot(new Slot(container, PhoenixPodBlockEntity.SLOT_NUTRIENT, 104, 36));

        // 玩家主背包 (3x9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // 玩家快捷栏 (1x9)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        this.addDataSlots(data);
    }

    public int getProgress() {
        return this.data.get(0);
    }

    public int getMaxProgress() {
        return this.data.get(1);
    }

    public PodState getPodState() {
        int idx = this.data.get(2);
        if (idx >= 0 && idx < PodState.values().length) {
            return PodState.values()[idx];
        }
        return PodState.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0) {
            if (this.container instanceof PhoenixPodBlockEntity be) {
                return be.startCultivating(player);
            }
        }
        return super.clickMenuButton(player, id);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            if (index < PhoenixPodBlockEntity.TOTAL_SLOTS) {
                if (!this.moveItemStackTo(itemstack1, PhoenixPodBlockEntity.TOTAL_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(itemstack1, 0, PhoenixPodBlockEntity.TOTAL_SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
