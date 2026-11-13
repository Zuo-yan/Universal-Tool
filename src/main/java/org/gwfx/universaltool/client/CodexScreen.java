package org.gwfx.universaltool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.gwfx.universaltool.UniversalToolMod;
import org.gwfx.universaltool.network.DeleteWaypointPayload;
import org.gwfx.universaltool.network.SaveWaypointPayload;
import org.gwfx.universaltool.network.TeleportRequestPayload;
import org.gwfx.universaltool.network.WriteScrollPayload;
import org.gwfx.universaltool.waypoint.Waypoint;
import org.gwfx.universaltool.waypoint.WaypointData;

import java.util.List;

public class CodexScreen extends Screen {

    private static final ResourceLocation PARCHMENT_BG =
            ResourceLocation.fromNamespaceAndPath(UniversalToolMod.MODID, "textures/gui/codex_parchment.png");

    private static final int GUI_WIDTH = 280;
    private static final int GUI_HEIGHT = 190;
    public static final int VOLUMES_COUNT = 3; // 共 3 卷，每卷 12 点，总计 36 点

    private static final String[] ROMAN_NUMERALS = {
            "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII",
            "XIII", "XIV", "XV", "XVI", "XVII", "XVIII", "XIX", "XX", "XXI", "XXII", "XXIII", "XXIV",
            "XXV", "XXVI", "XXVII", "XXVIII", "XXIX", "XXX", "XXXI", "XXXII", "XXXIII", "XXXIV", "XXXV", "XXXVI"
    };

    private int leftPos;
    private int topPos;
    private int currentVolume = 0; // 0, 1, 2
    private int selectedIndex = 0;

    // 命名输入框
    private boolean isNaming = false;
    private String pendingPhotoId = "";
    private EditBox nameInput;
    private Button confirmButton;

    // 拍立得快门闪白计时
    private int shutterFlash = 0;

