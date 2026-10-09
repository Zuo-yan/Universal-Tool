package org.gwfx.universaltool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.menu.PolymerizerMenu;

public class PolymerizerScreen extends AbstractContainerScreen<PolymerizerMenu> {

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/gui/container/polymerizer_gui.png");

    public PolymerizerScreen(PolymerizerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 212;
        this.inventoryLabelY = 116;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY = 5;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        if (!this.menu.hasResult() && !this.menu.isCrafting()) {
            int cx = this.leftPos + PolymerizerMenu.CENTER_X;
            int cy = this.topPos + PolymerizerMenu.CENTER_Y;
            if (mouseX >= cx - 9 && mouseX <= cx + 9 && mouseY >= cy - 9 && mouseY <= cy + 9) {
                guiGraphics.renderTooltip(this.font, Component.translatable("gui.universal_tool.polymerize_button"), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(GUI_TEXTURE, relX, relY, 0, 0, this.imageWidth, this.imageHeight);

        int cx = relX + PolymerizerMenu.CENTER_X - 9;
        int cy = relY + PolymerizerMenu.CENTER_Y - 9;

        if (this.menu.isCrafting()) {
            guiGraphics.blit(GUI_TEXTURE, cx, cy, 176, 18, 18, 18);
        } else if (!this.menu.hasResult()) {
            boolean hovered = mouseX >= cx && mouseX <= cx + 18 && mouseY >= cy && mouseY <= cy + 18;
            int vOffset = hovered ? 0 : 36;
            guiGraphics.blit(GUI_TEXTURE, cx, cy, 176, vOffset, 18, 18);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !this.menu.hasResult() && !this.menu.isCrafting()) {
            int cx = this.leftPos + PolymerizerMenu.CENTER_X;
            int cy = this.topPos + PolymerizerMenu.CENTER_Y;
            if (mouseX >= cx - 9 && mouseX <= cx + 9 && mouseY >= cy - 9 && mouseY <= cy + 9) {
                if (this.minecraft != null && this.minecraft.gameMode != null) {
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
