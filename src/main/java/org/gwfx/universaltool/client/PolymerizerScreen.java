package org.gwfx.universaltool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.menu.PolymerizerMenu;

import java.util.List;

public class PolymerizerScreen extends AbstractContainerScreen<PolymerizerMenu> {

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/gui/container/polymerizer_gui.png");

    public PolymerizerScreen(PolymerizerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 192;
        this.imageHeight = 224;
        this.inventoryLabelY = 127;
        this.inventoryLabelX = 16;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = -9999;
        this.titleLabelY = -9999;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;
        if (mouseX >= relX + 48 && mouseX <= relX + 144 && mouseY >= relY + 3 && mouseY <= relY + 15) {
            guiGraphics.renderComponentTooltip(this.font, List.of(
                    Component.translatable("block.universal_tool.universal_polymerizer").withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.universal_tool.polymerizer_subtitle").withStyle(ChatFormatting.AQUA)
            ), mouseX, mouseY);
        } else if (!this.menu.hasResult() && !this.menu.isCrafting() && isButtonHovered(mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(this.font, List.of(
                    Component.translatable("gui.universal_tool.polymerize_button").withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.universal_tool.polymerize_tooltip_detail").withStyle(ChatFormatting.GRAY)
            ), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFF96A4B8, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        guiGraphics.blit(GUI_TEXTURE, relX, relY, 0, 0, this.imageWidth, this.imageHeight);

        int cx = relX + PolymerizerMenu.CENTER_X;
        int cy = relY + PolymerizerMenu.CENTER_Y;
        int btnX = relX + 72;
        int btnY = relY + 82;
        boolean hovered = isButtonHovered(mouseX, mouseY);

        if (this.menu.isCrafting()) {
            guiGraphics.blit(GUI_TEXTURE, btnX, btnY, 196, 32, 48, 12);
            int progress = this.menu.getCraftingProgress();
            int frame = (progress / 4) % 4;
            int frameU = 196 + (frame % 2) * 20;
            int frameV = 48 + (frame / 2) * 20;
            guiGraphics.blit(GUI_TEXTURE, cx - 9, cy - 9, frameU, frameV, 18, 18);
        } else if (hovered && !this.menu.hasResult()) {
            guiGraphics.blit(GUI_TEXTURE, btnX, btnY, 196, 16, 48, 12);
        } else if (!this.menu.hasResult()) {
            guiGraphics.blit(GUI_TEXTURE, btnX, btnY, 196, 0, 48, 12);
        }

        // 1. 顶部八角牌匾内的精美主标题（亮金，居中，不遮挡上方与下方槽位）
        int titleX = relX + (this.imageWidth - this.font.width(this.title)) / 2;
        guiGraphics.drawString(this.font, this.title, titleX, relY + 5, 0xFFF5D678, true);

        // 2. 胶囊按钮文字（居中对齐，严密限制在48px按钮内，不遮挡任何槽位）
        Component btnText = this.menu.isCrafting()
                ? Component.translatable("gui.universal_tool.polymerizing")
                : Component.translatable("gui.universal_tool.start_polymerize");
        int textW = this.font.width(btnText);
        int btnTextX = btnX + Math.max(2, (48 - textW) / 2);
        int textColor = this.menu.isCrafting() ? 0xFFFFFFFF : (hovered ? 0xFFFFFFFF : 0xFF52F6EA);
        guiGraphics.drawString(this.font, btnText, btnTextX, btnY + 2, textColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !this.menu.hasResult() && !this.menu.isCrafting()) {
            if (isButtonHovered(mouseX, mouseY)) {
                if (this.minecraft != null && this.minecraft.gameMode != null) {
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isButtonHovered(double mouseX, double mouseY) {
        int btnX = this.leftPos + 72;
        int btnY = this.topPos + 82;
        boolean onPill = mouseX >= btnX && mouseX <= btnX + 48 && mouseY >= btnY && mouseY <= btnY + 12;
        int cx = this.leftPos + PolymerizerMenu.CENTER_X;
        int cy = this.topPos + PolymerizerMenu.CENTER_Y;
        boolean onCenterSlot = mouseX >= cx - 9 && mouseX <= cx + 9 && mouseY >= cy - 9 && mouseY <= cy + 9;
        return onPill || onCenterSlot;
    }
}