    public CodexScreen() {
        super(Component.translatable("gui.universal_tool.codex.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - GUI_WIDTH) / 2;
        this.topPos = (this.height - GUI_HEIGHT) / 2;

        this.nameInput = new EditBox(this.font, this.leftPos + 60, this.topPos + 80, 160, 20, Component.translatable("gui.universal_tool.codex.location_name"));
        this.nameInput.setMaxLength(32);
        this.nameInput.setValue(Component.translatable("gui.universal_tool.codex.default_waypoint", ClientWaypointCache.getWaypoints().size() + 1).getString());
        this.nameInput.setVisible(this.isNaming);

        this.confirmButton = Button.builder(Component.translatable("gui.universal_tool.codex.confirm"), btn -> onConfirmAdd())
                .bounds(this.leftPos + 100, this.topPos + 105, 80, 20)
                .build();
        this.confirmButton.visible = this.isNaming;

        this.addRenderableWidget(this.nameInput);
        this.addRenderableWidget(this.confirmButton);
    }

    public void enterNamingMode(String photoId) {
        this.pendingPhotoId = photoId;
        this.isNaming = true;
        this.shutterFlash = 6;
        if (this.nameInput != null) {
            this.nameInput.setVisible(true);
            this.nameInput.setValue(Component.translatable("gui.universal_tool.codex.default_waypoint", ClientWaypointCache.getWaypoints().size() + 1).getString());
            this.setFocused(this.nameInput);
        }
        if (this.confirmButton != null) {
            this.confirmButton.visible = true;
        }
    }

    private void onConfirmAdd() {
        if (this.minecraft != null && this.minecraft.player != null) {
            String name = this.nameInput.getValue().trim();
            if (name.isEmpty()) name = Component.translatable("gui.universal_tool.codex.unnamed_waypoint").getString();
            var p = this.minecraft.player;
            Waypoint wp = Waypoint.of(
                    name,
                    p.level().dimension().location(),
                    p.getX(), p.getY(), p.getZ(),
                    p.getYRot(), p.getXRot(),
                    this.pendingPhotoId
            );
            PacketDistributor.sendToServer(new SaveWaypointPayload(wp));
        }
        this.isNaming = false;
        this.nameInput.setVisible(false);
        this.confirmButton.visible = false;
    }

    private void startPureCapture() {
        if (ClientWaypointCache.getWaypoints().size() >= ClientWaypointCache.getMaxCapacity()) {
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable("message.universal_tool.codex_full"), true);
            }
            return;
        }
        // 触发纯净世界抓帧
        PhotoCaptureHandler.requestCapture();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.blit(PARCHMENT_BG, this.leftPos, this.topPos, 0, 0, GUI_WIDTH, GUI_HEIGHT, 280, 190);

        List<Waypoint> waypoints = ClientWaypointCache.getWaypoints();

        if (this.isNaming) {
            guiGraphics.fill(this.leftPos, this.topPos, this.leftPos + GUI_WIDTH, this.topPos + GUI_HEIGHT, 0x66000000);
            int mx = this.leftPos + 40;
            int my = this.topPos + 50;
            int mw = 200;
            int mh = 90;
            guiGraphics.fill(mx + 1, my + 1, mx + mw - 1, my + mh - 1, 0xFFC6C6C6);
            drawBevelOutline(guiGraphics, mx, my, mw, mh, 0xFF000000, 0xFFFFFFFF, 0xFF555555);
            drawCenteredInk(guiGraphics, Component.translatable("gui.universal_tool.codex.record_waypoint_title"),
                    this.leftPos + 140, this.topPos + 62, 0xFF404040);
            for (var renderable : this.renderables) {
                renderable.render(guiGraphics, mouseX, mouseY, partialTick);
            }
            return;
        }

        renderLeftPage(guiGraphics, waypoints);
        renderRightPage(guiGraphics, waypoints, mouseX, mouseY);

        if (this.shutterFlash > 0) {
            float alpha = (this.shutterFlash / 6.0F) * 0.7F;
            int a = (int) (alpha * 255) << 24;
            guiGraphics.fill(0, 0, this.width, this.height, a | 0x00FFFFFF);
            this.shutterFlash--;
        }
    }

    private void drawCenteredInk(GuiGraphics g, Component text, int centerX, int y, int color) {
        g.drawString(this.font, text, centerX - this.font.width(text) / 2, y, color, false);
    }

    private void drawCenteredInk(GuiGraphics g, String text, int centerX, int y, int color) {
        g.drawString(this.font, text, centerX - this.font.width(text) / 2, y, color, false);
    }

    private void renderLeftPage(GuiGraphics guiGraphics, List<Waypoint> waypoints) {
        int pageLeft = this.leftPos + 16;
        int pageTop = this.topPos + 18;

        if (waypoints.isEmpty() || selectedIndex < 0 || selectedIndex >= waypoints.size()) {
            drawCenteredInk(guiGraphics, Component.translatable("gui.universal_tool.codex.journal_title"), pageLeft + 55, pageTop + 30, 0xFF1E1812);
            drawCenteredInk(guiGraphics, Component.translatable("gui.universal_tool.codex.empty_hint_1"), pageLeft + 55, pageTop + 55, 0xFF3F3325);
            drawCenteredInk(guiGraphics, Component.translatable("gui.universal_tool.codex.empty_hint_2"), pageLeft + 55, pageTop + 75, 0xFF3F3325);
            return;
        }

        Waypoint wp = waypoints.get(selectedIndex);

        int photoX = pageLeft + 4;
        int photoY = pageTop + 4;
        guiGraphics.fill(photoX - 2, photoY - 2, photoX + 112, photoY + 64, 0xFFE6D7B8);
        drawBevelOutline(guiGraphics, photoX - 3, photoY - 3, 116, 68, 0xFF5C4328, 0xFF8C7252, 0xFFFBF3DF);

        ResourceLocation photoTex = PhotoManager.getOrLoadTexture(wp.photoId());
        if (photoTex != null) {
            guiGraphics.blit(photoTex, photoX, photoY, 0, 0, 110, 62, 110, 62);
        } else {
            guiGraphics.fill(photoX, photoY, photoX + 110, photoY + 62, 0xFF3F3325);
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.universal_tool.codex.no_photo"), photoX + 55, photoY + 26, 0xFFEAD9B3);
        }

        int infoY = photoY + 70;
        guiGraphics.drawString(this.font, Component.literal(wp.name()).withStyle(ChatFormatting.BOLD), pageLeft + 2, infoY, 0xFF1E1812, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.universal_tool.codex.dimension", getDimensionName(wp.dimension())), pageLeft + 2, infoY + 14, 0xFF3F3325, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.universal_tool.codex.coords",
                Math.round(wp.x()), Math.round(wp.y()), Math.round(wp.z())), pageLeft + 2, infoY + 26, 0xFF3F3325, false);

