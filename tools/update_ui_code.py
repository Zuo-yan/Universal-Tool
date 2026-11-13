import json
from pathlib import Path

root = Path(r"A:\Game\Code\Minecraft-Mod\万能工具")

# 1. Update PolymerizerMenu.java
menu_path = root / "src/main/java/org/gwfx/universaltool/menu/PolymerizerMenu.java"
text = menu_path.read_text(encoding="utf-8")
text = text.replace("public static final int CENTER_X = 88;", "public static final int CENTER_X = 96;")
text = text.replace("public static final int CENTER_Y = 62;", "public static final int CENTER_Y = 70;")
text = text.replace("public static final int RADIUS = 48;", "public static final int RADIUS = 44;")
text = text.replace("8 + col * 18, 127 + row * 18", "16 + col * 18, 139 + row * 18")
text = text.replace("8 + col * 18, 187", "16 + col * 18, 199")
menu_path.write_text(text, encoding="utf-8", newline="\n")
print("Updated PolymerizerMenu.java")

# 2. Update PolymerizerScreen.java
screen_path = root / "src/main/java/org/gwfx/universaltool/client/PolymerizerScreen.java"
screen_code = """package org.gwfx.universaltool.client;

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

        if (!this.menu.hasResult() && !this.menu.isCrafting() && isButtonHovered(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, Component.translatable("gui.universal_tool.polymerize_button"), mouseX, mouseY);
        }
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

        int titleX = relX + (this.imageWidth - this.font.width(this.title)) / 2;
        guiGraphics.drawString(this.font, this.title, titleX, relY + 8, 0xFFE0C060, true);

        Component hint = Component.translatable("gui.universal_tool.polymerizer_hint");
        int hintX = relX + (this.imageWidth - this.font.width(hint)) / 2;
        guiGraphics.drawString(this.font, hint, hintX, relY + 28, 0xFF7A889B, false);

        Component btnText = this.menu.isCrafting()
                ? Component.translatable("gui.universal_tool.polymerizing")
                : Component.translatable("gui.universal_tool.start_polymerize");
        int btnTextX = btnX + (48 - this.font.width(btnText)) / 2;
        int textColor = this.menu.isCrafting() ? 0xFFFFFFFF : (hovered ? 0xFFFFFFFF : 0xFF52F6EA);
        guiGraphics.drawString(this.font, btnText, btnTextX, btnY + 2, textColor, false);

        Component invTitle = Component.translatable("gui.universal_tool.inventory");
        guiGraphics.drawString(this.font, invTitle, relX + 16, relY + 127, 0xFF8A96A8, false);
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
"""
screen_path.write_text(screen_code, encoding="utf-8", newline="\n")
print("Updated PolymerizerScreen.java")

# 3. Update lang files
zh_path = root / "src/main/resources/assets/universal_tool/lang/zh_cn.json"
zh = json.loads(zh_path.read_text(encoding="utf-8"))
zh["gui.universal_tool.polymerizer_hint"] = "放入材料，点击中央核心开始聚合"
zh["gui.universal_tool.start_polymerize"] = "开始聚合"
zh["gui.universal_tool.polymerizing"] = "聚合中…"
zh["gui.universal_tool.inventory"] = "背包"
zh_path.write_text(json.dumps(zh, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")

en_path = root / "src/main/resources/assets/universal_tool/lang/en_us.json"
en = json.loads(en_path.read_text(encoding="utf-8"))
en["gui.universal_tool.polymerizer_hint"] = "Place materials, tap core to start"
en["gui.universal_tool.start_polymerize"] = "Start"
en["gui.universal_tool.polymerizing"] = "Synthesizing…"
en["gui.universal_tool.inventory"] = "Inventory"
en_path.write_text(json.dumps(en, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
print("Updated lang files")
