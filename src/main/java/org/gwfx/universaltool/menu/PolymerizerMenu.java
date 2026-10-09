package org.gwfx.universaltool.menu;

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
import org.gwfx.universaltool.block.UniversalPolymerizerBlockEntity;
import org.gwfx.universaltool.init.ModMenuTypes;

public class PolymerizerMenu extends AbstractContainerMenu {

    public static final int CENTER_X = 88;
    public static final int CENTER_Y = 62;
    public static final int RADIUS = 48;

    private final Container container;
    private final ContainerData data;

    public PolymerizerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, new SimpleContainer(UniversalPolymerizerBlockEntity.TOTAL_SLOTS), new SimpleContainerData(2));
    }

    public PolymerizerMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenuTypes.UNIVERSAL_POLYMERIZER_MENU.get(), containerId);
        checkContainerSize(container, UniversalPolymerizerBlockEntity.TOTAL_SLOTS);
        checkContainerDataCount(data, 2);
        this.container = container;
        this.data = data;

        container.startOpen(playerInventory.player);

        // 1. 舒展大表盘：12 个时钟方位输入槽位 (0 ~ 11, R = 48)
        for (int i = 0; i < 12; i++) {
            double angleDeg = i * 30.0 - 90.0;
            double rad = Math.toRadians(angleDeg);
            int slotX = (int) Math.round(CENTER_X + RADIUS * Math.cos(rad)) - 8;
            int slotY = (int) Math.round(CENTER_Y + RADIUS * Math.sin(rad)) - 8;
            this.addSlot(new Slot(container, i, slotX, slotY));
        }

        // 2. 中心产物槽 (Slot 12)
        this.addSlot(new PolymerizerResultSlot(container, UniversalPolymerizerBlockEntity.SLOT_RESULT, CENTER_X - 8, CENTER_Y - 8));

        // 3. 玩家背包 (3 排 9 列, y=127)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 127 + row * 18));
            }
        }

        // 4. 玩家快捷栏 (1 排 9 列, y=187)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 187));
        }

        this.addDataSlots(data);
    }

    public int getCraftingProgress() {
        return this.data.get(0);
    }

    public int getMaxProgress() {
        return this.data.get(1);
    }

    public boolean isCrafting() {
        return getCraftingProgress() > 0;
    }

    public boolean hasResult() {
        return !container.getItem(UniversalPolymerizerBlockEntity.SLOT_RESULT).isEmpty();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0) {
            if (this.container instanceof UniversalPolymerizerBlockEntity be) {
                return be.startCrafting(player);
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

            if (index < UniversalPolymerizerBlockEntity.TOTAL_SLOTS) {
                if (!this.moveItemStackTo(itemstack1, UniversalPolymerizerBlockEntity.TOTAL_SLOTS, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(itemstack1, 0, 12, false)) {
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