        if (this.minecraft != null && this.minecraft.player != null) {
            var player = this.minecraft.player;
            if (player.level().dimension().location().equals(wp.dimension())) {
                double dist = Math.sqrt(player.distanceToSqr(wp.x(), wp.y(), wp.z()));
                guiGraphics.drawString(this.font, Component.translatable("gui.universal_tool.codex.distance", Math.round(dist)), pageLeft + 2, infoY + 38, 0xFF5C4A36, false);
            } else {
                guiGraphics.drawString(this.font, Component.translatable("gui.universal_tool.codex.distance_other_dim"), pageLeft + 2, infoY + 38, 0xFF7A5F24, false);
            }
        }
    }

    private void renderRightPage(GuiGraphics guiGraphics, List<Waypoint> waypoints, int mouseX, int mouseY) {
        int rightLeft = this.leftPos + 148;
        int rightTop = this.topPos + 16;
        int maxCap = ClientWaypointCache.getMaxCapacity();

        // 顶部「记录当前位置」经典羊皮纸按钮
        boolean hoverAdd = mouseX >= rightLeft + 48 && mouseX <= rightLeft + 122 && mouseY >= rightTop - 2 && mouseY <= rightTop + 14;
        drawPixelButton(guiGraphics, rightLeft + 48, rightTop - 2, 74, 16,
                hoverAdd ? 0xFFF2E6CA : 0xFFE2D1AE,
                0xFF5C4328,
                hoverAdd ? 0xFFFFFAEC : 0xFFF7ECD4,
                0xFFAC946C);
        drawCenteredInk(guiGraphics, Component.translatable("gui.universal_tool.codex.record_here"), rightLeft + 85, rightTop + 2, 0xFF1E1812);

        // 12 个条目渲染 (根据当前卷切换)
        int itemH = 9;
        int startIndex = currentVolume * 12;
        for (int i = 0; i < 12; i++) {
            int globalIdx = startIndex + i;
            int itemY = rightTop + 16 + i * itemH;
            boolean isUnlocked = globalIdx < maxCap;
            boolean isSelected = (globalIdx == selectedIndex && isUnlocked && globalIdx < waypoints.size());
            boolean hover = mouseX >= rightLeft && mouseX <= rightLeft + 120 && mouseY >= itemY && mouseY < itemY + itemH;

            int bgCol = isSelected ? 0xFFD9C59E : (hover ? 0xFFE8DAB9 : 0x00000000);
            if (bgCol != 0) {
                guiGraphics.fill(rightLeft, itemY, rightLeft + 120, itemY + itemH - 1, bgCol);
            }

            String numStr = (globalIdx < ROMAN_NUMERALS.length ? ROMAN_NUMERALS[globalIdx] : String.valueOf(globalIdx + 1)) + ".";
            guiGraphics.drawString(this.font, numStr, rightLeft + 2, itemY, isUnlocked ? 0xFF3F3325 : 0xFF9C8B72, false);

            if (!isUnlocked) {
                guiGraphics.drawString(this.font, Component.translatable("gui.universal_tool.codex.locked_slot"), rightLeft + 28, itemY, 0xFF9C8B72, false);
            } else if (globalIdx < waypoints.size()) {
                String name = waypoints.get(globalIdx).name();
                if (name.length() > 7) name = name.substring(0, 6) + "..";
                guiGraphics.drawString(this.font, name, rightLeft + 28, itemY, 0xFF1E1812, false);

                // 删除按钮
                boolean hoverDel = mouseX >= rightLeft + 108 && mouseX <= rightLeft + 118 && mouseY >= itemY && mouseY < itemY + itemH;
                guiGraphics.drawString(this.font, "x", rightLeft + 110, itemY, hoverDel ? 0xFFAA2222 : 0xFF7D6B54, false);
            } else {
                guiGraphics.drawString(this.font, "---", rightLeft + 28, itemY, 0xFF8C7A62, false);
            }
        }

        // 翻页控件 [ < ] 第 1/3 卷 [ > ]
        int pageControlY = rightTop + 128;
        boolean hoverPrev = mouseX >= rightLeft + 10 && mouseX <= rightLeft + 28 && mouseY >= pageControlY && mouseY <= pageControlY + 12;
        boolean hoverNext = mouseX >= rightLeft + 92 && mouseX <= rightLeft + 110 && mouseY >= pageControlY && mouseY <= pageControlY + 12;

        drawPixelButton(guiGraphics, rightLeft + 10, pageControlY, 18, 12,
                hoverPrev ? 0xFFF2E6CA : 0xFFE2D1AE, 0xFF5C4328,
                hoverPrev ? 0xFFFFFAEC : 0xFFF7ECD4, 0xFFAC946C);
        drawCenteredInk(guiGraphics, "<", rightLeft + 19, pageControlY + 2, 0xFF1E1812);

        Component volText = Component.translatable("gui.universal_tool.codex.volume", currentVolume + 1, VOLUMES_COUNT);
        drawCenteredInk(guiGraphics, volText, rightLeft + 60, pageControlY + 2, 0xFF3F3325);

        drawPixelButton(guiGraphics, rightLeft + 92, pageControlY, 18, 12,
                hoverNext ? 0xFFF2E6CA : 0xFFE2D1AE, 0xFF5C4328,
                hoverNext ? 0xFFFFFAEC : 0xFFF7ECD4, 0xFFAC946C);
        drawCenteredInk(guiGraphics, ">", rightLeft + 101, pageControlY + 2, 0xFF1E1812);

        // 底部 MC 风格印章/石质按钮
        int btnY = rightTop + 144;
        boolean hoverWarp = mouseX >= rightLeft + 2 && mouseX <= rightLeft + 56 && mouseY >= btnY && mouseY <= btnY + 18;
        drawPixelButton(guiGraphics, rightLeft + 2, btnY, 54, 18,
                hoverWarp ? 0xFFA33B3B : 0xFF8A2E2E,
                hoverWarp ? 0xFFFFFFFF : 0xFF2B1010,
                hoverWarp ? 0xFFD47474 : 0xFFB85C5C,
                0xFF521919);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.universal_tool.codex.warp"), rightLeft + 29, btnY + 5, 0xFFFFFFFF);

        boolean hoverScroll = mouseX >= rightLeft + 64 && mouseX <= rightLeft + 118 && mouseY >= btnY && mouseY <= btnY + 18;
        drawPixelButton(guiGraphics, rightLeft + 64, btnY, 54, 18,
                hoverScroll ? 0xFF7B88B8 : 0xFF8B8B8B,
                hoverScroll ? 0xFFFFFFFF : 0xFF000000,
                hoverScroll ? 0xFFAEB8E0 : 0xFFC6C6C6,
                hoverScroll ? 0xFF4F5980 : 0xFF555555);
        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.universal_tool.codex.transcribe"), rightLeft + 91, btnY + 5, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !this.isNaming) {
            int rightLeft = this.leftPos + 148;
            int rightTop = this.topPos + 16;
            List<Waypoint> waypoints = ClientWaypointCache.getWaypoints();
            int maxCap = ClientWaypointCache.getMaxCapacity();

            // 点击「记录当前位置」按钮
            if (mouseX >= rightLeft + 48 && mouseX <= rightLeft + 122 && mouseY >= rightTop - 2 && mouseY <= rightTop + 14) {
                startPureCapture();
                return true;
            }

            // 翻页点击
            int pageControlY = rightTop + 128;
            if (mouseX >= rightLeft + 10 && mouseX <= rightLeft + 28 && mouseY >= pageControlY && mouseY <= pageControlY + 12) {
                if (currentVolume > 0) currentVolume--;
                return true;
            }
            if (mouseX >= rightLeft + 92 && mouseX <= rightLeft + 110 && mouseY >= pageControlY && mouseY <= pageControlY + 12) {
                if (currentVolume < VOLUMES_COUNT - 1) currentVolume++;
                return true;
            }

            // 点击条目或删除
            int itemH = 9;
            int startIndex = currentVolume * 12;
            for (int i = 0; i < 12; i++) {
                int globalIdx = startIndex + i;
                int itemY = rightTop + 16 + i * itemH;
                if (mouseX >= rightLeft && mouseX <= rightLeft + 120 && mouseY >= itemY && mouseY < itemY + itemH) {
                    if (globalIdx < maxCap && globalIdx < waypoints.size()) {
                        if (mouseX >= rightLeft + 106) {
                            PacketDistributor.sendToServer(new DeleteWaypointPayload(waypoints.get(globalIdx).id()));
                            if (selectedIndex >= waypoints.size() - 1 && selectedIndex > 0) {
                                selectedIndex--;
                            }
                            return true;
                        }
                        this.selectedIndex = globalIdx;
                        return true;
                    }
                }
            }

            // 跃迁
            int btnY = rightTop + 144;
            if (mouseX >= rightLeft + 2 && mouseX <= rightLeft + 56 && mouseY >= btnY && mouseY <= btnY + 18) {
                if (!waypoints.isEmpty() && selectedIndex < waypoints.size()) {
                    PacketDistributor.sendToServer(new TeleportRequestPayload(waypoints.get(selectedIndex).id()));
                    this.onClose();
                    return true;
                }
            }

            // 誊写
            if (mouseX >= rightLeft + 64 && mouseX <= rightLeft + 118 && mouseY >= btnY && mouseY <= btnY + 18) {
                if (!waypoints.isEmpty() && selectedIndex < waypoints.size()) {
                    PacketDistributor.sendToServer(new WriteScrollPayload(waypoints.get(selectedIndex).id()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private Component getDimensionName(ResourceLocation dim) {
        String path = dim.getPath();
        return switch (path) {
            case "overworld" -> Component.translatable("gui.universal_tool.codex.dim.overworld");
            case "the_nether" -> Component.translatable("gui.universal_tool.codex.dim.the_nether");
            case "the_end" -> Component.translatable("gui.universal_tool.codex.dim.the_end");
            default -> Component.literal(path);
        };
    }

    private static void drawPixelButton(GuiGraphics g, int x, int y, int w, int h, int fill, int border, int hiBevel, int loBevel) {
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        drawBevelOutline(g, x, y, w, h, border, hiBevel, loBevel);
    }

    private static void drawBevelOutline(GuiGraphics g, int x, int y, int w, int h, int border, int hiBevel, int loBevel) {
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, hiBevel);
        g.fill(x + 1, y + 2, x + 2, y + h - 1, hiBevel);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, loBevel);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, loBevel);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
