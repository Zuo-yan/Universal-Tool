package org.gwfx.universaltool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.phoenix.PhoenixPodMenu;
import org.gwfx.universaltool.phoenix.PodState;

public class PhoenixPodScreen extends AbstractContainerScreen<PhoenixPodMenu> {

    private static final ResourceLocation GUI_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/gui/container/phoenix_pod_gui.png");

    public PhoenixPodScreen(PhoenixPodMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY = 6;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);

        // 按钮悬停提示
        if (this.menu.getPodState() == PodState.EMPTY) {
            int bx = this.leftPos + 76;
            int by = this.topPos + 34;
            if (mouseX >= bx && mouseX <= bx + 24 && mouseY >= by && mouseY <= by + 20) {
                guiGraphics.renderTooltip(this.font, Component.translatable("gui.universal_tool.phoenix.start_btn_tip"), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        // 1. 绘制主体面板
        guiGraphics.blit(GUI_TEXTURE, relX, relY, 0, 0, this.imageWidth, this.imageHeight);

        PodState state = this.menu.getPodState();

        // 2. 状态指示灯与动画
        if (state == PodState.CULTIVATING) {
            // 进度条渲染 (u=176, v=0, w=24, h=16)
            int progress = this.menu.getProgress();
            int max = this.menu.getMaxProgress();
            int scaledW = (int) (24.0F * progress / max);
            guiGraphics.blit(GUI_TEXTURE, relX + 76, relY + 36, 176, 0, scaledW, 16);
        } else if (state == PodState.READY) {
            // 就绪绿灯 (u=176, v=16, 24x16)
            guiGraphics.blit(GUI_TEXTURE, relX + 76, relY + 36, 176, 16, 24, 16);
        } else {
            // 空置状态启动按钮 (u=176, v=32)
            boolean hovered = mouseX >= relX + 76 && mouseX <= relX + 100 && mouseY >= relY + 36 && mouseY <= relY + 52;
            int vOffset = hovered ? 48 : 32;
            guiGraphics.blit(GUI_TEXTURE, relX + 76, relY + 36, 176, vOffset, 24, 16);
        }

        // 3. 状态文字描述
        Component statusText = switch (state) {
            case EMPTY -> Component.translatable("gui.universal_tool.phoenix.state_empty");
            case CULTIVATING -> {
                int pct = (int) (100.0F * this.menu.getProgress() / this.menu.getMaxProgress());
                yield Component.translatable("gui.universal_tool.phoenix.state_cultivating", pct);
            }
            case READY -> Component.translatable("gui.universal_tool.phoenix.state_ready");
        };
        int textX = relX + (this.imageWidth - this.font.width(statusText)) / 2;
        guiGraphics.drawString(this.font, statusText, textX, relY + 20, 0x3F3F3F, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.menu.getPodState() == PodState.EMPTY) {
            int bx = this.leftPos + 76;
            int by = this.topPos + 36;
            if (mouseX >= bx && mouseX <= bx + 24 && mouseY >= by && mouseY <= by + 16) {
                if (this.minecraft != null && this.minecraft.gameMode != null) {
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
