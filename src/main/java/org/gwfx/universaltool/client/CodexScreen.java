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

        this.nameInput = new EditBox(this.font, this.leftPos + 60, this.topPos + 80, 160, 20, Component.literal("地点名称"));
        this.nameInput.setMaxLength(32);
        this.nameInput.setValue("探索坐标 " + (ClientWaypointCache.getWaypoints().size() + 1));
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
            this.nameInput.setValue("探索坐标 " + (ClientWaypointCache.getWaypoints().size() + 1));
            this.setFocused(this.nameInput);
        }
        if (this.confirmButton != null) {
            this.confirmButton.visible = true;
        }
    }

    private void onConfirmAdd() {
        if (this.minecraft != null && this.minecraft.player != null) {
            String name = this.nameInput.getValue().trim();
            if (name.isEmpty()) name = "未命名坐标";
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
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.blit(PARCHMENT_BG, this.leftPos, this.topPos, 0, 0, GUI_WIDTH, GUI_HEIGHT, 280, 190);

        List<Waypoint> waypoints = ClientWaypointCache.getWaypoints();

        if (this.isNaming) {
            guiGraphics.fill(this.leftPos + 40, this.topPos + 50, this.leftPos + 240, this.topPos + 140, 0xEE2A2521);
            guiGraphics.drawCenteredString(this.font, "记录当前传送点", this.leftPos + 140, this.topPos + 62, 0xFFE8D3A2);
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

    private void renderLeftPage(GuiGraphics guiGraphics, List<Waypoint> waypoints) {
        int pageLeft = this.leftPos + 16;
        int pageTop = this.topPos + 18;

        if (waypoints.isEmpty() || selectedIndex < 0 || selectedIndex >= waypoints.size()) {
            guiGraphics.drawCenteredString(this.font, "探险家手记", pageLeft + 55, pageTop + 30, 0x5C4033);
            guiGraphics.drawCenteredString(this.font, "当前卷尚无选定地点", pageLeft + 55, pageTop + 55, 0x8B7355);
            guiGraphics.drawCenteredString(this.font, "点击右侧记录当前位置", pageLeft + 55, pageTop + 75, 0x8B7355);
            return;
        }

        Waypoint wp = waypoints.get(selectedIndex);

        int photoX = pageLeft + 4;
        int photoY = pageTop + 4;
        guiGraphics.fill(photoX - 2, photoY - 2, photoX + 112, photoY + 64, 0xFFEDE2CD);
        guiGraphics.fill(photoX - 3, photoY - 3, photoX + 113, photoY - 2, 0xFFBFB5A0);

        ResourceLocation photoTex = PhotoManager.getOrLoadTexture(wp.photoId());
        if (photoTex != null) {
            guiGraphics.blit(photoTex, photoX, photoY, 0, 0, 110, 62, 110, 62);
        } else {
            guiGraphics.fill(photoX, photoY, photoX + 110, photoY + 62, 0xFF4A4036);
            guiGraphics.drawCenteredString(this.font, "无实景照片", photoX + 55, photoY + 26, 0xD4C4A8);
        }

        int infoY = photoY + 70;
        guiGraphics.drawString(this.font, Component.literal(wp.name()).withStyle(ChatFormatting.BOLD), pageLeft + 2, infoY, 0x3E2723, false);
        guiGraphics.drawString(this.font, "维度: " + getDimensionName(wp.dimension()), pageLeft + 2, infoY + 14, 0x5D4037, false);
        guiGraphics.drawString(this.font, String.format("坐标: %.0f, %.0f, %.0f", wp.x(), wp.y(), wp.z()), pageLeft + 2, infoY + 26, 0x5D4037, false);

        if (this.minecraft != null && this.minecraft.player != null) {
            var player = this.minecraft.player;
            if (player.level().dimension().location().equals(wp.dimension())) {
                double dist = Math.sqrt(player.distanceToSqr(wp.x(), wp.y(), wp.z()));
                guiGraphics.drawString(this.font, String.format("直线距离: %.0fm", dist), pageLeft + 2, infoY + 38, 0x795548, false);
            } else {
                guiGraphics.drawString(this.font, "距离: 异次元空间", pageLeft + 2, infoY + 38, 0x9E9D24, false);
            }
        }
    }

    private void renderRightPage(GuiGraphics guiGraphics, List<Waypoint> waypoints, int mouseX, int mouseY) {
        int rightLeft = this.leftPos + 148;
        int rightTop = this.topPos + 16;
        int maxCap = ClientWaypointCache.getMaxCapacity();

        // 顶部「记录当前位置」按钮
        boolean hoverAdd = mouseX >= rightLeft + 50 && mouseX <= rightLeft + 120 && mouseY >= rightTop - 2 && mouseY <= rightTop + 14;
        guiGraphics.fill(rightLeft + 48, rightTop - 2, rightLeft + 122, rightTop + 14, hoverAdd ? 0xFFC9B68F : 0xFFDFD1B5);
        guiGraphics.drawCenteredString(this.font, "记录当前位置", rightLeft + 85, rightTop + 1, 0x3E2723);

        // 12 个条目渲染 (根据当前卷切换)
        int itemH = 9;
        int startIndex = currentVolume * 12;
        for (int i = 0; i < 12; i++) {
            int globalIdx = startIndex + i;
            int itemY = rightTop + 16 + i * itemH;
            boolean isUnlocked = globalIdx < maxCap;
            boolean isSelected = (globalIdx == selectedIndex && isUnlocked && globalIdx < waypoints.size());
            boolean hover = mouseX >= rightLeft && mouseX <= rightLeft + 120 && mouseY >= itemY && mouseY < itemY + itemH;

            int bgCol = isSelected ? 0xFFBCAAA4 : (hover ? 0xFFD7CCC8 : 0x00000000);
            if (bgCol != 0) {
                guiGraphics.fill(rightLeft, itemY, rightLeft + 120, itemY + itemH - 1, bgCol);
            }

            String numStr = (globalIdx < ROMAN_NUMERALS.length ? ROMAN_NUMERALS[globalIdx] : String.valueOf(globalIdx + 1)) + ".";
            guiGraphics.drawString(this.font, numStr, rightLeft + 2, itemY, isUnlocked ? 0x4E342E : 0x9E9E9E, false);

            if (!isUnlocked) {
                guiGraphics.drawString(this.font, "[未解锁]", rightLeft + 28, itemY, 0x9E9E9E, false);
            } else if (globalIdx < waypoints.size()) {
                String name = waypoints.get(globalIdx).name();
                if (name.length() > 7) name = name.substring(0, 6) + "..";
                guiGraphics.drawString(this.font, name, rightLeft + 28, itemY, 0x2E1C14, false);

                // 删除按钮
                boolean hoverDel = mouseX >= rightLeft + 108 && mouseX <= rightLeft + 118 && mouseY >= itemY && mouseY < itemY + itemH;
                guiGraphics.drawString(this.font, "x", rightLeft + 110, itemY, hoverDel ? 0xFFD32F2F : 0xFF8D6E63, false);
            } else {
                guiGraphics.drawString(this.font, "---", rightLeft + 28, itemY, 0x8D6E63, false);
            }
        }

        // 翻页控件 [ < ] 第 1/3 卷 [ > ]
        int pageControlY = rightTop + 128;
        boolean hoverPrev = mouseX >= rightLeft + 10 && mouseX <= rightLeft + 28 && mouseY >= pageControlY && mouseY <= pageControlY + 12;
        boolean hoverNext = mouseX >= rightLeft + 92 && mouseX <= rightLeft + 110 && mouseY >= pageControlY && mouseY <= pageControlY + 12;

        guiGraphics.fill(rightLeft + 10, pageControlY, rightLeft + 28, pageControlY + 12, hoverPrev ? 0xFFC9B68F : 0xFFDFD1B5);
        guiGraphics.drawCenteredString(this.font, "<", rightLeft + 19, pageControlY + 2, 0x3E2723);

        String volText = String.format("第 %d/%d 卷", currentVolume + 1, VOLUMES_COUNT);
        guiGraphics.drawCenteredString(this.font, volText, rightLeft + 60, pageControlY + 2, 0x5D4037);

        guiGraphics.fill(rightLeft + 92, pageControlY, rightLeft + 110, pageControlY + 12, hoverNext ? 0xFFC9B68F : 0xFFDFD1B5);
        guiGraphics.drawCenteredString(this.font, ">", rightLeft + 101, pageControlY + 2, 0x3E2723);

        // 底部火漆印章按钮
        int btnY = rightTop + 144;
        boolean hoverWarp = mouseX >= rightLeft + 2 && mouseX <= rightLeft + 56 && mouseY >= btnY && mouseY <= btnY + 18;
        guiGraphics.fill(rightLeft + 2, btnY, rightLeft + 56, btnY + 18, hoverWarp ? 0xFFB71C1C : 0xFF880E4F);
        guiGraphics.drawCenteredString(this.font, "跃迁", rightLeft + 29, btnY + 5, 0xFFFFEBEE);

        boolean hoverScroll = mouseX >= rightLeft + 64 && mouseX <= rightLeft + 118 && mouseY >= btnY && mouseY <= btnY + 18;
        guiGraphics.fill(rightLeft + 64, btnY, rightLeft + 118, btnY + 18, hoverScroll ? 0xFFF57F17 : 0xFFE65100);
        guiGraphics.drawCenteredString(this.font, "誊写", rightLeft + 91, btnY + 5, 0xFFFFF8E1);
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

    private String getDimensionName(ResourceLocation dim) {
        String path = dim.getPath();
        return switch (path) {
            case "overworld" -> "主世界";
            case "the_nether" -> "下界";
            case "the_end" -> "末地";
            default -> path;
        };
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
